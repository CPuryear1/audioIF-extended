package org.redrossistudios.audioif.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class InfocomCatalogTest {

    private static List<InfocomCatalog.Game> allGames() {
        List<InfocomCatalog.Game> all = new ArrayList<>(InfocomCatalog.getBundledGames());
        all.addAll(InfocomCatalog.getDownloadableGames());
        return all;
    }

    @Test
    public void catalogEntriesAreWellFormed() {
        Set<String> localNames = new HashSet<>();
        for (InfocomCatalog.Game game : allGames()) {
            assertTrue(game.title, game.md5.matches("[0-9a-f]{32}"));
            assertTrue(game.title, game.getUrl().startsWith("https://"));
            assertTrue(game.title, game.getUrl().endsWith("/" + game.sourceFileName));
            assertTrue(game.title, game.getExtension().matches("z[1-58]"));
            assertTrue(game.title, game.getLocalFileName().matches("(?i).+\\.(z[1-8]|zblorb|zlb)$"));
            assertTrue("duplicate " + game.title, localNames.add(game.getLocalFileName()));
        }
        assertEquals(3, InfocomCatalog.getBundledGames().size());
        assertEquals(28, InfocomCatalog.getDownloadableGames().size());
    }

    @Test
    public void localFileNamesUseTitles() {
        assertEquals("Zork I.z3", InfocomCatalog.getBundledGames().get(0).getLocalFileName());
        for (InfocomCatalog.Game game : InfocomCatalog.getDownloadableGames()) {
            if (game.title.startsWith("Nord")) {
                assertEquals("Nord and Bert Couldn't Make Head or Tail of It.z4", game.getLocalFileName());
            }
            if (game.title.startsWith("Colossal")) {
                assertEquals("https://ifarchive.org/if-archive/games/zcode/Advent.z5", game.getUrl());
            }
        }
    }

    @Test
    public void md5HexMatchesKnownDigest() throws Exception {
        InputStream in = new ByteArrayInputStream("abc".getBytes(StandardCharsets.US_ASCII));
        assertEquals("900150983cd24fb0d6963f7d28e17f72", InfocomCatalog.md5Hex(in));
    }

    @Test
    public void bundledAssetsMatchCatalogChecksums() throws Exception {
        for (InfocomCatalog.Game game : InfocomCatalog.getBundledGames()) {
            File asset = new File("src/main/assets/games", game.getLocalFileName());
            assertTrue(asset.getAbsolutePath(), asset.isFile());
            try (InputStream in = new FileInputStream(asset)) {
                assertEquals(game.title, game.md5, InfocomCatalog.md5Hex(in));
            }
        }
        assertTrue(new File("src/main/assets/games/LICENSE-zork.txt").isFile());
        assertFalse(new File("src/main/assets/games/Advent.z5").exists());
    }
}
