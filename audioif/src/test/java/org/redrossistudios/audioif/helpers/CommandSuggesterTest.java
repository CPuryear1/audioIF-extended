package org.redrossistudios.audioif.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class CommandSuggesterTest {

    private static final List<String> DICTIONARY = Arrays.asList(
            "take", "drop", "open", "the", "lamp", "lantern", "lamb", "lump", "mailbox",
            "leaflet", "door", "north", ",", ".");

    @Test
    public void detectsRejectionMessages() {
        assertTrue(CommandSuggester.isRejection("I don't know the word \"lamq\"."));
        assertTrue(CommandSuggester.isRejection("You can't see any such thing.\n>"));
        assertTrue(CommandSuggester.isRejection("That\u2019s not a verb I recognise."));
        assertFalse(CommandSuggester.isRejection("Taken."));
        assertFalse(CommandSuggester.isRejection(null));
    }

    @Test
    public void replacesUnknownWordWithClosestDictionaryWords() {
        List<String> suggestions = CommandSuggester.suggest("take the lamq", DICTIONARY, 3);
        assertEquals(Arrays.asList("take the lamb", "take the lamp", "take the lump"), suggestions);
    }

    @Test
    public void ranksTranspositionsAndSharedPrefixesHigher() {
        List<String> dictionary = Arrays.asList("open", "grate", "giant", "drat", "bottle", "bother");
        assertEquals(Arrays.asList("open grate", "open giant", "open drat"),
                CommandSuggester.suggest("open grait", dictionary, 3));
        assertEquals("open bottle", CommandSuggester.suggest("open bottel", dictionary, 3).get(0));
    }

    @Test
    public void replacesObjectWordsWhenAllWordsAreKnown() {
        List<String> suggestions = CommandSuggester.suggest("take the lamp", DICTIONARY, 3);
        assertEquals(Arrays.asList("take the lamb", "take the lump"), suggestions);
    }

    @Test
    public void truncatesInputToDictionaryEntryLength() {
        List<String> v3Dictionary = Arrays.asList("take", "lanter", "mailbo");
        assertEquals(Collections.singletonList("take mailbo"),
                CommandSuggester.suggest("take mailbax", v3Dictionary, 3));
    }

    @Test
    public void returnsEmptyWhenNothingIsClose() {
        assertTrue(CommandSuggester.suggest("xyzzy", DICTIONARY, 3).isEmpty());
        assertTrue(CommandSuggester.suggest("", DICTIONARY, 3).isEmpty());
    }

    @Test
    public void buildsNumberedPrompt() {
        assertEquals("Did you mean one, take the lamp, two, take the lantern, three, something else?",
                CommandSuggester.buildPrompt(Arrays.asList("take the lamp", "take the lantern")));
    }

    @Test
    public void parsesSpokenChoices() {
        assertEquals(1, CommandSuggester.parseChoice("One"));
        assertEquals(2, CommandSuggester.parseChoice("to"));
        assertEquals(2, CommandSuggester.parseChoice("number 2"));
        assertEquals(3, CommandSuggester.parseChoice("option three."));
        assertEquals(4, CommandSuggester.parseChoice("for"));
        assertEquals(-1, CommandSuggester.parseChoice("take the lamp"));
    }

    @Test
    public void computesEditDistance() {
        assertEquals(0, CommandSuggester.editDistance("lamp", "lamp"));
        assertEquals(1, CommandSuggester.editDistance("lamp", "lamb"));
        assertEquals(3, CommandSuggester.editDistance("kitten", "sitting"));
        assertEquals(1, CommandSuggester.editDistance("bottel", "bottle"));
    }
}
