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
        assertEquals("north west", CommandNormalizer.normalize("North West", NO_WORDS));
    }

    @Test
    public void terminalMappingsDropTheRemainder() {
        assertEquals("U", CommandNormalizer.normalize("go up the stairs", ALL_WORDS));
        assertEquals("W", CommandNormalizer.normalize("go west please", ALL_WORDS));
        assertEquals("z", CommandNormalizer.normalize("wait a few turns", ALL_WORDS));
        assertEquals("look", CommandNormalizer.normalize("look around the room", ALL_WORDS));
        assertEquals("i", CommandNormalizer.normalize("check my inventory", ALL_WORDS));
        assertEquals("i", CommandNormalizer.normalize("What am I carrying?", ALL_WORDS));
    }

    @Test
    public void stripsFillersAndContractions() {
        assertEquals("open the mailbox", CommandNormalizer.normalize("Can you please open the mailbox?", ALL_WORDS));
        assertEquals("take the leaflet", CommandNormalizer.normalize("I'd like to take the leaflet please", ALL_WORDS));
        assertEquals("look in the mailbox", CommandNormalizer.normalize("What's in the mailbox?", ALL_WORDS));
        assertEquals("move the rug", CommandNormalizer.normalize("let's move the rug out of the way", ALL_WORDS));
        assertEquals("look", CommandNormalizer.normalize("Where am I?", ALL_WORDS));
    }

    @Test
    public void replacesWordsOldParsersLack() {
        assertEquals("take all", CommandNormalizer.normalize("take everything", ALL_WORDS));
        assertEquals("drop the sword", CommandNormalizer.normalize("drop my sword", ALL_WORDS));
        assertEquals("D", CommandNormalizer.normalize("go back down", ALL_WORDS));
        assertEquals("U", CommandNormalizer.normalize("go upstairs", ALL_WORDS));
        assertEquals("turn on the lamp", CommandNormalizer.normalize("switch on the lamp", ALL_WORDS));
        assertEquals("drop the garlic", CommandNormalizer.normalize("get rid of the garlic", ALL_WORDS));
        assertEquals("take the egg and the leaflet",
                CommandNormalizer.normalize("take the egg and a leaflet", ALL_WORDS));
    }

    @Test
    public void motionSentenceEndingInDirectionBecomesDirection() {
        assertEquals("N", CommandNormalizer.normalize("follow the path north", ALL_WORDS));
        assertEquals("SW", CommandNormalizer.normalize("walk through the forest south west", ALL_WORDS));
        assertEquals("move the rug north", CommandNormalizer.normalize("move the rug north", ALL_WORDS));
    }

    @Test
    public void fillerOnlyInputIsKept() {
        assertEquals("okay", CommandNormalizer.normalize("okay", ALL_WORDS));
        assertEquals("g", CommandNormalizer.normalize("again", ALL_WORDS));
    }

    @Test
    public void putAddsNewMapping() {
        CommandNormalizer.put("grab", "take");
        assertEquals("take lamp", CommandNormalizer.normalize("Grab lamp", ALL_WORDS));
    }
}
