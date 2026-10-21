package org.redrossistudios.audioif.controllers;

import android.app.ProgressDialog;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import org.redrossistudios.audioif.R;
import org.redrossistudios.audioif.helpers.EdgeToEdge;
import org.redrossistudios.audioif.helpers.Alert;
import org.redrossistudios.audioif.helpers.CommandSession;
import org.redrossistudios.audioif.helpers.FileScanner;
import org.redrossistudios.audioif.helpers.SavedGameManager;
import org.redrossistudios.audioif.helpers.StoryFileTypeChecker;
import org.redrossistudios.audioif.helpers.ZGame;
import org.redrossistudios.audioif.models.StoryFileType;
import org.zmpp.ExecutionControl;
import org.zmpp.blorb.NativeImage;
import org.zmpp.blorb.NativeImageFactory;
import org.zmpp.io.IOSystem;
import org.zmpp.vm.MachineFactory;
import org.zmpp.windowing.BufferedScreenModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

public class TextModeActivity extends AppCompatActivity {

    private ExecutionControl executionControl;
    private MachineFactory.MachineInitStruct machineInit;
    private BufferedScreenModel screenModel;
    private TextView storyField;
    private EditText commandField;
    private Button button;
    private InputStream storyIs;
    private FileScanner fileScanner;
    private StoryFileTypeChecker storyFileTypeChecker;
    private org.redrossistudios.audioif.helpers.Alert Alert;
    private ScrollView scrollView;
    private TextView footer;
    private ProgressDialog progressDialog;
    private SavedGameManager savedGameManager;
    private String filePath;
    private TextModeActivity activity;
    private CommandSession session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activity = this;
        Alert = new Alert(this);
        super.onCreate(savedInstanceState);
        getSupportActionBar().hide();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_text_mode);
        EdgeToEdge.applySystemBarPadding(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        storyField = (TextView) findViewById(R.id.textView);
        storyField.setText("");
        commandField = (EditText) findViewById(R.id.editText);
        button = (Button) findViewById(R.id.button);
        progressDialog = ProgressDialog.show(TextModeActivity.this, "",
                "Loading. Please wait...", true);
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        footer = (TextView) findViewById(R.id.footer);
        storyFileTypeChecker = new StoryFileTypeChecker();

        fileScanner = new FileScanner(this);

        fileScanner.setDialogDismissListener(new FileScanner.FileDialogDismissedListener() {
            @Override
            public void dialogDismissed() {
                activity.finish();
            }
        });
        fileScanner.setFileListener(new FileScanner.FileSelectedListener() {
            @Override
            public void fileSelected(final File file) {
                try {
                    filePath = file.getAbsolutePath();
                    String fileExtension = file.getName().substring(file.getName().lastIndexOf('.') + 1);
                    StoryFileType storyFileType = storyFileTypeChecker.GetStoryFileType(fileExtension);
                    storyIs = new FileInputStream(file);
                    configureEngine(storyIs, storyFileType);
                    executeEngine();
                } catch (Exception e){
                    e.printStackTrace();
                    Alert.show("Error", e.toString() + "\n" + e.getMessage());
                }

            }
        }).showDialog();
        progressDialog.dismiss();
    }

    public void executeEngine(){
        try {
            executionControl = new ExecutionControl(machineInit);
            executionControl.resizeScreen(100,500);
            screenModel.init(executionControl.getMachine(), executionControl.getZsciiEncoding());
            executionControl.run();
            ZGame game = new ZGame(executionControl, screenModel);
            session = new CommandSession(game);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handleButtonClick();
                }
            });
            String initialText = game.readIntro();
            storyField.append(initialText);
            session.start(initialText);

        } catch (Exception ex) {
            Alert.show("Error", ex.toString() + "\n" + ex.getMessage());
        }
    }


    private void configureEngine(InputStream storyIs, StoryFileType storyFileType){
        machineInit = new MachineFactory.MachineInitStruct();
        if(storyFileType == StoryFileType.BLORBFILE){
            machineInit.blorbFile = storyIs;
        }
        else if(storyFileType == StoryFileType.ZFILE){
            machineInit.storyFile = storyIs;
        }
        machineInit.nativeImageFactory = new NativeImageFactory() {
            public NativeImage createImage(InputStream inputStream)
                    throws IOException {
                return null;
            }
        };
        savedGameManager = new SavedGameManager(filePath.substring(0, filePath.lastIndexOf('.')) + ".sav");
        machineInit.saveGameDataStore = savedGameManager;

        machineInit.ioSystem = new IOSystem() {
            public Reader getInputStreamReader() { return null; }
            public Writer getTranscriptWriter() { return null; }
        };
        screenModel = new BufferedScreenModel();
        machineInit.statusLine = screenModel;
        machineInit.screenModel = screenModel;
        machineInit.keyboardInputStream = new org.zmpp.io.InputStream() {
            public void close() { }
            public String readLine() { return null; }
        };
    }

    private void handleButtonClick(){
        if (session == null) {
            return;
        }
        String typed = commandField.getText().toString();
        CommandSession.Result result = session.handle(typed);
        // Show what was actually sent when the app changed or split the command.
        List<String> sent = result.sentCommands();
        boolean unchanged = sent.size() == 1 && sent.get(0).trim().equalsIgnoreCase(typed.trim());
        appendCommandEcho(unchanged || sent.isEmpty() ? typed : typed + "  [" + String.join("; ", sent) + "]");
        for (CommandSession.Exchange exchange : result.exchanges) {
            if (exchange.response != null && !exchange.response.trim().isEmpty()) {
                storyField.append("\n\n" + exchange.response);
            }
            if (exchange.note != null) {
                appendNote(exchange.note);
            }
        }
        footer.requestFocus();
        commandField.setText("");
    }

    private void appendCommandEcho(String echo) {
        SpannableString line = new SpannableString("\n\n> " + echo);
        line.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.amber)),
                0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        line.setSpan(new StyleSpan(Typeface.BOLD), 0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        storyField.append(line);
    }

    // App messages (help, suggestions, undo) are shown in italics to set them apart from the game.
    private void appendNote(String note) {
        SpannableString line = new SpannableString("\n\n" + note);
        line.setSpan(new StyleSpan(Typeface.ITALIC), 0, line.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        storyField.append(line);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                handleButtonClick();
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }
}
