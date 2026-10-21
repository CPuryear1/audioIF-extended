package org.redrossistudios.audioif.helpers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Answers "help" from a {@link WalkthroughIndex}: finds the first walkthrough goal the
 * player hasn't done and hints at it, more plainly each time help is asked for the same
 * goal: where it is, then which things it involves, then the exact command.
 */
public final class HintAdvisor {

    static final String ALL_DONE = "You've done everything I know about. Say score to see how you're doing.";
    static final String MORE = " Say help again for a bigger hint.";

    private static final int MAX_ROUTE = 12;
    private static final Map<String, String> DIRECTION_WORDS = new HashMap<>();
    private static final Map<String, String> OPPOSITE = new HashMap<>();
    private static final Set<String> NOT_NOUNS = new HashSet<>(Arrays.asList("the", "a", "an", "to", "with",
            "on", "onto", "in", "into", "at", "under", "from", "and", "all", "me", "it", "of", "off", "up",
            "down", "out", "over", "through", "using", "for", "about", "behind", "inside", "around"));

    static {
        String[][] words = {{"N", "north"}, {"S", "south"}, {"E", "east"}, {"W", "west"}, {"NE", "northeast"},
                {"NW", "northwest"}, {"SE", "southeast"}, {"SW", "southwest"}, {"U", "up"}, {"D", "down"},
                {"IN", "in"}, {"OUT", "out"}, {"UP", "up"}, {"DOWN", "down"}, {"NORTH", "north"},
                {"SOUTH", "south"}, {"EAST", "east"}, {"WEST", "west"}, {"NORTHEAST", "northeast"},
                {"NORTHWEST", "northwest"}, {"SOUTHEAST", "southeast"}, {"SOUTHWEST", "southwest"}};
        for (String[] pair : words) {
            DIRECTION_WORDS.put(pair[0], pair[1]);
        }
        String[][] pairs = {{"N", "S"}, {"E", "W"}, {"NE", "SW"}, {"NW", "SE"}, {"U", "D"}, {"IN", "OUT"}};
        for (String[] pair : pairs) {
            OPPOSITE.put(pair[0], pair[1]);
            OPPOSITE.put(pair[1], pair[0]);
        }
    }

    private final WalkthroughIndex index;
    // The walkthrough's own moves: room -> (command -> room).
    private final Map<String, Map<String, String>> walkthroughExits = new LinkedHashMap<>();
    // Other checked ways: the reverses of those moves, and every direction the replay tried in each room.
    private final Map<String, Map<String, String>> reverseExits = new LinkedHashMap<>();
    private int lastTarget = -2;
    private int level;

    public HintAdvisor(WalkthroughIndex index) {
        this.index = index;
        for (WalkthroughIndex.Step step : index.steps) {
            if (step.room != null && step.toRoom != null) {
                String key = key(step.command);
                walkthroughExits.computeIfAbsent(step.room, k -> new LinkedHashMap<>()).putIfAbsent(key, step.toRoom);
                // Zork's map isn't symmetric (north of the house, "e" leads behind it, but "w"
                // from there enters the kitchen), so only use ways back the replay tried.
                String back = OPPOSITE.get(key);
                if (back != null && step.returns) {
                    reverseExits.computeIfAbsent(step.toRoom, k -> new LinkedHashMap<>()).putIfAbsent(back, step.room);
                }
            }
        }
        for (Map.Entry<String, Map<String, String>> room : index.exits.entrySet()) {
            for (Map.Entry<String, String> exit : room.getValue().entrySet()) {
                reverseExits.computeIfAbsent(room.getKey(), k -> new LinkedHashMap<>())
                        .putIfAbsent(exit.getKey(), exit.getValue());
            }
        }
    }

