package org.redrossistudios.audioif.helpers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns what the player said or typed into game commands and app replies.
 * Shared by audio mode, text mode and the play harness so all behave the same.
 *
 * On top of {@link CommandNormalizer} and {@link CommandSuggester} it adds
 * things old parsers lack: several commands in one sentence ("open the mailbox
 * and take the leaflet"), "twice", "go back", "keep going", "go to the
 * kitchen" (walks a remembered route), "undo", "help", "hint", guessing what
 * "it" means, and friendlier replies when a command is rejected.
 */
public final class CommandSession {

    /** The running game. */
    public interface Game extends CommandNormalizer.Dictionary {
        /** Sends a command and returns the game's reply. */
        String send(String command);

        List<String> dictionaryWords();

        /** The current room from the status line, or null if unknown. */
        String currentRoom();

        /** Captures the state for undo, or returns null if unsupported. */
        Object snapshot();

        /** Restores a {@link #snapshot()}; returns false on failure. */
        boolean restore(Object snapshot);
    }

    /** One step of the reply: a command sent with the game's response, and/or a note from the app. */
    public static final class Exchange {
        /** The command sent to the game, or null for app-only notes. */
        public final String command;
        /** The game's reply, or null. */
        public final String response;
        /** A note from the app, or null. */
        public final String note;

        Exchange(String command, String response, String note) {
            this.command = command;
            this.response = response;
            this.note = note;
        }
    }

    public static final class Result {
        public final List<Exchange> exchanges = new ArrayList<>();
        /** True if the game rejected a command. */
        public boolean rejected;
        /** Suggestions offered for a numbered choice, empty if none. */
        public List<String> suggestions = Collections.emptyList();

        public List<String> sentCommands() {
            List<String> sent = new ArrayList<>();
            for (Exchange exchange : exchanges) {
                if (exchange.command != null) {
                    sent.add(exchange.command);
                }
            }
            return sent;
        }

        /** Game responses and notes, in order, separated by blank lines. */
        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Exchange exchange : exchanges) {
                for (String part : new String[]{exchange.response, exchange.note}) {
                    if (part != null && !part.trim().isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append("\n\n");
                        }
                        sb.append(part.trim());
                    }
                }
            }
            return sb.toString();
        }

        void note(String note) {
            exchanges.add(new Exchange(null, null, note));
        }
    }

    public static final String ASK_AGAIN = "OK, what would you like to do?";

    static final String HELP_TEXT = "Say or type short commands, for example: look, inventory, "
            + "go north, take the lamp, open the mailbox, read the leaflet. "
            + "You can chain them: open the mailbox and take the leaflet. "
            + "Say go back to retrace your last step, keep going to continue the same way, "
            + "or go to a place you have already visited, such as go to the kitchen. "
            + "Say again to repeat a command, undo to take back your last turn, "
            + "and save or restore to keep your progress.";

    static final String HINT_TEXT = "This game has no built-in hints. Try examining everything "
            + "you find, reading anything with writing on it, opening containers, "
            + "and trying every exit. Say help for a list of commands.";

    static final String QUESTION_NOTE = "The game can't answer questions. Try look, "
            + "examine something, or inventory.";

    static final String REPHRASE_NOTE = "Try a short command that starts with a verb, "
            + "like take the lamp or open the door.";

    private static final int MAX_UNDO = 10;
    private static final int MAX_REPEAT = 5;
    private static final int MAX_WALK = 25;

    private static final Set<String> HELP_PHRASES = set("help", "help me", "commands",
            "what can i do", "what can i say", "how do i play", "instructions", "list the commands",
            "what are the commands");
    private static final Set<String> HINT_PHRASES = set("hint", "hints", "give me a hint",
            "i need a hint", "i am stuck", "clue", "give me a clue", "any hints", "what should i do",
            "what do i do", "what now", "now what", "what next", "what do i do next", "what should i do next",
            "i do not know what to do");
    private static final Set<String> UNDO_PHRASES = set("undo", "undo that", "undo the last move",
            "undo last move", "undo the last turn", "take that back", "take back", "oops undo");
    private static final Set<String> BACK_PHRASES = set("go back", "back", "return", "go back the way i came",
            "retrace the steps", "go back where i came from", "go back to where i was",
            "head back", "walk back", "turn back", "turn around", "go back the way i came in");
    private static final Set<String> CONTINUE_PHRASES = set("continue", "keep going", "go on",
            "forward", "go forward", "keep walking", "carry on", "walk on", "keep moving", "move on",
            "onward", "go straight", "straight ahead", "go straight ahead");
    private static final Set<String> QUESTION_WORDS = set("what", "where", "who", "why", "how",
            "when", "which", "is", "are", "can", "could", "do", "does", "should");
    private static final Set<String> PRONOUNS = set("it", "them", "him", "her");
    private static final Set<String> NOUN_STOPS = set("here", "there", "is", "are", "was", "and",
            "with", "in", "on", "of", "to", "from", "at", "by", "into", "it", "that", "which",
            "you", "a", "an", "the", "all", "but", "as", "for", "lies", "sits", "stands");
    private static final Set<String> VERB_STARTERS = set("take", "get", "grab", "pick", "drop",
            "open", "close", "shut", "go", "walk", "run", "head", "climb", "enter", "exit", "leave",
            "read", "examine", "x", "look", "check", "inspect", "put", "place", "turn", "switch", "light",
            "move", "push", "pull", "press", "kill", "attack", "hit", "fight", "give", "throw", "eat",
            "drink", "wait", "z", "inventory", "i", "unlock", "lock", "search", "wear", "remove",
            "tie", "untie", "dig", "fill", "pour", "wave", "ring", "inflate", "deflate", "listen",
            "smell", "touch", "say", "answer", "jump", "swim", "follow", "board", "launch", "lower",
            "raise", "lift", "break", "cut", "burn", "blow", "count", "pray", "knock", "save", "score",
            "restore", "north", "south", "east", "west", "northeast", "northwest", "southeast",
            "southwest", "up", "down", "n", "s", "e", "w", "ne", "nw", "se", "sw", "u", "d",
            "then", "also", "use", "return", "continue", "undo", "help");
    private static final Map<String, String> OPPOSITE = new HashMap<>();
    private static final Map<String, String> DIRECTION_ABBREVIATION = new HashMap<>();
    private static final List<String> CANT_GO_PHRASES = Arrays.asList(
            "you can't go that way", "you must specify", "you hit your head", "i don't know the word",
            "there was no verb", "that sentence isn't one i recogni", "you can't see any such thing",
            "i only understood you as far as", "you used the word", "where do you want to go",
            "you'll have to say which", "you can't go there", "you can't see any");
    private static final Pattern GO_TO = Pattern.compile(
            "^(?:go|walk|head|run|travel|return|get|navigate|move|find the way|take me)"
                    + "(?: back)? (?:to|into|toward|towards) (?:the )?(.+)$");
    private static final Pattern USE_ON = Pattern.compile("^use (?:the )?(.+?) (?:on|with|in|to open) (?:the )?(.+)$");
    private static final Pattern REPEAT = Pattern.compile("^(.+?) (twice|two times|three times|four times|five times|(\\d) times)$");
    private static final Pattern CLAUSE_SPLIT = Pattern.compile(
            "\\s*(?:[.;!?]+\\s+|,?\\s+and then\\s+|,\\s*then\\s+|\\s+then\\s+|,\\s*after that\\s+|\\s+after that\\s+)\\s*");
    private static final Pattern AND_SPLIT = Pattern.compile("\\s*(?:,\\s*and\\s+|\\s+and\\s+|,\\s*)");

    static {
        String[][] pairs = {{"N", "S"}, {"E", "W"}, {"NE", "SW"}, {"NW", "SE"}, {"U", "D"}, {"IN", "OUT"}};
        for (String[] pair : pairs) {
            OPPOSITE.put(pair[0], pair[1]);
            OPPOSITE.put(pair[1], pair[0]);
        }
        for (Map.Entry<String, String> entry : CommandNormalizer.DIRECTIONS.entrySet()) {
            DIRECTION_ABBREVIATION.put(entry.getKey().replace(" ", ""), entry.getValue());
            DIRECTION_ABBREVIATION.put(entry.getValue().toLowerCase(Locale.ROOT), entry.getValue());
        }
        DIRECTION_ABBREVIATION.put("in", "IN");
        DIRECTION_ABBREVIATION.put("out", "OUT");
    }

    private static final class Move {
        final String direction;
        final String from;
        final String to;

        Move(String direction, String from, String to) {
            this.direction = direction;
            this.from = from;
            this.to = to;
        }
    }

    private static final class Snapshot {
        final Object state;
        final String room;
        final Move lastMove;
        final String lastNoun;

        Snapshot(Object state, String room, Move lastMove, String lastNoun) {
            this.state = state;
            this.room = room;
            this.lastMove = lastMove;
            this.lastNoun = lastNoun;
        }
    }

    private final Game game;
    private List<String> pending;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Map<String, Map<String, String>> map = new LinkedHashMap<>();
    private final Set<String> vocabulary = new LinkedHashSet<>();
    private String room;
    private Move lastMove;
    private String lastNoun;

    public CommandSession(Game game) {
        this.game = game;
    }

    /** Call with the game's opening text so the starting room is known. */
    public void start(String introText) {
        learnVocabulary(introText);
        String status = game.currentRoom();
        room = status != null ? status : roomFromResponse(introText, true);
        if (room != null) {
            map.computeIfAbsent(room, k -> new LinkedHashMap<>());
        }
    }

    /** True while numbered suggestions are waiting for a choice. */
    public boolean hasPendingSuggestions() {
        return pending != null;
    }

    public Result handle(String input) {
        Result result = new Result();
        String text = input == null ? "" : input.trim();
        List<String> offered = pending;
        pending = null;

        if (offered != null) {
            int choice = CommandSuggester.parseChoice(text);
            if (choice >= 1 && choice <= offered.size()) {
                takeSnapshot();
                runCommand(offered.get(choice - 1), result);
                return result;
            }
            if (choice == offered.size() + 1 || CommandNormalizer.canonicalize(text).equals("something else")) {
                result.note(ASK_AGAIN);
                return result;
            }
        }
        if (text.isEmpty()) {
            send(" ", result);
            return result;
        }

        String canonical = CommandNormalizer.canonicalize(text);
        if (HELP_PHRASES.contains(canonical) && !game.contains("help")) {
            result.note(HELP_TEXT);
            return result;
        }
        if (HINT_PHRASES.contains(canonical) && !game.contains("hint")) {
            result.note(HINT_TEXT);
            return result;
        }
        if (UNDO_PHRASES.contains(canonical) && !game.contains("undo")) {
            undo(result);
            return result;
        }

        takeSnapshot();
        for (String clause : splitClauses(text)) {
            if (!runClause(clause, result)) {
                break;
            }
        }
        return result;
    }

    // Returns false if the clause was rejected and the rest of the input should be skipped.
    private boolean runClause(String clause, Result result) {
        String canonical = CommandNormalizer.canonicalize(clause);
        if (canonical.isEmpty()) {
            return true;
        }
        int times = 1;
        Matcher repeat = REPEAT.matcher(canonical);
        if (repeat.matches()) {
            times = repeatCount(repeat.group(2));
            canonical = repeat.group(1);
            clause = canonical;
        }

        if (BACK_PHRASES.contains(canonical)) {
            String direction = backDirection();
            if (direction == null) {
                result.note("I don't know which way you came from yet.");
                return false;
            }
            return runCommand(direction, result);
        }
        if (CONTINUE_PHRASES.contains(canonical) && lastMove != null) {
            boolean ok = true;
            for (int i = 0; i < times && ok; i++) {
                ok = runCommand(lastMove.direction, result);
            }
            return ok;
        }
        Matcher goTo = GO_TO.matcher(canonical);
        if (goTo.matches() && CommandNormalizer.direction(goTo.group(1)) == null) {
            return goTo(clause, goTo.group(1), result);
        }

        String command = CommandNormalizer.normalize(clause, game);
        boolean ok = true;
        for (int i = 0; i < times && ok; i++) {
            ok = runCommand(command, result);
        }
        return ok;
    }

    // Sends one command and handles a rejection. Returns false if rejected.
    private boolean runCommand(String command, Result result) {
        String response = send(command, result);
        if (retryWithNoun(command, response, result)) {
            return true;
        }
        if (!isRejected(response)) {
            return true;
        }
        result.rejected = true;
        explainRejection(command, response, result);
        return false;
    }

    private String send(String command, Result result) {
        String before = room;
        String response = game.send(command);
        result.exchanges.add(new Exchange(command, response, null));
        learnVocabulary(response);
        String direction = DIRECTION_ABBREVIATION.get(command.toLowerCase(Locale.ROOT).replace(" ", ""));
        String status = game.currentRoom();
        String after = status != null ? status : roomFromResponse(response, direction != null);
        if (after != null) {
            map.computeIfAbsent(after, k -> new LinkedHashMap<>());
            if (direction != null && before != null && !after.equals(before)) {
                map.computeIfAbsent(before, k -> new LinkedHashMap<>()).put(direction, after);
                // Assume the way back is the opposite direction until a move proves otherwise.
                String opposite = OPPOSITE.get(direction);
                if (opposite != null) {
                    map.get(after).putIfAbsent(opposite, before);
                }
                lastMove = new Move(direction, before, after);
            }
            room = after;
        }
        if (direction != null && before != null && before.equals(room) && map.containsKey(before)) {
            map.get(before).remove(direction);
        }
        if (!isRejected(response)) {
            rememberNoun(command, response);
        }
        return response;
    }

    private boolean retryWithNoun(String command, String response, Result result) {
        String lower = response.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        boolean unclear = lower.contains("i don't see what you are referring to")
                || lower.contains("i'm not sure what") || lower.contains("what do you want to")
                || lower.contains("you can't see any such thing");
        if (!unclear || lastNoun == null) {
            return false;
        }
        String[] words = command.toLowerCase(Locale.ROOT).split(" ");
        boolean replaced = false;
        for (int i = 0; i < words.length; i++) {
            if (PRONOUNS.contains(words[i])) {
                words[i] = "the " + lastNoun;
                replaced = true;
            }
        }
        if (!replaced) {
            return false;
        }
        String retried = String.join(" ", words);
        String retryResponse = send(retried, result);
        if (isRejected(retryResponse) || retryResponse.toLowerCase(Locale.ROOT).contains("you can't see any")) {
            result.exchanges.remove(result.exchanges.size() - 1);
            return false;
        }
        // Hide the parser error; it didn't use a turn.
        result.exchanges.remove(result.exchanges.size() - 2);
        result.note("(I took it to mean the " + lastNoun + ".)");
        return true;
    }

    private void explainRejection(String command, String response, Result result) {
        String canonical = CommandNormalizer.canonicalize(command);
        String firstWord = canonical.split(" ")[0];
        if (QUESTION_WORDS.contains(firstWord)) {
            result.note(QUESTION_NOTE);
            return;
        }
        Matcher use = USE_ON.matcher(canonical);
        if (firstWord.equals("use") && !game.contains("use")) {
            if (use.matches()) {
                List<String> options = new ArrayList<>();
                for (String verb : new String[]{"turn", "open", "unlock", "hit"}) {
                    if (game.contains(verb)) {
                        options.add(verb + " the " + use.group(2) + " with the " + use.group(1));
                    }
                    if (options.size() == 3) {
                        break;
                    }
                }
                offer(options, result);
            } else {
                result.note("This game doesn't know use. Say what to do instead, like turn, "
                        + "open, light, wave or read.");
            }
            return;
        }
        List<String> suggestions = CommandSuggester.suggest(command, game.dictionaryWords(), 3,
                response, vocabulary);
        if (suggestions.isEmpty()) {
            if (!CommandSuggester.isRejection(response) || response.toLowerCase(Locale.ROOT).contains("can't see")) {
                return;
            }
            result.note(REPHRASE_NOTE);
            return;
        }
        offer(suggestions, result);
    }

    private void offer(List<String> options, Result result) {
        if (options.isEmpty()) {
            result.note(REPHRASE_NOTE);
            return;
        }
        pending = options;
        result.suggestions = options;
        result.note(CommandSuggester.buildPrompt(options));
    }

    private boolean goTo(String clause, String place, Result result) {
        // Let the game try first ("go into the house" may be a real command); parser
        // errors don't use a turn, so on failure the reply is replaced by the walk.
        String command = CommandNormalizer.normalize(clause, game);
        String response = send(command, result);
        if (!cantGo(response)) {
            return true;
        }
        Exchange attempt = result.exchanges.remove(result.exchanges.size() - 1);
        String target = findRoom(place);
        if (target == null) {
            result.exchanges.add(attempt);
            result.rejected = true;
            result.note("I don't know a place called " + place + " yet. Try a direction, like north or go up.");
            return false;
        }
        if (target.equals(room)) {
            result.note("You are already in " + target + ".");
            return true;
        }
        List<String> path = route(room, target);
        if (path == null) {
            result.note("I don't know a way to " + target + " from here yet.");
            return false;
        }
        String expected = room;
        for (String direction : path) {
            expected = map.get(expected).get(direction);
            if (!runCommand(direction, result)) {
                return false;
            }
            if (room == null || !room.equals(expected)) {
                result.note("I expected to reach " + expected + ", so I stopped here.");
                return false;
            }
        }
        result.note("You walk to " + target + ".");
        return true;
    }

    private String findRoom(String place) {
        String wanted = place.toLowerCase(Locale.ROOT).replaceAll("^(the|a|an) ", "");
        List<String> words = new ArrayList<>();
        for (String word : wanted.split(" ")) {
            if (word.equals("back")) {
                word = "behind";
            }
            if (!set("the", "a", "an", "room", "area", "place", "of").contains(word)) {
                words.add(word);
            }
        }
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String name : map.keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals(wanted)) {
                return name;
            }
            Set<String> nameWords = new HashSet<>(Arrays.asList(lower.split(" ")));
            if (words.isEmpty() || !nameWords.containsAll(words)) {
                continue;
            }
            List<String> path = room == null ? null : route(room, name);
            int distance = name.equals(room) ? 0 : path == null ? 1000 + name.length() : path.size();
            if (distance < bestDistance) {
                best = name;
                bestDistance = distance;
            }
        }
        return best;
    }

    private List<String> route(String from, String to) {
        if (from == null) {
            return null;
        }
        Map<String, List<String>> paths = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        paths.put(from, new ArrayList<>());
        queue.add(from);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(to)) {
                return paths.get(current);
            }
            Map<String, String> exits = map.get(current);
            if (exits == null || paths.get(current).size() >= MAX_WALK) {
                continue;
            }
            for (Map.Entry<String, String> exit : exits.entrySet()) {
                if (!paths.containsKey(exit.getValue())) {
                    List<String> path = new ArrayList<>(paths.get(current));
                    path.add(exit.getKey());
                    paths.put(exit.getValue(), path);
                    queue.add(exit.getValue());
                }
            }
        }
        return null;
    }

    private String backDirection() {
        if (lastMove == null) {
            return null;
        }
        Map<String, String> exits = room == null ? null : map.get(room);
        if (exits != null) {
            for (Map.Entry<String, String> exit : exits.entrySet()) {
                if (exit.getValue().equals(lastMove.from)) {
                    return exit.getKey();
                }
            }
        }
        return OPPOSITE.get(lastMove.direction);
    }

    private void takeSnapshot() {
        Object state = game.snapshot();
        if (state == null) {
            return;
        }
        undo.push(new Snapshot(state, room, lastMove, lastNoun));
        while (undo.size() > MAX_UNDO) {
            undo.removeLast();
        }
    }

    private void undo(Result result) {
        Snapshot snapshot = undo.poll();
        if (snapshot == null || !game.restore(snapshot.state)) {
            result.note("There is nothing to undo.");
            return;
        }
        String status = game.currentRoom();
        room = status != null ? status : snapshot.room;
        lastMove = snapshot.lastMove;
        lastNoun = snapshot.lastNoun;
        result.note(room == null ? "Undone." : "Undone. You are in " + room + ".");
    }

    private void rememberNoun(String command, String response) {
        String[] words = command.toLowerCase(Locale.ROOT).split(" ");
        boolean handledObject = false;
        for (int i = words.length - 1; i >= 1; i--) {
            String word = words[i];
            if (!NOUN_STOPS.contains(word) && !PRONOUNS.contains(word)
                    && !DIRECTION_ABBREVIATION.containsKey(word) && game.contains(word)) {
                lastNoun = word;
                handledObject = true;
                break;
            }
        }
        // Only object interactions ("open the mailbox" -> "reveals a leaflet") introduce a new noun;
        // room descriptions from look or movement mention too much scenery.
        String noun = handledObject ? lastNounIn(response) : null;
        if (noun != null) {
            lastNoun = noun;
        }
    }

    // The last "a/an/the <known words>" phrase in the response; the last word is the noun.
    private String lastNounIn(String response) {
        if (response == null) {
            return null;
        }
        String[] words = response.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s]", " ").trim().split("\\s+");
        String noun = null;
        for (int i = 0; i < words.length; i++) {
            if (!words[i].equals("a") && !words[i].equals("an") && !words[i].equals("the")) {
                continue;
            }
            String candidate = null;
            for (int j = i + 1; j < words.length && j <= i + 4; j++) {
                if (NOUN_STOPS.contains(words[j]) || !game.contains(words[j])) {
                    break;
                }
                candidate = words[j];
            }
            if (candidate != null) {
                noun = candidate;
            }
        }
        return noun;
    }

    private void learnVocabulary(String response) {
        if (response == null) {
            return;
        }
        for (String word : response.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (word.length() >= 3) {
                vocabulary.add(word);
            }
        }
    }

    private boolean isRejected(String response) {
        return CommandSuggester.isRejection(response);
    }

    private static boolean cantGo(String response) {
        String lower = response == null ? "" : response.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        for (String phrase : CANT_GO_PHRASES) {
            if (lower.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    // A room heading is a short capitalised first line without sentence punctuation, e.g. "West of House".
    static String roomFromResponse(String response, boolean moved) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        String[] lines = response.trim().split("\\r?\\n");
        for (int i = 0; i < Math.min(lines.length, moved ? 1 : 6); i++) {
            String line = lines[i].trim();
            if (looksLikeRoomName(line)) {
                return line;
            }
        }
        return null;
    }

    private static boolean looksLikeRoomName(String line) {
        if (line.isEmpty() || line.matches(".*[.!?:,\"]$") || line.contains(">")) {
            return false;
        }
        String[] words = line.split("\\s+");
        if (words.length > 6) {
            return false;
        }
        Set<String> small = set("of", "the", "and", "a", "an", "in", "on", "to", "at", "by", "with");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i > 0 && small.contains(word)) {
                continue;
            }
            if (!Character.isUpperCase(word.charAt(0))) {
                return false;
            }
        }
        return true;
    }

    /** Splits "open the mailbox and take the leaflet. go north" into separate commands. */
    List<String> splitClauses(String input) {
        List<String> clauses = new ArrayList<>();
        for (String sentence : CLAUSE_SPLIT.split(input.trim())) {
            String[] parts = AND_SPLIT.split(sentence);
            StringBuilder current = new StringBuilder();
            Matcher separators = AND_SPLIT.matcher(sentence);
            List<String> seps = new ArrayList<>();
            while (separators.find()) {
                seps.add(separators.group());
            }
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.isEmpty()) {
                    continue;
                }
                String first = CommandNormalizer.canonicalize(part).split(" ")[0];
                if (current.length() > 0 && !VERB_STARTERS.contains(first)) {
                    current.append(seps.get(i - 1)).append(part);
                } else {
                    if (current.length() > 0) {
                        clauses.add(current.toString());
                    }
                    current = new StringBuilder(part);
                }
            }
            if (current.length() > 0) {
                clauses.add(current.toString());
            }
        }
        return clauses;
    }

    private static int repeatCount(String phrase) {
        switch (phrase) {
            case "twice": case "two times": return 2;
            case "three times": return 3;
            case "four times": return 4;
            case "five times": return 5;
            default:
                return Math.max(1, Math.min(MAX_REPEAT, Integer.parseInt(phrase.substring(0, 1))));
        }
    }

    private static Set<String> set(String... words) {
        return new HashSet<>(Arrays.asList(words));
    }
}
