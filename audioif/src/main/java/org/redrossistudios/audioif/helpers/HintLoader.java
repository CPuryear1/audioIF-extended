package org.redrossistudios.audioif.helpers;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Gives a session walkthrough hints when the app bundles a walkthrough named after the
 * story file (assets/walkthroughs/Zork I.txt for Zork I.z3; see tools/fetch-walkthroughs.ps1).
 * The first load replays the walkthrough in the background; later loads use the cache.
 */
public final class HintLoader {

    private HintLoader() {
    }

    public static void start(Context context, String storyPath, final CommandSession session) {
        final File story = new File(storyPath);
        String fileName = story.getName();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || !fileName.substring(dot + 1).toLowerCase().matches("z[1-8]")) {
            return;
        }
        final String name = fileName.substring(0, dot);
        final String walkthrough;
        try (InputStream in = context.getAssets().open("walkthroughs/" + name + ".txt")) {
            walkthrough = new String(HintCatalog.readAll(in), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            return;
        }
        final File cacheDir = new File(context.getFilesDir(), "hints");
        session.setHintsLoading(true);
        Thread thread = new Thread(null, new Runnable() {
            @Override
            public void run() {
                WalkthroughIndex index = null;
                try (InputStream in = new FileInputStream(story)) {
                    index = HintCatalog.load(name, HintCatalog.readAll(in), walkthrough, cacheDir);
                } catch (IOException ignored) {
                    // No hints, just the generic advice.
                }
                session.setHintAdvisor(index == null ? null : new HintAdvisor(index));
            }
        }, "hints", 16L << 20);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }
}
