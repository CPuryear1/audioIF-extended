package org.redrossistudios.audioif.helpers;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Replays the fetched Zork walkthroughs (tools/fetch-walkthroughs.ps1) on hidden games.
 * Skipped when they haven't been fetched. Writes a step-by-step report to
 * build/play/walkthrough-*.txt for diagnosing desyncs.
 */
public class WalkthroughReplayTest {

    private static final String[] GAMES = {"Zork I", "Zork II", "Zork III"};

    @Test
    public void replaysEachWalkthroughFarEnough() throws Exception {
        boolean any = false;
        for (String name : GAMES) {
            File walkthrough = new File("src/main/assets/walkthroughs/" + name + ".txt");
            if (!walkthrough.exists()) {
                continue;
            }
            any = true;
            byte[] story = Files.readAllBytes(new File("src/main/assets/games/" + name + ".z3").toPath());
            List<WalkthroughParser.Step> steps = WalkthroughParser.parse(
                    new String(Files.readAllBytes(walkthrough.toPath()), StandardCharsets.ISO_8859_1));

            WalkthroughIndex.Run best = null;
            int bestSeed = 0;
            StringBuilder seeds = new StringBuilder();
            for (int seed = 1; seed <= HintCatalog.ATTEMPTS; seed++) {
                WalkthroughIndex.Run run = WalkthroughIndex.replay(steps, HeadlessGame.players(story).create(seed));
                seeds.append("seed ").append(seed).append(": ").append(run.changed.size()).append('/')
                        .append(steps.size()).append(run.died ? " died" : "").append(" score ")
                        .append(run.score()).append('\n');
                if (best == null || run.score() > best.score()) {
                    best = run;
                    bestSeed = seed;
                }
            }
            int fieldCount = HeadlessGame.start(story, 1).game.stateVector().length;
            WalkthroughIndex index = WalkthroughIndex.fromRun(best, fieldCount);

            StringBuilder report = new StringBuilder(seeds).append("best seed ").append(bestSeed)
                    .append(", goals ").append(index.goalCount()).append("\n\n");
            for (int i = 0; i < index.steps.size(); i++) {
                WalkthroughIndex.Step step = index.steps.get(i);
                String reply = best.replies.get(i).replace('\n', ' ');
                report.append(i).append(step.isGoal() ? " *" : "  ").append(" [").append(step.room).append("] ")
                        .append(step.command)
                        .append(step.toRoom != null ? " -> " + step.toRoom : "")
                        .append("  {").append(step.fields.length).append("}  ")
                        .append(reply, 0, Math.min(140, reply.length())).append('\n');
            }
            report.append("\nretries:\n");
            for (String retry : best.retries) {
                report.append(retry, 0, Math.min(200, retry.length())).append('\n');
            }
            File out = new File("build/play");
            out.mkdirs();
            Files.write(new File(out, "walkthrough-" + name.replace(' ', '_') + ".txt").toPath(),
                    report.toString().getBytes(StandardCharsets.UTF_8));
            assertTrue(name + " replay too short", best.changed.size() > steps.size() / 3);
            assertTrue(name + " has too few goals", index.goalCount() > 10);
        }
        Assume.assumeTrue("run tools/fetch-walkthroughs.ps1 first", any);
    }
}