    /**
     * A hint for a player in {@code room} with game state {@code state}. {@code knownExits} is
     * the map the player has explored (room -> (direction or command -> room)), used first for
     * directions; may be null.
     */
    public synchronized String hint(int[] state, String room, Map<String, Map<String, String>> knownExits) {
        int target = index.nextGoal(state);
        if (target < 0) {
            lastTarget = target;
            return ALL_DONE;
        }
        if (target != lastTarget) {
            lastTarget = target;
            level = 0;
        }
        int shown = level;
        level = Math.min(level + 1, 2);
        WalkthroughIndex.Step goal = index.steps.get(target);
        boolean here = goal.room == null || room == null || goal.room.equalsIgnoreCase(room);
        if (here) {
            switch (shown) {
                case 0:
                    return "There's something to do right here. Look around and examine what you see." + MORE;
                case 1:
                    return thingsHint(goal.command) + " Say help again to hear exactly what to do.";
                default:
                    return "Try: " + goal.command + ".";
            }
        }
        switch (shown) {
            case 0:
                return "Your next step is somewhere else, at " + goal.room + "." + " Say help again for directions.";
            case 1:
                return directions(room, goal.room, knownExits) + " Say help again to hear what to do there.";
            default:
                return "At " + goal.room + ", try: " + goal.command + ". " + directions(room, goal.room, knownExits);
        }
    }

    // "tie chest to rope" -> "Try doing something with the chest and the rope."
    static String thingsHint(String command) {
        String[] words = command.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<String> nouns = new ArrayList<>();
        for (int i = 1; i < words.length; i++) {
            if (!NOT_NOUNS.contains(words[i]) && !nouns.contains(words[i])) {
                nouns.add(words[i]);
            }
        }
        if (nouns.isEmpty()) {
            return "Think about the word " + words[0] + ".";
        }
        StringBuilder sb = new StringBuilder("Try doing something with the ");
        for (int i = 0; i < nouns.size(); i++) {
            if (i > 0) {
                sb.append(i == nouns.size() - 1 ? " and the " : ", the ");
            }
            sb.append(nouns.get(i));
        }
        return sb.append('.').toString();
    }

    private String directions(String from, String to, Map<String, Map<String, String>> knownExits) {
        if (knownExits != null && route(from, to, knownExits) != null) {
            return "You've been there: say go to " + to + ".";
        }
        List<String> path = route(from, to, knownExits, walkthroughExits, reverseExits);
        if (path == null) {
            return "I don't know the way from here. Try to retrace your steps.";
        }
        StringBuilder sb = new StringBuilder("From here, ");
        int shown = Math.min(path.size(), MAX_ROUTE);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(i == shown - 1 && shown == path.size() ? ", then " : ", ");
            }
            String word = DIRECTION_WORDS.get(path.get(i));
            sb.append(word != null ? (i == 0 ? "go " + word : word) : path.get(i).toLowerCase(Locale.ROOT));
        }
        if (shown < path.size()) {
            sb.append(", and so on. Ask again on the way");
        }
        return sb.append('.').toString();
    }

    // Breadth-first over the player's map and, if given, the walkthrough's moves and their reverses.
    @SafeVarargs
    private static List<String> route(String from, String to, Map<String, Map<String, String>>... maps) {
        if (from == null) {
            return null;
        }
        Map<String, List<String>> paths = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        paths.put(from, new ArrayList<>());
        queue.add(from);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equalsIgnoreCase(to)) {
                return paths.get(current);
            }
            Map<String, String> exits = new LinkedHashMap<>();
            for (Map<String, Map<String, String>> map : maps) {
                Map<String, String> more = map == null ? null : map.get(current);
                if (more != null) {
                    for (Map.Entry<String, String> exit : more.entrySet()) {
                        exits.putIfAbsent(key(exit.getKey()), exit.getValue());
                    }
                }
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

    // "s", "go south", "S" -> "S"; other commands unchanged.
    private static String key(String command) {
        String text = command.trim().replaceFirst("(?i)^(?:go|walk|run|head) ", "");
        String upper = text.toUpperCase(Locale.ROOT);
        if (DIRECTION_WORDS.containsKey(upper)) {
            String word = DIRECTION_WORDS.get(upper);
            for (Map.Entry<String, String> entry : DIRECTION_WORDS.entrySet()) {
                if (entry.getValue().equals(word) && entry.getKey().length() <= 3) {
                    return entry.getKey().equals("UP") ? "U" : entry.getKey();
                }
            }
        }
        return text;
    }

    /** The opposite move ("s" for "go north"), or null for anything else. */
    static String opposite(String command) {
        return OPPOSITE.get(key(command));
    }
}
