package org.redrossistudios.audioif.helpers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Loads the walkthrough index for a game: from the cache if the story file and
 * walkthrough are unchanged, otherwise by replaying the walkthrough (a few seconds)
 * and caching the result. Call off the main thread.
 */
public final class HintCatalog {

    /** Random seeds to try when replaying. */
    public static final int ATTEMPTS = 5;

    private HintCatalog() {
    }

    /**
     * Returns null if there is no usable walkthrough. {@code cacheDir} may be null to skip caching.
     */
    public static WalkthroughIndex load(String name, byte[] story, String walkthrough, File cacheDir) {
        if (story == null || walkthrough == null) {
            return null;
        }
        CRC32 crc = new CRC32();
        crc.update(story);
        crc.update(walkthrough.getBytes(StandardCharsets.UTF_8));
        crc.update(WalkthroughIndex.FORMAT);
        File cache = cacheDir == null ? null : new File(cacheDir,
                name.replaceAll("[^A-Za-z0-9]+", "_") + "-" + Long.toHexString(crc.getValue()) + ".idx");
        try {
            if (cache != null && cache.exists()) {
                WalkthroughIndex cached = WalkthroughIndex.deserialize(
                        new String(readFile(cache), StandardCharsets.UTF_8));
                if (cached != null) {
                    return cached;
                }
            }
            List<WalkthroughParser.Step> steps = WalkthroughParser.parse(walkthrough);
            if (steps.isEmpty()) {
                return null;
            }
            WalkthroughIndex index = WalkthroughIndex.build(steps, HeadlessGame.players(story), ATTEMPTS);
            if (index.goalCount() == 0) {
                return null;
            }
            if (cache != null) {
                cacheDir.mkdirs();
                try (OutputStream out = new FileOutputStream(cache)) {
                    out.write(index.serialize().getBytes(StandardCharsets.UTF_8));
                }
            }
            return index;
        } catch (Exception | StackOverflowError e) {
            return null;
        }
    }

    private static byte[] readFile(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readAll(in);
        }
    }

    public static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) > 0; ) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
