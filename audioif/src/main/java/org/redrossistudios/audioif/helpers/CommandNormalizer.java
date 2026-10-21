package org.redrossistudios.audioif.helpers;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts recognized speech into canonical interactive-fiction commands,
 * e.g. "north west" -> "NW", "examine lamp" -> "x lamp".
 *
 * A phrase only matches at the start of the utterance and on whole words, so
 * "pick up lamp" is left alone while "up" becomes "U". When several phrases
 * match, the longest one wins. Add new mappings with {@link #put(String, String)}.
 */
public final class CommandNormalizer {

    /** Tells the normalizer whether the loaded game understands a word. */
    public interface Dictionary {
        boolean contains(String word);
    }

    private static final Map<String, String> PHRASES = new LinkedHashMap<>();

    static {
        putDirection("north west", "NW");
        putDirection("northwest", "NW");
        putDirection("north east", "NE");
        putDirection("northeast", "NE");
        putDirection("south east", "SE");
        putDirection("southeast", "SE");
        putDirection("south west", "SW");
        putDirection("southwest", "SW");
        putDirection("north", "N");
        putDirection("south", "S");
        putDirection("east", "E");
        putDirection("west", "W");
        putDirection("up", "U");
        putDirection("down", "D");

        // "x|examine": use "x" if the game knows it, otherwise fall back to "examine".
        put("examine", "x|examine");
        put("look at", "x|examine");
        put("check", "x|examine");
        put("check out", "x|examine");
        put("inspect", "x|examine");
        put("study", "x|examine");
        put("look inside", "look in");
        put("look into", "look in");
        put("inventory", "i");
        put("wait", "z");
        put("again", "g");
    }

    private CommandNormalizer() { }

    /**
     * Adds or replaces a mapping from a spoken phrase to a canonical command.
     * Separate fallbacks with "|", e.g. "x|examine"; the first whose verb the
     * game knows is used.
     */
    public static void put(String spokenPhrase, String canonicalCommand) {
        PHRASES.put(clean(spokenPhrase), canonicalCommand);
    }

    private static void putDirection(String spokenPhrase, String canonicalCommand) {
        put(spokenPhrase, canonicalCommand);
        put("go " + spokenPhrase, canonicalCommand);
    }

    /**
     * Normalizes a spoken command. A mapping is applied only if the first word of
     * its canonical command is in the game's dictionary (when one is supplied);
     * otherwise the spoken text is returned unchanged.
     */
    public static String normalize(String spoken, Dictionary dictionary) {
        if (spoken == null) {
            return "";
        }
        String text = clean(spoken);
        String bestPhrase = null;
        for (String phrase : PHRASES.keySet()) {
            boolean matches = text.equals(phrase) || text.startsWith(phrase + " ");
            if (matches && (bestPhrase == null || phrase.length() > bestPhrase.length())) {
                bestPhrase = phrase;
            }
        }
        if (bestPhrase == null) {
            return spoken.trim();
        }
        String rest = text.substring(bestPhrase.length());
        for (String canonical : PHRASES.get(bestPhrase).split("\\|")) {
            String canonicalVerb = canonical.split(" ")[0].toLowerCase(Locale.ROOT);
            if (dictionary == null || dictionary.contains(canonicalVerb)) {
                return (canonical + rest).trim();
            }
        }
        return spoken.trim();
    }

    private static String clean(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[.,!?]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
