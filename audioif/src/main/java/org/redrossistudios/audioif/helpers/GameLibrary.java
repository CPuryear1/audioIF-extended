package org.redrossistudios.audioif.helpers;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** App-private folder holding the bundled and downloaded games. */
public final class GameLibrary {

    private static final String TAG = "GameLibrary";
    private static final String ASSET_DIR = "games";

    private GameLibrary() {
    }

    public static File getGamesDir(Context context) {
        File dir = new File(context.getFilesDir(), "games");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "Could not create " + dir);
        }
        return dir;
    }

    public static File getGameFile(Context context, InfocomCatalog.Game game) {
        return new File(getGamesDir(context), game.getLocalFileName());
    }

    public static boolean isInstalled(Context context, InfocomCatalog.Game game) {
        return getGameFile(context, game).isFile();
    }

    /** Copies the games shipped in assets/games into the games folder if they aren't there yet. */
    public static void installBundledGames(Context context) {
        for (InfocomCatalog.Game game : InfocomCatalog.getBundledGames()) {
            File target = getGameFile(context, game);
            if (target.isFile()) {
                continue;
            }
            File temp = new File(target.getPath() + ".tmp");
            try (InputStream in = context.getAssets().open(ASSET_DIR + "/" + game.getLocalFileName());
                 OutputStream out = new FileOutputStream(temp)) {
                copy(in, out);
            } catch (IOException e) {
                Log.e(TAG, "Could not install " + game.title, e);
                temp.delete();
                continue;
            }
            if (!temp.renameTo(target)) {
                Log.e(TAG, "Could not rename " + temp);
                temp.delete();
            }
        }
    }

    static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
