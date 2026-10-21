package org.redrossistudios.audioif.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CommandInputTest {

    private static final CommandNormalizer.Dictionary ZORK1 = new CommandNormalizer.Dictionary() {
        @Override
        public boolean contains(String word) {
            return !word.equals("x") && !word.equals("check");
        }
    };

    private static final List<String> PENDING = Arrays.asList("take lamp", "take lantern");

    @Test
    public void normalizesWhenNoSuggestionsPending() {
        CommandInput.Result r = CommandInput.resolve("check the mailbox", null, ZORK1);
        assertEquals("examine the mailbox", r.command);
        assertNull(r.reply);
    }

    @Test
    public void numberPicksPendingSuggestion() {
        assertEquals("take lantern", CommandInput.resolve("2", PENDING, ZORK1).command);
        assertEquals("take lamp", CommandInput.resolve("one", PENDING, ZORK1).command);
    }

    @Test
    public void somethingElseAsksAgain() {
        assertEquals(CommandInput.ASK_AGAIN, CommandInput.resolve("3", PENDING, ZORK1).reply);
        assertEquals(CommandInput.ASK_AGAIN, CommandInput.resolve("Something else", PENDING, ZORK1).reply);
    }

    @Test
    public void newCommandIgnoresPendingSuggestions() {
        assertEquals("N", CommandInput.resolve("go north", PENDING, ZORK1).command);
    }
}
