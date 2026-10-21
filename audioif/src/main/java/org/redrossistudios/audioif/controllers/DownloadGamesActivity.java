package org.redrossistudios.audioif.controllers;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.redrossistudios.audioif.helpers.EdgeToEdge;
import org.redrossistudios.audioif.helpers.GameDownloader;
import org.redrossistudios.audioif.helpers.GameLibrary;
import org.redrossistudios.audioif.helpers.InfocomCatalog;

import java.io.File;
import java.util.List;

/** Lists the downloadable games; tap one to download it into the games folder. */
public class DownloadGamesActivity extends AppCompatActivity {

    private static final String PREFS = "downloads";
    private static final String KEY_NOTICE_ACCEPTED = "copyrightNoticeAccepted";

    private final List<InfocomCatalog.Game> games = InfocomCatalog.getDownloadableGames();
    private InfocomCatalog.Game downloading;
    private ArrayAdapter<InfocomCatalog.Game> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Download Infocom games");
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<InfocomCatalog.Game>(this, android.R.layout.simple_list_item_1, games) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                TextView row = (TextView) super.getView(position, view, parent);
                InfocomCatalog.Game game = getItem(position);
                String status;
                if (game == downloading) {
                    status = " (downloading...)";
                } else if (GameLibrary.isInstalled(DownloadGamesActivity.this, game)) {
                    status = " (downloaded)";
                } else {
                    status = "";
                }
                row.setText(game.title + status);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onGameTapped(games.get(position));
            }
        });
        setContentView(list);
        EdgeToEdge.applySystemBarPadding(this);
        if (!getPreferences().getBoolean(KEY_NOTICE_ACCEPTED, false)) {
            showCopyrightNotice();
        }
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void showCopyrightNotice() {
        new AlertDialog.Builder(this)
                .setTitle("About these games")
                .setMessage("Infocom games other than Zork I-III are still copyrighted by Microsoft. "
                        + "They are downloaded from the Obsessively Complete Infocom Catalog (eblong.com/infocom) "
                        + "for your personal use. Colossal Cave Adventure is downloaded from the IF Archive "
                        + "(ifarchive.org).")
                .setCancelable(false)
                .setPositiveButton("I understand", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getPreferences().edit().putBoolean(KEY_NOTICE_ACCEPTED, true).apply();
                    }
                })
                .setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    private void onGameTapped(InfocomCatalog.Game game) {
        if (downloading != null) {
            Toast.makeText(this, "Please wait for " + downloading.title + " to finish.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (GameLibrary.isInstalled(this, game)) {
            Toast.makeText(this, game.title + " is already downloaded. Start audio or text mode to play it.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        downloading = game;
        adapter.notifyDataSetChanged();
        GameDownloader.download(this, game, new GameDownloader.Callback() {
            @Override
            public void onSuccess(InfocomCatalog.Game game, File file) {
                finishDownload(game.title + " downloaded.");
            }

            @Override
            public void onFailure(InfocomCatalog.Game game, Exception error) {
                finishDownload("Could not download " + game.title + ": " + error.getMessage());
            }
        });
    }

    private void finishDownload(String message) {
        downloading = null;
        if (isFinishing() || isDestroyed()) {
            return;
        }
        adapter.notifyDataSetChanged();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
