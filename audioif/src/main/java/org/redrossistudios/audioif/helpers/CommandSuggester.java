package org.redrossistudios.audioif.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects when the game rejected a command and suggests similar commands
 * built from words in the game's dictionary, ranked by edit distance.
 */
public final class CommandSuggester {

    /** Parser messages (lower case) that mean the game didn't accept the command. */
    private static final List<String> REJECTION_PHRASES = new ArrayList<>(Arrays.asList(
            "i don't know the word",
            "i don't know that word",
            "you can't see any such thing",
            "that's not a verb i recognise",
            "that's not a verb i recognize",
            "you used the word",
            "i didn't understand that sentence",
            "i only understood you as far as",
            "there was no verb in that sentence",
            "that sentence isn't one i recognize",
            "that sentence isn't one i recognise",
            "i beg your pardon",
            "i don't understand that",
            "noun missing",
            "too many nouns",
            "what do you want to"
    ));

    /** Messages saying the sentence itself was malformed, with no specific word to fix. */
    private static final List<String> SENTENCE_PHRASES = Arrays.asList(
            "there was no verb in that sentence",
            "that sentence isn't one i recogni",
            "i beg your pardon",
            "i didn't understand that sentence",
            "i don't understand that",
            "noun missing",
            "too many nouns",
            "what do you want to"
    );

    /** Verbs worth suggesting in place of a misheard first word ("fry" for "try" is not). */
    private static final Set<String> SUGGESTED_VERBS = new HashSet<>(Arrays.asList(
            "take", "get", "grab", "pick", "drop", "open", "close", "shut", "go", "walk", "run", "climb",
            "enter", "exit", "leave", "read", "examine", "look", "search", "put", "turn", "light", "move",
            "push", "pull", "press", "kill", "attack", "hit", "give", "throw", "eat", "drink", "wait",
            "inventory", "unlock", "lock", "wear", "remove", "tie", "untie", "dig", "fill", "pour", "wave",
            "ring", "inflate", "deflate", "listen", "smell", "touch", "say", "jump", "swim", "follow",
            "board", "launch", "lower", "raise", "lift", "break", "cut", "burn", "blow", "count", "pray",
            "knock", "kick", "rub", "shake", "squeeze", "drag", "insert", "cross", "help", "save",
            "restore", "score", "north", "south", "east", "west", "northeast", "northwest", "southeast",
            "southwest", "up", "down", "in", "out", "wind", "dive", "feel", "find", "hide", "hold", "yell",
            "shout", "sing", "sit", "stand", "talk", "tell", "ask", "fix", "melt", "play", "show",
            "slide", "spin", "stab", "strike", "swing", "unscrew", "wake", "brief", "verbose", "diagnose"));

    private static final Set<String> COMPASS_STEMS = new HashSet<>(Arrays.asList("north", "south", "east", "west"));

    /** Full spellings for common words that games store truncated (e.g. "northe"). */
    private static final List<String> COMMON_FULL_WORDS = Arrays.asList(
            "northeast", "northwest", "southeast", "southwest", "container", "unscrew", "staircase",
            "stairway", "examine", "inventory", "lantern", "mailbox", "leaflet", "advertisement",
            "unlock", "inflate", "deflate", "restart", "restore", "verbose", "brief", "superbrief",
            "diagnose", "unlight", "extinguish", "squeeze", "trapdoor", "painting", "sandwich",
            "skeleton", "screwdriver", "wrench", "torch", "garlic", "bottle", "window", "kitchen");

