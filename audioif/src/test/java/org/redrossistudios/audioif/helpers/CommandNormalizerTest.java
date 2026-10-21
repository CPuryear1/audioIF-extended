package org.redrossistudios.audioif.helpers;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CommandNormalizerTest {

    private static final CommandNormalizer.Dictionary ALL_WORDS = new CommandNormalizer.Dictionary() {
        @Override
        public boolean contains(String word) {
            return true;
        }
    };

    private static final CommandNormalizer.Dictionary NO_WORDS = new CommandNormalizer.Dictionary() {
        @Override
        public boolean contains(String word) {
            return false;
        }
    };

    @Test
    public void mapsDirections() {
        assertEquals("NW", CommandNormalizer.normalize("North West", ALL_WORDS));
        assertEquals("NE", CommandNormalizer.normalize("northeast", ALL_WORDS));
        assertEquals("SE", CommandNormalizer.normalize("go south east", ALL_WORDS));
        assertEquals("SW", CommandNormalizer.normalize("South west.", ALL_WORDS));
        assertEquals("N", CommandNormalizer.normalize("north", ALL_WORDS));
        assertEquals("S", CommandNormalizer.normalize("go south", ALL_WORDS));
        assertEquals("E", CommandNormalizer.normalize("East", ALL_WORDS));
        assertEquals("W", CommandNormalizer.normalize("west", ALL_WORDS));
        assertEquals("U", CommandNormalizer.normalize("up", ALL_WORDS));
        assertEquals("D", CommandNormalizer.normalize("go down", ALL_WORDS));
    }

    @Test
    public void mapsLeadingVerbsAndKeepsObject() {
        assertEquals("x brass lamp", CommandNormalizer.normalize("Examine brass lamp", ALL_WORDS));
        assertEquals("x mailbox", CommandNormalizer.normalize("look at mailbox", ALL_WORDS));
        assertEquals("i", CommandNormalizer.normalize("inventory", ALL_WORDS));
    }

    @Test
    public void onlyMatchesWholeLeadingWords() {
        assertEquals("pick up lamp", CommandNormalizer.normalize("pick up lamp", ALL_WORDS));
        assertEquals("northern", CommandNormalizer.normalize("northern", ALL_WORDS));
        assertEquals("open the door", CommandNormalizer.normalize("open the door", ALL_WORDS));
    }

    @Test
    public void fallsBackToExamineWhenGameLacksX() {
        CommandNormalizer.Dictionary zork1 = new CommandNormalizer.Dictionary() {
            @Override
            public boolean contains(String word) {
                return !word.equals("x") && !word.equals("check");
            }
        };
        assertEquals("examine the mailbox", CommandNormalizer.normalize("check the mailbox", zork1));
        assertEquals("examine lamp", CommandNormalizer.normalize("Inspect lamp", zork1));
        assertEquals("x mailbox", CommandNormalizer.normalize("check out mailbox", ALL_WORDS));
    }

    @Test
    public void leavesTextUnchangedWhenGameLacksCanonicalWord() {
        assertEquals("examine lamp", CommandNormalizer.normalize("examine lamp", NO_WORDS));
        assertEquals("North West", CommandNormalizer.normalize("North West", NO_WORDS));
    }

    @Test
    public void putAddsNewMapping() {
        CommandNormalizer.put("grab", "take");
        assertEquals("take lamp", CommandNormalizer.normalize("Grab lamp", ALL_WORDS));
    }
}
