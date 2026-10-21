package org.redrossistudios.audioif.helpers;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Environment;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

public class FileScanner {


    public  final String STORY_EXTENSIONS = "(?i).+\\.(z[1-8]|zblorb|zlb)$";
    private final AppCompatActivity activity;
    public ListView list;
    private Dialog dialog;
    private ArrayList<String> filePathList;
    private File chosenFile;

    public interface FileSelectedListener {
        void fileSelected(File file);
    }

    public interface FileDialogDismissedListener{
        void dialogDismissed();
    }

    public FileScanner setDialogDismissListener(FileDialogDismissedListener dialogDismissListener){
        this.dialogDismissListener = dialogDismissListener;
        return this;
    }

    private FileDialogDismissedListener dialogDismissListener;
    public FileScanner setFileListener(FileSelectedListener fileListener) {
        this.fileListener = fileListener;
        return this;
    }
    private FileSelectedListener fileListener;


    public FileScanner(final AppCompatActivity activity) {
        this.activity = activity;
    }

    // Scanning shared storage can take many seconds on a phone full of files, so it runs
    // on a background thread and the picker is shown once the scan completes.
    public void showDialog() {
        final ProgressDialog progress = ProgressDialog.show(activity, "",
                "Searching for games...", true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<String> found = scanDir(GameLibrary.getGamesDir(activity),
                        new ArrayList<String>(), 0);
                Collections.sort(found, String.CASE_INSENSITIVE_ORDER);
                scanDir(Environment.getExternalStorageDirectory(), found, 0);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }
                        progress.dismiss();
                        filePathList = found;
                        if (filePathList.isEmpty()) {
                            showNoFilesDialog();
                        } else {
                            buildDialog();
                            dialog.show();
                        }
                    }
                });
            }
        }, "FileScanner").start();
    }

    private void buildDialog() {
        dialog = new Dialog(activity);
        list = new ListView(activity);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int which, long id) {
                chosenFile = new File(filePathList.get(which));
                if (fileListener != null) {
                    fileListener.fileSelected(chosenFile);
                }
                dialog.dismiss();
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if(chosenFile == null && dialogDismissListener != null){
                    dialogDismissListener.dialogDismissed();
                }
            }
        });
        dialog.setContentView(list);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);

        ArrayList<String> fileList = new ArrayList<>();
        for (String filePath : filePathList) {
            fileList.add(new File(filePath).getName());
        }
        list.setAdapter(new ArrayAdapter<String>(activity,
                android.R.layout.simple_list_item_1, fileList) {
            @Override
            public View getView(int pos, View view, ViewGroup parent) {
                view = super.getView(pos, view, parent);
                ((TextView) view).setSingleLine(true);
                return view;
            }
        });
    }

    private void showNoFilesDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage("No story files found.")
                .setCancelable(false)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        activity.finish();
                    }
                });
        builder.create().show();
    }

    private static final int MAX_SCAN_DEPTH = 8;

    private ArrayList<String> scanDir(File dir, ArrayList<String> list, int depth) {
        if (depth > MAX_SCAN_DEPTH) {
            return list;
        }
        File[] children = dir.listFiles();
        if (children == null)
            return list;
        for (File child : children) {
            String name = child.getName();
            if (child.isFile()) {
                if (name.matches(STORY_EXTENSIONS)) {
                    list.add(child.getPath());
                }
            } else if (!name.startsWith(".") && !(depth == 0 && name.equals("Android"))) {
                scanDir(child, list, depth + 1);
            }
        }
        return list;
    }

}