package org.redrossistudios.audioif.helpers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads catalog games into the games folder, checking each file's MD5. */
public final class GameDownloader {

    public interface Callback {
        void onSuccess(InfocomCatalog.Game game, File file);

        void onFailure(InfocomCatalog.Game game, Exception error);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private GameDownloader() {
    }

    public static void download(Context context, final InfocomCatalog.Game game, final Callback callback) {
        final File target = GameLibrary.getGameFile(context.getApplicationContext(), game);
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    downloadTo(game, target);
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(game, target);
                        }
                    });
                } catch (final Exception e) {
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailure(game, e);
                        }
                    });
                }
            }
        });
    }

    private static void downloadTo(InfocomCatalog.Game game, File target) throws IOException {
        File temp = new File(target.getPath() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(game.getUrl()).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + status);
            }
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(temp)) {
                GameLibrary.copy(in, out);
            }
            String md5;
            try (InputStream in = new FileInputStream(temp)) {
                md5 = InfocomCatalog.md5Hex(in);
            }
            if (!md5.equalsIgnoreCase(game.md5)) {
                throw new IOException("Downloaded file is corrupt (checksum mismatch)");
            }
            if (!temp.renameTo(target)) {
                throw new IOException("Could not save " + target.getName());
            }
        } finally {
            connection.disconnect();
            if (temp.exists()) {
                temp.delete();
            }
        }
    }
}
