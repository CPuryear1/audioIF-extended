package org.redrossistudios.audioif.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommandSessionTest {

    /** A tiny Zork-like world: Field -N-> Path -N-> Clearing, Path -E-> Hut. */
    private static final class FakeGame implements CommandSession.Game {
        final Set<String> words = new HashSet<>(Arrays.asList(
                "n", "s", "e", "w", "north", "south", "east", "west", "take", "open", "drop", "examine",
                "the", "lamp", "box", "leaflet", "turn", "unlock", "door", "key", "with", "look", "in", "i",
                "z", "read", "it", "what"));
        final Map<String, Map<String, String>> exits = new HashMap<>();
        final Map<String, String> replies = new HashMap<>();
        final List<String> sent = new ArrayList<>();
        String room = "Field";

        FakeGame() {
            exit("Field", "N", "Path");
            exit("Path", "S", "Field");
            exit("Path", "N", "Clearing");
            exit("Clearing", "S", "Path");
            exit("Path", "E", "Hut");
            exit("Hut", "W", "Path");
            replies.put("open the box", "Opening the box reveals a leaflet.");
            replies.put("take the leaflet", "Taken.");
            replies.put("take the lamp", "Taken.");
            replies.put("read the leaflet", "WELCOME!");
            replies.put("read it", "I don't see what you are referring to.");
            replies.put("z", "Time passes...");
        }

        void exit(String from, String direction, String to) {
            exits.computeIfAbsent(from, k -> new HashMap<>()).put(direction, to);
        }

        @Override
        public String send(String command) {
            sent.add(command);
            String upper = command.toUpperCase();
            if (upper.matches("N|S|E|W")) {
                String to = exits.getOrDefault(room, new HashMap<>()).get(upper);
                if (to == null) {
                    return "You can't go that way.";
                }
                room = to;
                return to + "\nYou are in the " + to.toLowerCase() + ".";
            }
            if (replies.containsKey(command)) {
                return replies.get(command);
            }
            for (String word : command.toLowerCase().split(" ")) {
                if (!words.contains(word)) {
                    return "I don't know the word \"" + word + "\".";
                }
            }
            return "You can't see any such thing.";
        }

        @Override
        public List<String> dictionaryWords() {
            return new ArrayList<>(words);
        }

        @Override
        public String currentRoom() {
            return room;
        }

        @Override
        public Object snapshot() {
            return room;
        }

        @Override
        public boolean restore(Object snapshot) {
            room = (String) snapshot;
            return true;
        }

        @Override
        public boolean contains(String word) {
            return words.contains(word);
        }
    }

    private FakeGame game;
    private CommandSession session;

    @Before
    public void setUp() {
        game = new FakeGame();
        session = new CommandSession(game);
        session.start("Field\nYou are in a field. There is a lamp here.");
    }

    @Test
    public void normalizesBeforeSending() {
        session.handle("please go north");
        assertEquals(Arrays.asList("N"), game.sent);
    }

    @Test
    public void splitsCompoundCommands() {
        CommandSession.Result result = session.handle("open the box and take the leaflet. go north");
        assertEquals(Arrays.asList("open the box", "take the leaflet", "N"), result.sentCommands());
    }

    @Test
    public void keepsObjectListsTogether() {
        assertEquals(Arrays.asList("take the lamp and the leaflet"),
                session.splitClauses("take the lamp and the leaflet"));
        assertEquals(Arrays.asList("take the lamp", "go north"),
                session.splitClauses("take the lamp, then go north"));
    }

    @Test
    public void stopsAfterRejectedClause() {
        CommandSession.Result result = session.handle("frobnicate the box and go north");
        assertTrue(result.rejected);
        assertEquals(1, result.sentCommands().size());
    }

    @Test
    public void repeatsTwice() {
        session.handle("go north twice");
        assertEquals(Arrays.asList("N", "N"), game.sent);
        assertEquals("Clearing", game.room);
    }

    @Test
    public void goBackReversesLastMove() {
        session.handle("go north");
        session.handle("go east");
        session.handle("go back");
        assertEquals("Path", game.room);
    }

    @Test
    public void keepGoingRepeatsLastDirection() {
        session.handle("go north");
        session.handle("keep going");
        assertEquals("Clearing", game.room);
    }

    @Test
    public void goToWalksRememberedRoute() {
        session.handle("go north");
        session.handle("go north");
        CommandSession.Result result = session.handle("go to the field");
        assertEquals("Field", game.room);
        assertTrue(result.text().contains("You walk to Field."));
        assertFalse(result.text().contains("I don't know the word"));
    }

    @Test
    public void goToUnknownPlaceExplains() {
        CommandSession.Result result = session.handle("go to the castle");
        assertTrue(result.text().contains("I don't know a place called castle"));
    }

    @Test
    public void undoRestoresPreviousTurn() {
        session.handle("go north");
        CommandSession.Result result = session.handle("undo");
        assertEquals("Field", game.room);
        assertTrue(result.text().contains("Undone. You are in Field."));
    }

    @Test
    public void undoWithNothingToUndo() {
        assertEquals("There is nothing to undo.", session.handle("undo").text());
    }

    @Test
    public void helpAndHintAreAnsweredByTheApp() {
        assertEquals(CommandSession.HELP_TEXT, session.handle("Help").text());
        assertEquals(CommandSession.HINT_TEXT, session.handle("what should I do now?").text());
        assertTrue(game.sent.isEmpty());
    }

    @Test
    public void pronounFallsBackToLastNoun() {
        session.handle("open the box");
        CommandSession.Result result = session.handle("read it");
        assertEquals("read the leaflet", game.sent.get(game.sent.size() - 1));
        assertTrue(result.text().contains("WELCOME!"));
        assertFalse(result.text().contains("referring to"));
    }

    @Test
    public void questionsGetAnExplanation() {
        CommandSession.Result result = session.handle("why is the sky blue?");
        assertTrue(result.text().contains(CommandSession.QUESTION_NOTE));
    }

    @Test
    public void useXOnYOffersRealVerbs() {
        CommandSession.Result result = session.handle("use the key on the door");
        assertEquals(Arrays.asList("turn the door with the key", "open the door with the key",
                "unlock the door with the key"),
                result.suggestions);
        session.handle("three");
        assertEquals("unlock the door with the key", game.sent.get(game.sent.size() - 1));
    }

    @Test
    public void numberPicksPendingSuggestionAndSomethingElseAsksAgain() {
        session.handle("take the lamq");
        assertTrue(session.hasPendingSuggestions());
        assertEquals(CommandSession.ASK_AGAIN, session.handle("something else").text());
        session.handle("take the lamq");
        session.handle("one");
        assertEquals("take the lamp", game.sent.get(game.sent.size() - 1));
    }

    @Test
    public void newCommandIgnoresPendingSuggestions() {
        session.handle("take the lamq");
        session.handle("go north");
        assertEquals("N", game.sent.get(game.sent.size() - 1));
        assertFalse(session.hasPendingSuggestions());
    }

    @Test
    public void detectsRoomNameHeadings() {
        assertEquals("West of House", CommandSession.roomFromResponse("West of House\nYou are standing.", true));
        assertEquals(null, CommandSession.roomFromResponse("Taken.", true));
    }
}
