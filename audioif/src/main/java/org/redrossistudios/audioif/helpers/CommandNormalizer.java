package org.redrossistudios.audioif.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts natural speech into commands an old-style interactive-fiction parser
 * understands, e.g. "please go north west" -> "NW", "what's in my bag?" ->
 * "look in the bag", "switch the lamp back on" -> "turn the lamp on".
 *
 * Steps, in order:
 * <ol>
 * <li>{@link #canonicalize}: lower case, strip punctuation, expand contractions
 *     ("what's" -> "what is"), drop leading fillers ("please", "can I",
 *     "let's") and trailing fillers ("please", "too", "out of the way"), and
 *     replace words old parsers don't know ("my"/"this" -> "the",
 *     "everything" -> "all", "upstairs" -> "up", "back" before a direction).</li>
 * <li>A motion sentence ending in a direction becomes just that direction
 *     ("follow the passage north" -> "N").</li>
 * <li>Phrase map: a phrase only matches whole words at the start of the
 *     utterance, and the longest match wins. The rest of the utterance is
 *     kept, except for terminal mappings (directions, "wait", "inventory",
 *     "look"...) where it is dropped ("wait for the water" -> "z").</li>
 * </ol>
 * Extend it with {@link #put}, {@link #putTerminal}, {@link #addLeadingFiller}
 * and {@link #addTrailingFiller}.
 */
public final class CommandNormalizer {

    /** Tells the normalizer whether the loaded game understands a word. */
    public interface Dictionary {
        boolean contains(String word);
    }

    private static final Map<String, String> PHRASES = new LinkedHashMap<>();
    private static final Set<String> TERMINAL = new HashSet<>();
    private static final List<String> LEADING_FILLERS = new ArrayList<>();
    private static final List<String> TRAILING_FILLERS = new ArrayList<>();
    private static final Map<String, String> CONTRACTIONS = new LinkedHashMap<>();
    private static final Map<String, String> WORD_REPLACEMENTS = new LinkedHashMap<>();

    /** Direction words, mapped to their canonical abbreviation. */
    static final Map<String, String> DIRECTIONS = new LinkedHashMap<>();

    /** Words that make a sentence ending in a direction a movement command. */
    private static final Set<String> MOTION_VERBS = new HashSet<>(Arrays.asList(
            "go", "walk", "head", "run", "follow", "continue", "keep", "travel", "proceed",
            "wander", "crawl", "return", "move", "hurry", "jog", "step", "sneak"));

    /** Words after which "back" is filler: "go back down", "put it back in". */
    private static final Set<String> AFTER_BACK = new HashSet<>(Arrays.asList(
            "in", "into", "inside", "on", "onto", "off", "down", "up", "out", "outside"));

    static {
        DIRECTIONS.put("north west", "NW");
        DIRECTIONS.put("northwest", "NW");
        DIRECTIONS.put("north east", "NE");
        DIRECTIONS.put("northeast", "NE");
        DIRECTIONS.put("south east", "SE");
        DIRECTIONS.put("southeast", "SE");
        DIRECTIONS.put("south west", "SW");
        DIRECTIONS.put("southwest", "SW");
        DIRECTIONS.put("north", "N");
        DIRECTIONS.put("south", "S");
        DIRECTIONS.put("east", "E");
        DIRECTIONS.put("west", "W");
        DIRECTIONS.put("up", "U");
        DIRECTIONS.put("down", "D");
        for (Map.Entry<String, String> direction : new LinkedHashMap<>(DIRECTIONS).entrySet()) {
            for (String prefix : new String[]{"", "go ", "walk ", "head ", "run ", "move ", "travel "}) {
                putTerminal(prefix + direction.getKey(), direction.getValue());
                if (!prefix.isEmpty()) {
                    putTerminal(prefix + direction.getValue().toLowerCase(Locale.ROOT), direction.getValue());
                }
            }
        }

        CONTRACTIONS.put("what's", "what is");
        CONTRACTIONS.put("where's", "where is");
        CONTRACTIONS.put("who's", "who is");
        CONTRACTIONS.put("how's", "how is");
        CONTRACTIONS.put("that's", "that is");
        CONTRACTIONS.put("there's", "there is");
        CONTRACTIONS.put("it's", "it is");
        CONTRACTIONS.put("let's", "let us");
        CONTRACTIONS.put("i'm", "i am");
        CONTRACTIONS.put("i'd", "i would");
        CONTRACTIONS.put("i'll", "i will");
        CONTRACTIONS.put("i've", "i have");
        CONTRACTIONS.put("don't", "do not");
        CONTRACTIONS.put("can't", "cannot");
        CONTRACTIONS.put("won't", "will not");
        CONTRACTIONS.put("gonna", "going to");
        CONTRACTIONS.put("wanna", "want to");

        for (String filler : new String[]{
                "please", "can i", "could i", "may i", "can you", "could you", "would you", "will you",
                "i want to", "i would like to", "i would", "i need to", "i will", "i am going to",
                "i shall", "i should", "i try to", "let us", "let me", "try to", "now", "ok", "okay",
                "just", "then", "and", "so", "um", "uh", "well", "alright", "all right", "hey",
                "next", "first", "quickly", "carefully"}) {
            addLeadingFiller(filler);
        }
        for (String filler : new String[]{
                "please", "too", "again", "now", "as well", "for me", "right now", "aside",
                "out of the way", "wider", "all the way", "quickly", "carefully", "thanks",
                "thank you", "then", "already", "here"}) {
            addTrailingFiller(filler);
        }

        WORD_REPLACEMENTS.put("everything", "all");
        WORD_REPLACEMENTS.put("all of it", "all");
        WORD_REPLACEMENTS.put("all of them", "all");
        WORD_REPLACEMENTS.put("all my stuff", "all");
        WORD_REPLACEMENTS.put("all of my stuff", "all");
        WORD_REPLACEMENTS.put("all my things", "all");
        WORD_REPLACEMENTS.put("all the stuff", "all");
        WORD_REPLACEMENTS.put("all the things", "all");
        WORD_REPLACEMENTS.put("upstairs", "up");
        WORD_REPLACEMENTS.put("downstairs", "down");
        WORD_REPLACEMENTS.put("lantern light", "lantern");

        // "x|examine": use "x" if the game knows it, otherwise fall back to "examine".
        put("examine", "x|examine");
        put("x", "x|examine");
        put("look at", "x|examine");
        put("check", "x|examine");
        put("check out", "x|examine");
        put("inspect", "x|examine");
        put("study", "x|examine");
        put("describe", "x|examine");
        put("look inside", "look in");
        put("look into", "look in");
        put("what is in", "look in");
        put("what is inside", "look in");
        put("again", "g");
        putTerminal("repeat that", "g");
        putTerminal("do that again", "g");

        // Looking around ("look" alone keeps the rest: "look under the rug").
        putTerminal("look around", "look");
        putTerminal("where am i", "look");
        putTerminal("describe the room", "look");
        putTerminal("describe the area", "look");
        putTerminal("describe the surroundings", "look");
        putTerminal("describe where i am", "look");
        putTerminal("what is around", "look");
        putTerminal("what is around me", "look");
        putTerminal("what is here", "look");
        putTerminal("what do i see", "look");
        putTerminal("what can i see", "look");

        // Inventory.
        putTerminal("inventory", "i");
        putTerminal("the inventory", "i");
        putTerminal("check the inventory", "i");
        putTerminal("show the inventory", "i");
        putTerminal("list the inventory", "i");
        putTerminal("open the inventory", "i");
        putTerminal("what am i carrying", "i");
        putTerminal("what am i holding", "i");
        putTerminal("what do i have", "i");
        putTerminal("what have i got", "i");
        putTerminal("what is in the inventory", "i");
        putTerminal("what is in the pockets", "i");
        putTerminal("list the items", "i");

        // Taking and dropping.
        put("pick all up", "take all");
        put("get rid of", "drop");
        put("throw away", "drop");
        put("toss away", "drop");
        put("put down", "drop");
        put("set down", "drop");

        // Light: Zork I has no "switch" verb.
        put("switch on", "turn on");
        put("switch off", "turn off");
        put("switch", "turn");

        // Movement verbs old parsers lack ("head down" -> "walk down").
        put("head", "walk");
        put("tighten", "turn");
        put("loosen", "turn");
        put("unscrew", "turn");

        // Waiting and game commands.
        putTerminal("wait", "z");
        putTerminal("score", "score");
        putTerminal("what is the score", "score");
        putTerminal("show the score", "score");
        putTerminal("check the score", "score");
        putTerminal("save", "save");
        putTerminal("save the game", "save");
        putTerminal("save game", "save");
        putTerminal("save progress", "save");
        putTerminal("restore", "restore");
        putTerminal("load the game", "restore");
        putTerminal("load game", "restore");
        putTerminal("load the saved game", "restore");
        putTerminal("restore the game", "restore");
        putTerminal("restart", "restart");
        putTerminal("start over", "restart");
        putTerminal("restart the game", "restart");
        putTerminal("quit the game", "quit");
        putTerminal("exit the game", "quit");
    }

    private CommandNormalizer() { }

    /**
     * Adds or replaces a mapping from a spoken phrase to a canonical command;
     * words after the phrase are kept. Separate fallbacks with "|", e.g.
     * "x|examine"; the first whose verb the game knows is used.
     */
    public static void put(String spokenPhrase, String canonicalCommand) {
        PHRASES.put(canonicalize(spokenPhrase), canonicalCommand);
    }

    /** Like {@link #put}, but anything said after the phrase is dropped. */
    public static void putTerminal(String spokenPhrase, String canonicalCommand) {
        String key = canonicalize(spokenPhrase);
        PHRASES.put(key, canonicalCommand);
        TERMINAL.add(key);
    }

    /** Adds a phrase that is removed from the start of an utterance ("please"). */
    public static void addLeadingFiller(String phrase) {
        LEADING_FILLERS.add(clean(phrase));
    }

    /** Adds a phrase that is removed from the end of an utterance ("please"). */
    public static void addTrailingFiller(String phrase) {
        TRAILING_FILLERS.add(clean(phrase));
    }

    /** Returns the canonical abbreviation ("N") if the text is just a direction, else null. */
    public static String direction(String text) {
        return DIRECTIONS.get(clean(text));
    }

    /**
     * Normalizes a spoken command. A mapping is applied only if the first word of
     * its canonical command is in the game's dictionary (when one is supplied);
     * otherwise the cleaned-up text is returned.
     */
    public static String normalize(String spoken, Dictionary dictionary) {
        if (spoken == null) {
            return "";
        }
        String text = canonicalize(spoken);
        if (text.isEmpty()) {
            return spoken.trim();
        }

        String endDirection = motionEndingInDirection(text);
        if (endDirection != null && (dictionary == null || dictionary.contains(endDirection.toLowerCase(Locale.ROOT)))) {
            return endDirection;
        }

        String bestPhrase = null;
        for (String phrase : PHRASES.keySet()) {
            boolean matches = text.equals(phrase) || text.startsWith(phrase + " ");
            if (matches && (bestPhrase == null || phrase.length() > bestPhrase.length())) {
                bestPhrase = phrase;
            }
        }
        if (bestPhrase == null) {
            return text;
        }
        String rest = TERMINAL.contains(bestPhrase) ? "" : text.substring(bestPhrase.length());
        for (String canonical : PHRASES.get(bestPhrase).split("\\|")) {
            String canonicalVerb = canonical.split(" ")[0].toLowerCase(Locale.ROOT);
            if (dictionary == null || dictionary.contains(canonicalVerb)) {
                return (canonical + rest).trim();
            }
        }
        return text;
    }

    /**
     * Cleans an utterance without applying the phrase map: lower case, no
     * punctuation, contractions expanded, fillers removed and replacement words
     * applied. Never returns an empty string for non-empty input that only
     * consists of fillers ("okay" stays "okay").
     */
    public static String canonicalize(String spoken) {
        if (spoken == null) {
            return "";
        }
        String text = clean(spoken);
        for (Map.Entry<String, String> contraction : CONTRACTIONS.entrySet()) {
            text = replaceWords(text, contraction.getKey(), contraction.getValue());
        }
        text = stripFillers(text);
        for (Map.Entry<String, String> replacement : WORD_REPLACEMENTS.entrySet()) {
            text = replaceWords(text, replacement.getKey(), replacement.getValue());
        }
        return replaceDeterminers(text);
    }

    private static String stripFillers(String text) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String filler : LEADING_FILLERS) {
                if (text.startsWith(filler + " ")) {
                    text = text.substring(filler.length() + 1);
                    changed = true;
                }
            }
            for (String filler : TRAILING_FILLERS) {
                if (text.endsWith(" " + filler)) {
                    text = text.substring(0, text.length() - filler.length() - 1);
                    changed = true;
                }
            }
        }
        return text;
    }

    // "my sword" -> "the sword", "this room" -> "the room", "and a guidebook" -> "and the guidebook",
    // "go back down" -> "go down". A word at the very end is left alone ("take that").
    private static String replaceDeterminers(String text) {
        String[] words = text.split(" ");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            boolean last = i == words.length - 1;
            if (!last) {
                switch (word) {
                    case "my": case "your": case "this": case "these": case "those": case "that":
                    case "our": case "his":
                        word = "the";
                        break;
                    case "a": case "an":
                        if (i > 0 && words[i - 1].equals("and")) {
                            word = "the";
                        }
                        break;
                    case "back":
                        String next = words[i + 1];
                        if (AFTER_BACK.contains(next) || DIRECTIONS.containsKey(next)) {
                            continue;
                        }
                        break;
                    default:
                        break;
                }
            }
            out.add(word);
        }
        return String.join(" ", out);
    }

    // "follow the passage north" -> "N"; needs at least one word between verb and direction
    // only when the verb could be followed by a direction directly (handled by the phrase map).
    private static String motionEndingInDirection(String text) {
        String[] words = text.split(" ");
        if (words.length < 2 || !MOTION_VERBS.contains(words[0])) {
            return null;
        }
        if (words[0].equals("move") && words.length > 2) {
            return null; // "move the rug north" is about the rug
        }
        if (words.length >= 3) {
            String two = words[words.length - 2] + " " + words[words.length - 1];
            if (DIRECTIONS.containsKey(two)) {
                return DIRECTIONS.get(two);
            }
        }
        String last = words[words.length - 1];
        if (DIRECTIONS.containsKey(last) && !last.equals("up") && !last.equals("down")) {
            return DIRECTIONS.get(last);
        }
        return null;
    }

    private static String replaceWords(String text, String from, String to) {
        if (text.equals(from)) {
            return to;
        }
        String padded = " " + text + " ";
        String result = padded.replace(" " + from + " ", " " + to + " ");
        while (!result.equals(padded)) {
            padded = result;
            result = padded.replace(" " + from + " ", " " + to + " ");
        }
        return result.trim();
    }

    static String clean(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replace('\u2019', '\'')
                .replaceAll("[.,!?;:\"]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
