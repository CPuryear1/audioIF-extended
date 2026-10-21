package org.redrossistudios.audioif.harness;

import org.junit.Assume;
import org.junit.Test;
import org.redrossistudios.audioif.helpers.CommandInput;
import org.redrossistudios.audioif.helpers.CommandNormalizer;
import org.redrossistudios.audioif.helpers.CommandSuggester;
import org.zmpp.ExecutionControl;
import org.zmpp.iff.FormChunk;
import org.zmpp.iff.WritableFormChunk;
import org.zmpp.io.IOSystem;
import org.zmpp.vm.MachineFactory;
import org.zmpp.vm.SaveGameDataStore;
import org.zmpp.windowing.AnnotatedText;
import org.zmpp.windowing.BufferedScreenModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer tool: plays a game headlessly through the same input pipeline as
 * text mode (CommandInput -> CommandNormalizer/CommandSuggester -> ZMPP).
 *
 * Skipped unless given a commands file:
 *   gradlew :audioif:testDebugUnitTest --tests *PlayHarness* -Pplay=C:\path\commands.txt
 * Optional: -PplayGame=path\to\story.z3 (defaults to the bundled Zork I), -PplaySeed=1234.
 *
 * Commands are replayed from the start on every run, one per line; blank lines
 * and lines starting with '#' are ignored. The random generator is seeded so
 * replays are deterministic. Output goes to audioif/build/play/:
 * transcript.md, transcript.json and latest.txt (the last three turns).
 */
public class PlayHarness {

    static final class Turn {
        String typed;
        String sent;
        String reply;
        String response = "";
        boolean rejected;
        List<String> suggestions = new ArrayList<>();
    }

    private BufferedScreenModel screenModel;
    private ExecutionControl executionControl;

    @Test
    public void play() throws Exception {
        String commandsPath = System.getProperty("play.commands", "");
        Assume.assumeTrue("pass -Pplay=<commands file> to run", !commandsPath.isEmpty());
        String gamePath = System.getProperty("play.game", "");
        if (gamePath.isEmpty()) {
            gamePath = "src/main/assets/games/Zork I.z3";
        }
        short seed = Short.parseShort(System.getProperty("play.seed", "1234"));

        List<String> commands = new ArrayList<>();
        for (String line : Files.readAllLines(new File(commandsPath).toPath(), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                commands.add(trimmed);
            }
        }

        File outDir = new File("build/play");
        outDir.mkdirs();
        start(new File(gamePath));
        executionControl.getMachine().random((short) -Math.abs(seed));
        List<String> dictionaryWords = executionControl.getMachine().getDictionaryWords();
        CommandNormalizer.Dictionary dictionary = word -> executionControl.getMachine().lookupToken(0, word) != 0;

        String intro = readBuffer();
        if (intro.length() <= 1) {
            intro = send(" ");
        }

        List<Turn> turns = new ArrayList<>();
        List<String> pending = null;
        for (String typed : commands) {
            Turn turn = new Turn();
            turn.typed = typed;
            CommandInput.Result resolved = CommandInput.resolve(typed, pending, dictionary);
            pending = null;
            if (resolved.reply != null) {
                turn.reply = resolved.reply;
                turns.add(turn);
                continue;
            }
            turn.sent = resolved.command;
            turn.response = send(resolved.command);
            if (!resolved.command.trim().isEmpty() && CommandSuggester.isRejection(turn.response)) {
                turn.rejected = true;
                turn.suggestions = CommandSuggester.suggest(resolved.command, dictionaryWords, 3);
                if (!turn.suggestions.isEmpty()) {
                    pending = turn.suggestions;
                }
            }
            turns.add(turn);
        }

        write(new File(outDir, "transcript.md"), markdown(intro, turns, 0));
        write(new File(outDir, "latest.txt"), markdown(null, turns, Math.max(0, turns.size() - 3)));
        write(new File(outDir, "transcript.json"), json(turns));
    }

    private void start(File game) throws Exception {
        MachineFactory.MachineInitStruct init = new MachineFactory.MachineInitStruct();
        init.storyFile = new FileInputStream(game);
        init.nativeImageFactory = inputStream -> null;
        init.saveGameDataStore = new SaveGameDataStore() {
            public boolean saveFormChunk(WritableFormChunk formchunk) { return false; }
            public FormChunk retrieveFormChunk() { return null; }
        };
        init.ioSystem = new IOSystem() {
            public Reader getInputStreamReader() { return null; }
            public Writer getTranscriptWriter() { return null; }
        };
        screenModel = new BufferedScreenModel();
        init.statusLine = screenModel;
        init.screenModel = screenModel;
        init.keyboardInputStream = new org.zmpp.io.InputStream() {
            public void close() { }
            public String readLine() { return null; }
        };
        executionControl = new ExecutionControl(init);
        executionControl.resizeScreen(100, 500);
        screenModel.init(executionControl.getMachine(), executionControl.getZsciiEncoding());
        executionControl.run();
    }

    // Mirrors TextModeActivity.runCommand: an empty response means the game wants more input.
    private String send(String command) {
        String text = "";
        for (int i = 0; i < 5 && text.length() <= 1; i++) {
            executionControl.resumeWithInput(i == 0 ? command : " ");
            text = readBuffer();
        }
        return text;
    }

    private String readBuffer() {
        StringBuilder result = new StringBuilder();
        for (AnnotatedText text : screenModel.getLowerBuffer()) {
            result.append(text.getText());
        }
        String s = result.toString();
        return s.length() > 2 ? s.substring(0, s.length() - 2).trim() : s;
    }

    private static String markdown(String intro, List<Turn> turns, int from) {
        StringBuilder sb = new StringBuilder();
        if (intro != null) {
            sb.append(intro).append("\n\n");
        }
        for (int i = from; i < turns.size(); i++) {
            Turn t = turns.get(i);
            sb.append("## ").append(i + 1).append(". > ").append(t.typed);
            if (t.sent != null && !t.sent.trim().equalsIgnoreCase(t.typed.trim())) {
                sb.append("  [sent: ").append(t.sent).append("]");
            }
            if (t.rejected) {
                sb.append("  **REJECTED**");
            }
            sb.append("\n\n");
            sb.append(t.reply != null ? "(app) " + t.reply : t.response).append("\n\n");
            if (!t.suggestions.isEmpty()) {
                sb.append("(app) ").append(CommandSuggester.buildPrompt(t.suggestions)).append("\n\n");
            }
        }
        return sb.toString();
    }

    private static String json(List<Turn> turns) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < turns.size(); i++) {
            Turn t = turns.get(i);
            sb.append("  {\"n\":").append(i + 1)
                    .append(",\"typed\":").append(q(t.typed))
                    .append(",\"sent\":").append(q(t.sent))
                    .append(",\"appReply\":").append(q(t.reply))
                    .append(",\"rejected\":").append(t.rejected)
                    .append(",\"suggestions\":[");
            for (int j = 0; j < t.suggestions.size(); j++) {
                sb.append(j > 0 ? "," : "").append(q(t.suggestions.get(j)));
            }
            sb.append("],\"response\":").append(q(t.response)).append("}")
                    .append(i < turns.size() - 1 ? ",\n" : "\n");
        }
        return sb.append("]\n").toString();
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    private static void write(File file, String text) throws Exception {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
