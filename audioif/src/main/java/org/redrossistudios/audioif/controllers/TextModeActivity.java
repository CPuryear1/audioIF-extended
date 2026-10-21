package org.redrossistudios.audioif.controllers;

import android.app.ProgressDialog;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
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
import org.redrossistudios.audioif.helpers.CommandInput;
import org.redrossistudios.audioif.helpers.CommandNormalizer;
import org.redrossistudios.audioif.helpers.CommandSuggester;
import org.redrossistudios.audioif.helpers.FileScanner;
import org.redrossistudios.audioif.helpers.SavedGameManager;
import org.redrossistudios.audioif.helpers.StoryFileTypeChecker;
import org.redrossistudios.audioif.models.StoryFileType;
import org.zmpp.ExecutionControl;
import org.zmpp.blorb.NativeImage;
import org.zmpp.blorb.NativeImageFactory;
import org.zmpp.io.IOSystem;
import org.zmpp.vm.MachineFactory;
import org.zmpp.windowing.AnnotatedText;
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
    private List<String> dictionaryWords;
    private List<String> pendingSuggestions;

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
            try {
                dictionaryWords = executionControl.getMachine().getDictionaryWords();
            } catch (Exception e) {
                Log.w("CommandSuggester", "Could not read the game dictionary", e);
                dictionaryWords = null;
            }
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handleButtonClick();
                }
            });
            String initialText = getBufferText(screenModel);
            if(initialText.length() > 1){
                storyField.append(initialText);
            }
            else{
                runCommand(" ", " ");
            }

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
        String typed = commandField.getText().toString();
        CommandInput.Result resolved = CommandInput.resolve(typed, pendingSuggestions, gameDictionary);
        pendingSuggestions = null;
        if (resolved.reply != null) {
            storyField.append("\n\n >" + typed + "\n\n" + resolved.reply);
            footer.requestFocus();
            commandField.setText("");
            return;
        }
        runCommand(typed, resolved.command);
    }

    private void runCommand(String typed, String command){
        executionControl.resumeWithInput(command);
        String currentText = getBufferText(screenModel);

        if(currentText.length() > 1){
            // Show what was actually sent when the normalizer or a suggestion changed it.
            String echo = typed.trim().equalsIgnoreCase(command.trim()) ? typed : typed + "  [" + command + "]";
            storyField.append("\n\n >" + echo + "\n\n" + currentText);
            offerSuggestionsIfRejected(command, currentText);
            footer.requestFocus();
            commandField.setText("");
        }
        else{
            runCommand(" ", " ");
        }
    }

    // Same rejection handling as audio mode, shown as text; type a number to choose.
    private void offerSuggestionsIfRejected(String command, String gameResponse) {
        if (command.trim().isEmpty() || dictionaryWords == null
                || !CommandSuggester.isRejection(gameResponse)) {
            return;
        }
        List<String> suggestions = CommandSuggester.suggest(command, dictionaryWords, 3);
        if (!suggestions.isEmpty()) {
            pendingSuggestions = suggestions;
            storyField.append("\n\n" + CommandSuggester.buildPrompt(suggestions));
        }
    }

    private final CommandNormalizer.Dictionary gameDictionary = new CommandNormalizer.Dictionary() {
        @Override
        public boolean contains(String word) {
            try {
                return executionControl.getMachine().lookupToken(0, word) != 0;
            } catch (Exception e) {
                Log.w("CommandNormalizer", "Dictionary lookup failed for '" + word + "'", e);
                return false;
            }
        }
    };

    private String getBufferText(BufferedScreenModel screenModel){
        String result = "";
        for (AnnotatedText text : screenModel.getLowerBuffer()) {
            result += text.getText();
        }
        if(result.length() > 2){
            return result.substring(0,result.length()-2).trim();
        }
        return result;
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
