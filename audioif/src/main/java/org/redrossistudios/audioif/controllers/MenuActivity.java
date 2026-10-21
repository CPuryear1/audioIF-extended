package org.redrossistudios.audioif.controllers;

import android.Manifest;
import android.app.Application;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.redrossistudios.audioif.R;
import org.redrossistudios.audioif.helpers.EdgeToEdge;
import org.redrossistudios.audioif.helpers.GameLibrary;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Here, thisActivity is the current activity

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Bundled and downloaded games live in app storage, so all-files access is only
            // needed for story files copied elsewhere on the device. Ask once, not every launch.
            final SharedPreferences prefs = getSharedPreferences("permissions", MODE_PRIVATE);
            if (!Environment.isExternalStorageManager()
                    && !prefs.getBoolean("allFilesAccessAsked", false)) {
                prefs.edit().putBoolean("allFilesAccessAsked", true).apply();
                new AlertDialog.Builder(this)
                        .setTitle("Find your own story files?")
                        .setMessage("The included and downloaded games work without any extra "
                                + "permission. To also find story files you have copied onto "
                                + "this device, allow audio IF to access all files.")
                        .setPositiveButton("Open settings", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Not now", null)
                        .show();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {


                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        3);


            }
        }


        GameLibrary.installBundledGames(this);
        setContentView(R.layout.activity_menu);
        EdgeToEdge.applySystemBarPadding(this);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowHomeEnabled(true);
        actionBar.setIcon(R.mipmap.ic_launcher);
        Button audioButton = (Button)findViewById(R.id.audioButton);
        Button textButton = (Button)findViewById(R.id.textButton);
        Button linkButton = (Button)findViewById(R.id.linkButton);
        Button downloadButton = (Button)findViewById(R.id.downloadButton);
        Button voiceButton = (Button)findViewById(R.id.voiceButton);
        voiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadNewActivity(VoiceSettingsActivity.class);
            }
        });
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadNewActivity(DownloadGamesActivity.class);
            }
        });
        audioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadNewActivity(MainActivity.class);
            }
        });
        textButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                loadNewActivity(TextModeActivity.class);
            }
        });
        linkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://ifarchive.org/indexes/if-archiveXgamesXzcode.html"));
                startActivity(browserIntent);
            }
        });
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 3:
                //External storage2
                if(grantResults.length == 2 && grantResults[0]== PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED){
                    //resume tasks needing this permission
                }else{
                    moveTaskToBack(true);
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(1);
                }
                break;
        }
    }

    private void loadNewActivity(Class<?> cls){
        Intent intent = new Intent(this, cls);
        startActivity(intent);
    }

}
