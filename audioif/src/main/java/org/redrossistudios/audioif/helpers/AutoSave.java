package org.redrossistudios.audioif.helpers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Keeps one automatic save per story file so a game resumes where the player
 * left off. Stores the Quetzal state plus a short recap of the last text shown.
 */
public final class AutoSave {

    public static final String RESUMED_NOTE =
            "Welcome back. Your game picks up where you left off. To start over, say restart.";

    private static final int MAX_RECAP = 4000;

    private final File stateFile;
    private final File recapFile;
    private final File skipsFile;

    public AutoSave(File directory, String storyPath) {
        String name = new File(storyPath).getName().replaceAll("[^A-Za-z0-9]+", "_");
        String key = name + "-" + Integer.toHexString(storyPath.hashCode());
        directory.mkdirs();
        stateFile = new File(directory, key + ".qzl");
        recapFile = new File(directory, key + ".txt");
        skipsFile = new File(directory, key + ".skips");
    }

    public boolean exists() {
        return stateFile.isFile();
    }

    /** Saves the state; a null state (e.g. the game has ended) clears the save instead. */
    public void save(byte[] state, String recap) {
        if (state == null) {
            clear();
            return;
        }
        try {
            write(stateFile, state);
            String text = recap == null ? "" : recap.trim();
            if (text.length() > MAX_RECAP) {
                text = text.substring(text.length() - MAX_RECAP);
            }
            write(recapFile, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public byte[] loadState() {
        try {
            return exists() ? Files.readAllBytes(stateFile.toPath()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public String loadRecap() {
        try {
            return recapFile.isFile() ? new String(Files.readAllBytes(recapFile.toPath()), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    public void clear() {
        stateFile.delete();
        recapFile.delete();
        skipsFile.delete();
    }

    /** Keeps the hint steps the player has done another way (see {@link CommandSession#hintSkips()}). */
    public void saveHintSkips(String skips) {
        try {
            write(skipsFile, (skips == null ? "" : skips).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String loadHintSkips() {
        try {
            return skipsFile.isFile() ? new String(Files.readAllBytes(skipsFile.toPath()), StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    // Writes to a temporary file first so an interrupted save never leaves a corrupt file.
    private static void write(File target, byte[] data) throws IOException {
        File temp = new File(target.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(data);
            out.getFD().sync();
        }
        if (!temp.renameTo(target)) {
            target.delete();
            if (!temp.renameTo(target)) {
                throw new IOException("Could not write " + target);
            }
        }
    }
}