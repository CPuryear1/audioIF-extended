package org.redrossistudios.audioif.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class WalkthroughParserTest {

    @Test
    public void parsesSlashSeparatedCommandsAfterTheTitle() {
        List<WalkthroughParser.Step> steps = WalkthroughParser.parse(
                "Made-up Adventure\r\n\r\nN/Open  Gate/E\r\nTake Egg/Wait(till the bird sings)/D\n");
        assertEquals("[n, open gate, e, take egg, wait (till the bird sings), d]", steps.toString());
        assertNull(steps.get(0).note);
        assertTrue(steps.get(4).repeats());
    }

    @Test
    public void unclosedNoteEndsAtTheLine() {
        List<WalkthroughParser.Step> steps = WalkthroughParser.parse("Title\nN/Wait(until dawn\nHit bell(keep going)/S");
        assertEquals("[n, wait (until dawn), hit bell (keep going), s]", steps.toString());
        assertTrue(steps.get(2).repeats());
    }

    @Test
    public void notesWithoutTillDontRepeat() {
        List<WalkthroughParser.Step> steps = WalkthroughParser.parse("x/Look under seat(important)");
        assertEquals("look under seat", steps.get(1).command);
        assertFalse(steps.get(1).repeats());
    }

    @Test
    public void emptyInput() {
        assertTrue(WalkthroughParser.parse(null).isEmpty());
        assertTrue(WalkthroughParser.parse("Just a title").isEmpty());
    }
}
