package org.redrossistudios.audioif.controllers;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.redrossistudios.audioif.R;
import org.redrossistudios.audioif.helpers.Alert;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    public boolean isFileScannerInput = false;
    public boolean isFileManuallyChosen = false;
    private ExecutionControl executionControl;
    private MachineFactory.MachineInitStruct machineInit;
    private BufferedScreenModel screenModel;
    private TextView storyField;
    private EditText commandField;
    private Button button;
    private static final int REQ_CODE_RECORD_AUDIO = 101;
    private static final Map<String, String> COMPASS_ALIASES = new LinkedHashMap<>();
    static {
        COMPASS_ALIASES.put("north west", "NW");
        COMPASS_ALIASES.put("northwest", "NW");
        COMPASS_ALIASES.put("north east", "NE");
        COMPASS_ALIASES.put("northeast", "NE");
        COMPASS_ALIASES.put("south east", "SE");
        COMPASS_ALIASES.put("southeast", "SE");
        COMPASS_ALIASES.put("south west", "SW");
        COMPASS_ALIASES.put("southwest", "SW");
    }
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private int utteranceCounter = 0;
    private volatile String pendingUtteranceId;
    private TextToSpeech tts;
    private InputStream storyIs;
    private FileScanner fileScanner;
    private StoryFileTypeChecker storyFileTypeChecker;
    private org.redrossistudios.audioif.helpers.Alert Alert;
    private ProgressDialog progressDialog;
    private SavedGameManager savedGameManager;
    private String filePath;
    private MainActivity activity;

    private void startVoiceInput() {
        if (speechRecognizer == null || isFinishing()) {
            return;
        }
        isFileScannerInput = false;
        if (isListening) {
            speechRecognizer.cancel();
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        isListening = true;
        speechRecognizer.startListening(intent);
    }

    private void stopVoiceInput() {
        if (speechRecognizer != null && isListening) {
            isListening = false;
            speechRecognizer.cancel();
        }
    }

    // Speaks the text and starts voice recognition once TTS finishes (or is interrupted).
    private void speakThenListen(String text) {
        stopVoiceInput();
        String utteranceId = "utterance-" + (++utteranceCounter);
        pendingUtteranceId = utteranceId;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    private void onUtteranceFinished(final String utteranceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (utteranceId != null && utteranceId.equals(pendingUtteranceId)) {
                    pendingUtteranceId = null;
                    startVoiceInput();
                }
            }
        });
    }

    private static String applyCompassAliases(String voiceInputText) {
        String lower = voiceInputText.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> alias : COMPASS_ALIASES.entrySet()) {
            if (lower.contains(alias.getKey())) {
                return alias.getValue();
            }
        }
        return voiceInputText;
    }

    private final RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) { }

        @Override
        public void onBeginningOfSpeech() { }

        @Override
        public void onRmsChanged(float rmsdB) { }

        @Override
        public void onBufferReceived(byte[] buffer) { }

        @Override
        public void onEndOfSpeech() { }

        @Override
        public void onError(int error) {
            if (!isListening || isFinishing()) {
                return;
            }
            isListening = false;
            Log.w("SpeechRecognizer", "Recognition error: " + error);
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                tts.speak("Microphone permission is required for voice commands.",
                        TextToSpeech.QUEUE_FLUSH, null, "permission-error");
                return;
            }
            speakThenListen("Sorry, I didn't catch that. Please try again.");
        }

        @Override
        public void onResults(Bundle results) {
            if (!isListening) {
                return;
            }
            isListening = false;
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches == null || matches.isEmpty()) {
                speakThenListen("Sorry, I didn't catch that. Please try again.");
                return;
            }
            commandField.setText(applyCompassAliases(matches.get(0)));
            handleButtonClick();
        }

        @Override
        public void onPartialResults(Bundle partialResults) { }

        @Override
        public void onEvent(int eventType, Bundle params) { }
    };

    private final UtteranceProgressListener utteranceProgressListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) { }

        @Override
        public void onDone(String utteranceId) {
            onUtteranceFinished(utteranceId);
        }

        @Override
        public void onStop(String utteranceId, boolean interrupted) {
            onUtteranceFinished(utteranceId);
        }

        @Override
        public void onError(String utteranceId) {
            onUtteranceFinished(utteranceId);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activity = this;
        Alert = new Alert(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        storyField = (TextView) findViewById(R.id.textView);
        commandField = (EditText) findViewById(R.id.editText);
        button = (Button) findViewById(R.id.button);
        progressDialog = ProgressDialog.show(MainActivity.this, "",
                "Loading. Please wait...", true);

        tts = new TextToSpeech(this, this);
        tts.setOnUtteranceProgressListener(utteranceProgressListener);

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(recognitionListener);
        } else {
            Log.e("SpeechRecognizer", "Speech recognition is not available on this device");
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CODE_RECORD_AUDIO);
        }
        storyFileTypeChecker = new StoryFileTypeChecker();
    }

    public void executeEngine(){
        try {
            executionControl = new ExecutionControl(machineInit);
            executionControl.resizeScreen(100,500);
            screenModel.init(executionControl.getMachine(), executionControl.getZsciiEncoding());
            executionControl.run();
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handleButtonClick();
                }
            });
//            commandField.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View view) {
//                    startVoiceInput();
//                }
//            });
            storyField.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(tts.isSpeaking()){
                        tts.stop();
                    }
                    else{
                        startVoiceInput();
                    }
                }
            });
            storyField.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    stopVoiceInput();
                    commandField.setVisibility(View.VISIBLE);
                    showKeyboard(activity, commandField);
                    return true;
                }
            });
            String initialText = getBufferText(screenModel);
            if(initialText.length() > 1){
                storyField.setText(initialText);
                speakThenListen(storyField.getText().toString());
            }
            else{
                commandField.setText(" ");
                handleButtonClick();
            }

        } catch (Exception ex) {
            Alert.show("Error", ex.toString() + "\n" + ex.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        if (speechRecognizer != null) {
            isListening = false;
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        // Don't forget to shutdown tts!
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onInit(int status) {

        if (status == TextToSpeech.SUCCESS) {

            int result = tts.setLanguage(Locale.getDefault());

            if (result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "This Language is not supported");
            } else {
                //Call speak stuff
                fileScanner = new FileScanner(this);
                progressDialog.dismiss();
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
                            String fileExtension = file.getName().split("\\.")[1];
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
            }

        } else {
            Log.e("TTS", "Initilization Failed!");
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

        savedGameManager = new SavedGameManager(filePath.split("\\.")[0] + ".sav");
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
        String currentCommand = commandField.getText().toString();
        String currentText = "";

        executionControl.resumeWithInput(currentCommand);
        currentText = getBufferText(screenModel);

        if(currentText.length() > 1){
            storyField.setText(currentText);
            commandField.setText("");
            commandField.setVisibility(View.GONE);
            speakThenListen(storyField.getText().toString());
        }
        else{
            commandField.setText(" ");
            handleButtonClick();
        }
    }

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

    private void showKeyboard(Context activityContext, final EditText editText){

        final InputMethodManager imm = (InputMethodManager)
                activityContext.getSystemService(Context.INPUT_METHOD_SERVICE);

        if (!editText.hasFocus()) {
            editText.requestFocus();
        }

        editText.post(new Runnable() {
            @Override
            public void run() {
                imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED);
            }
        });
    }
}