    /** Words that are never replaced when the game says it can't see something. */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "at", "to", "in", "on", "into", "onto", "with", "from",
            "under", "over", "and", "it", "all", "of", "up", "down", "off", "out"
    ));

    private static final Map<String, Integer> SPOKEN_NUMBERS = new LinkedHashMap<>();

    static {
        putNumber(1, "1", "one", "won", "first");
        putNumber(2, "2", "two", "to", "too", "second");
        putNumber(3, "3", "three", "tree", "third");
        putNumber(4, "4", "four", "for", "fore", "fourth");
        putNumber(5, "5", "five", "fifth");
    }

    private CommandSuggester() { }

    private static void putNumber(int value, String... words) {
        for (String word : words) {
            SPOKEN_NUMBERS.put(word, value);
        }
    }

    /** Adds another parser message that marks a rejected command. */
    public static void addRejectionPhrase(String phrase) {
        REJECTION_PHRASES.add(phrase.toLowerCase(Locale.ROOT));
    }

    public static boolean isRejection(String gameResponse) {
        if (gameResponse == null) {
            return false;
        }
        String text = gameResponse.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        for (String phrase : REJECTION_PHRASES) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns up to {@code limit} commands similar to {@code command}. Words the
     * game doesn't know are replaced with the closest dictionary words; if every
     * word is known (e.g. "You can't see any such thing"), the object words are
     * replaced instead. Candidates are ordered by edit distance, then by longest
     * shared prefix, then by smallest length difference.
     */
    public static List<String> suggest(String command, Collection<String> dictionaryWords, int limit) {
        return suggest(command, dictionaryWords, limit, null, null);
    }

    /**
     * Like {@link #suggest(String, Collection, int)}, but uses the game's reply to
     * pick the word to fix ("I don't know the word "contai"") and expands words
     * the game stores truncated ("northe") to full words seen in
     * {@code fullWords} or a built-in list; truncated words that can't be
     * expanded are not suggested when {@code fullWords} is given. Returns no
     * suggestions when the reply says the sentence as a whole made no sense.
     */
    public static List<String> suggest(String command, Collection<String> dictionaryWords, int limit,
                                       String gameResponse, Collection<String> fullWords) {
        List<String> result = new ArrayList<>();
        if (command == null || dictionaryWords == null || dictionaryWords.isEmpty() || limit <= 0) {
            return result;
        }
        String[] words = CommandNormalizer.clean(command).split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            return result;
        }

        Set<String> dictionary = new HashSet<>();
        int maxEntryLength = 0;
        for (String entry : dictionaryWords) {
            String word = entry.toLowerCase(Locale.ROOT).trim();
            if (isWord(word)) {
                dictionary.add(word);
                maxEntryLength = Math.max(maxEntryLength, word.length());
            }
        }
        if (dictionary.isEmpty()) {
            return result;
        }

        String response = gameResponse == null ? "" : gameResponse.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        String namedWord = namedWord(response);
        List<Integer> positions = new ArrayList<>();
        if (namedWord != null) {
            for (int i = 0; i < words.length; i++) {
                if (truncate(words[i], maxEntryLength).equals(truncate(namedWord, maxEntryLength))) {
                    positions.add(i);
                }
            }
        }
        if (positions.isEmpty()) {
            for (String phrase : SENTENCE_PHRASES) {
                if (response.contains(phrase)) {
                    return result;
                }
            }
            for (int i = 0; i < words.length; i++) {
                if (!dictionary.contains(truncate(words[i], maxEntryLength))) {
                    positions.add(i);
                }
            }
        }
        if (positions.isEmpty()) {
            for (int i = 1; i < words.length; i++) {
                if (!STOP_WORDS.contains(words[i])) {
                    positions.add(i);
                }
            }
        }

        List<String> knownFullWords = new ArrayList<>(COMMON_FULL_WORDS);
        Set<String> seenWords = new HashSet<>();
        if (fullWords != null) {
            for (String word : fullWords) {
                String lower = word.toLowerCase(Locale.ROOT);
                knownFullWords.add(lower);
                seenWords.add(truncate(lower, maxEntryLength));
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int position : positions) {
            String original = truncate(words[position], maxEntryLength);
            if (original.length() < 2) {
                continue;
            }
            int maxDistance = original.length() <= 4 ? 1 : 2;
            for (String entry : dictionary) {
                if (entry.equals(original) || entry.length() < 2) {
                    continue;
                }
                // Object words must be things the game has mentioned; you can't refer to unseen things.
                if (fullWords != null && position > 0 && !seenWords.contains(entry)) {
                    continue;
                }
                int distance = editDistance(original, entry);
                if (distance <= maxDistance) {
                    String spelled = entry;
                    if (entry.length() == maxEntryLength) {
                        spelled = expand(entry, knownFullWords);
                        if (spelled == null) {
                            if (fullWords != null) {
                                continue;
                            }
                            spelled = entry;
                        }
                    }
                    if (spelled.equals(words[position]) || !plausible(words[position], spelled, position)) {
                        continue;
                    }
                    String[] replaced = words.clone();
                    replaced[position] = spelled;
                    candidates.add(new Candidate(join(replaced), distance,
                            commonPrefixLength(original, entry),
                            Math.abs(original.length() - entry.length())));
                }
            }
        }

        candidates.sort((a, b) -> {
            if (a.distance != b.distance) {
                return Integer.compare(a.distance, b.distance);
            }
            if (a.prefixLength != b.prefixLength) {
                return Integer.compare(b.prefixLength, a.prefixLength);
            }
            if (a.lengthDifference != b.lengthDifference) {
                return Integer.compare(a.lengthDifference, b.lengthDifference);
            }
            return a.command.compareTo(b.command);
        });
        Set<String> seen = new HashSet<>();
        seen.add(join(words));
        for (Candidate candidate : candidates) {
            if (seen.add(candidate.command)) {
                result.add(candidate.command);
                if (result.size() == limit) {
                    break;
                }
            }
        }
        return result;
    }

    // A first word must become a common verb; a direction must not become a different direction
    // ("southwest" -> "southern") or appear from a word that wasn't one ("further" -> "northern").
    private static boolean plausible(String original, String replacement, int position) {
        if (position == 0 && !SUGGESTED_VERBS.contains(replacement)) {
            return false;
        }
        boolean originalCompass = isCompass(original);
        boolean replacementCompass = isCompass(replacement);
        if (originalCompass && CommandNormalizer.direction(original) != null) {
            return false;
        }
        return !replacementCompass || (originalCompass || original.charAt(0) == replacement.charAt(0));
    }

    private static boolean isCompass(String word) {
        for (String stem : COMPASS_STEMS) {
            if (word.startsWith(stem)) {
                return true;
            }
        }
        return false;
    }

    /** Builds the spoken prompt, e.g. "Did you mean one, take lamp, two, ..., three, something else?" */
    public static String buildPrompt(List<String> suggestions) {
        StringBuilder prompt = new StringBuilder("Did you mean");
        int i = 0;
        for (; i < suggestions.size(); i++) {
            prompt.append(' ').append(numberWord(i + 1)).append(", ").append(suggestions.get(i)).append(',');
        }
        prompt.append(' ').append(numberWord(i + 1)).append(", something else?");
        return prompt.toString();
    }

    /**
     * Parses a spoken option number such as "two", "number 2" or "option to".
     * Returns -1 if the utterance isn't just a number.
     */
    public static int parseChoice(String spoken) {
        if (spoken == null) {
            return -1;
        }
        String text = spoken.toLowerCase(Locale.ROOT).replaceAll("[.,!?]", " ").trim()
                .replaceAll("^(number|option|choice)\\s+", "");
        Integer value = SPOKEN_NUMBERS.get(text);
        return value == null ? -1 : value;
    }

    /**
     * Edit distance (optimal string alignment): insertions, deletions,
     * substitutions and swaps of adjacent letters each cost 1.
     */
    public static int editDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }

    private static int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        return i;
    }

    /** Returns X from replies like: I don't know the word "X". */
    static String namedWord(String response) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("the word [\"'\u201c]([a-z]+)[\"'\u201d]").matcher(response);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String expand(String truncated, List<String> fullWords) {
        String best = null;
        for (String word : fullWords) {
            if (word.startsWith(truncated) && (best == null || word.length() < best.length())) {
                best = word;
            }
        }
        return best;
    }

    private static String numberWord(int n) {
        String[] names = {"zero", "one", "two", "three", "four", "five"};
        return n < names.length ? names[n] : String.valueOf(n);
    }

    private static boolean isWord(String word) {
        return !word.isEmpty() && Character.isLetter(word.charAt(0));
    }

    private static String truncate(String word, int maxLength) {
        return maxLength > 0 && word.length() > maxLength ? word.substring(0, maxLength) : word;
    }

    private static String join(String[] words) {
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(word);
        }
        return builder.toString();
    }

    private static final class Candidate {
        final String command;
        final int distance;
        final int prefixLength;
        final int lengthDifference;

        Candidate(String command, int distance, int prefixLength, int lengthDifference) {
            this.command = command;
            this.distance = distance;
            this.prefixLength = prefixLength;
            this.lengthDifference = lengthDifference;
        }
    }
}
