package org.redrossistudios.audioif.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parses a stepwise walkthrough written as slash-separated commands, the format of
 * the IF Archive's Sols2 collection: {@code N/N/U/Get egg/D/Kill troll with sword}.
 * Lines before the first slash are treated as a title. A note in parentheses after
 * a command, such as {@code Wait(till the man appears)}, says to repeat it; the
 * note may be unclosed.
 */
public final class WalkthroughParser {

    public static final class Step {
        public final String command;
        /** The parenthesised note, lower case, or null. */
        public final String note;

        Step(String command, String note) {
            this.command = command;
            this.note = note;
        }

        public boolean repeats() {
            return note != null && note.matches(".*\\b(till|until|keep)\\b.*");
        }

        @Override
        public String toString() {
            return note == null ? command : command + " (" + note + ")";
        }
    }

    private WalkthroughParser() {
    }

    public static List<Step> parse(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        StringBuilder body = new StringBuilder();
        boolean started = false;
        for (String line : text.split("\\r?\\n")) {
            started |= line.indexOf('/') >= 0;
            if (started && !line.trim().isEmpty()) {
                body.append(line.trim()).append('/');
            }
        }
        List<Step> steps = new ArrayList<>();
        for (String part : body.toString().split("/")) {
            String segment = part.trim();
            String note = null;
            int open = segment.indexOf('(');
            if (open >= 0) {
                int close = segment.indexOf(')', open);
                note = segment.substring(open + 1, close < 0 ? segment.length() : close).trim()
                        .toLowerCase(Locale.ROOT);
                segment = segment.substring(0, open).trim();
            }
            segment = segment.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (!segment.isEmpty()) {
                steps.add(new Step(segment, note == null || note.isEmpty() ? null : note));
            }
        }
        return steps;
    }
}
