package org.redrossistudios.audioif.helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A plain-text log of one audio-mode session, for debugging speech: what the app said,
 * every alternative the recognizer heard (with confidence), what was used and sent to the
 * game, and recognizer errors. One file per session; only the newest {@link #KEEP} are kept.
 */
public class PlayLog {

    public static final int KEEP = 20;

    private final File file;
    private final Clock clock;

    public interface Clock {
        long now();
    }

    public PlayLog(File folder, String gameName) {
        this(folder, gameName, System::currentTimeMillis);
    }

    PlayLog(File folder, String gameName, Clock clock) {
        this.clock = clock;
        folder.mkdirs();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(new Date(clock.now()));
        String safeName = gameName == null ? "game" : gameName.replaceAll("[^A-Za-z0-9 ._-]", "").trim();
        file = new File(folder, "play-" + stamp + "-" + (safeName.isEmpty() ? "game" : safeName) + ".txt");
        prune(folder);
        write("START", "audio mode, " + (gameName == null ? "unknown game" : gameName));
    }

    public File file() {
        return file;
    }

    /** Text the app speaks: game replies, app notes, prompts. */
    public void said(String text) {
        write("SAY", text);
    }

    /** Recognizer alternatives, best first; confidences may be null or shorter than the list. */
    public void heard(List<String> alternatives, float[] confidences) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < alternatives.size(); i++) {
            line.append(i > 0 ? " | " : "").append(i + 1).append(". ").append(alternatives.get(i));
            if (confidences != null && i < confidences.length && confidences[i] >= 0) {
                line.append(String.format(Locale.US, " (%.2f)", confidences[i]));
            }
        }
        write("HEARD", line.toString());
    }

    public void typed(String text) {
        write("TYPED", text);
    }

    /** What the session made of the input: the chosen text and the commands sent to the game. */
    public void result(CommandSession.Result result) {
        if (result.heard != null) {
            write("USED", result.heard);
        }
        List<String> sent = result.sentCommands();
        if (!sent.isEmpty()) {
            write("SENT", String.join("; ", sent) + (result.rejected ? "  [rejected]" : ""));
        } else if (result.rejected) {
            write("SENT", "[rejected]");
        }
    }

    public void event(String text) {
        write("EVENT", text);
    }

    private synchronized void write(String kind, String text) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(clock.now()));
        String body = text == null ? "" : text.trim().replace("\r", "").replace("\n", "\n" + pad(time, kind));
        try (Writer out = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            out.write(time + " " + String.format(Locale.US, "%-5s", kind) + " " + body + "\n");
        } catch (IOException e) {
            // Logging must never break play.
        }
    }

    private static String pad(String time, String kind) {
        char[] spaces = new char[time.length() + 7];
        Arrays.fill(spaces, ' ');
        return new String(spaces);
    }

    private static void prune(File folder) {
        File[] logs = folder.listFiles((dir, name) -> name.startsWith("play-") && name.endsWith(".txt"));
        if (logs == null || logs.length < KEEP) {
            return;
        }
        Arrays.sort(logs, Comparator.comparing(File::getName).reversed());
        for (int i = KEEP - 1; i < logs.length; i++) {
            logs[i].delete();
        }
    }

    /** The newest log in the folder, or null. */
    public static File latest(File folder) {
        File[] logs = folder.listFiles((dir, name) -> name.startsWith("play-") && name.endsWith(".txt"));
        if (logs == null || logs.length == 0) {
            return null;
        }
        Arrays.sort(logs, Comparator.comparing(File::getName));
        return logs[logs.length - 1];
    }

    public static String read(File log) throws IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(log)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) {
                bytes.write(buffer, 0, n);
            }
            return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
