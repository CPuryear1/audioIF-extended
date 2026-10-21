package org.redrossistudios.audioif.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PlayLogTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private long time = 1_700_000_000_000L;

    private PlayLog newLog(File folder) {
        time += 1000;
        final long start = time;
        return new PlayLog(folder, "Zork I.z3", () -> start);
    }

    private static List<String> lines(PlayLog log) throws Exception {
        return Arrays.asList(PlayLog.read(log.file()).split("\n"));
    }

    @Test
    public void recordsSpeechAndWhatWasSent() throws Exception {
        PlayLog log = newLog(temp.getRoot());
        log.said("West of House\nThere is a small mailbox here.");
        log.heard(Arrays.asList("open the mail box", "open the mailbox"), new float[] {0.82f, 0.4f});
        CommandSession.Result result = new CommandSession.Result();
        result.heard = "open the mailbox";
        result.exchanges.add(new CommandSession.Exchange("open the mailbox", "Opening the mailbox...", null));
        log.result(result);
        log.event("speech error 7 (no match)");

        List<String> lines = lines(log);
        assertTrue(lines.get(0).endsWith("START audio mode, Zork I.z3"));
        assertTrue(lines.get(1).endsWith("SAY   West of House"));
        assertTrue(lines.get(2).matches(" +There is a small mailbox here\\."));
        assertTrue(lines.get(3).endsWith("HEARD 1. open the mail box (0.82) | 2. open the mailbox (0.40)"));
        assertTrue(lines.get(4).endsWith("USED  open the mailbox"));
        assertTrue(lines.get(5).endsWith("SENT  open the mailbox"));
        assertTrue(lines.get(6).endsWith("EVENT speech error 7 (no match)"));
    }

    @Test
    public void handlesMissingConfidences() throws Exception {
        PlayLog log = newLog(temp.getRoot());
        log.heard(Collections.singletonList("go north"), null);
        assertTrue(lines(log).get(1).endsWith("HEARD 1. go north"));
    }

    @Test
    public void keepsOnlyTheNewestLogs() throws Exception {
        PlayLog last = null;
        for (int i = 0; i < PlayLog.KEEP + 5; i++) {
            last = newLog(temp.getRoot());
        }
        assertEquals(PlayLog.KEEP, temp.getRoot().listFiles().length);
        assertEquals(last.file(), PlayLog.latest(temp.getRoot()));
    }
}
