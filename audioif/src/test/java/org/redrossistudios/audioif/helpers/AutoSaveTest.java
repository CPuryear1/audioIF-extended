package org.redrossistudios.audioif.helpers;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.zmpp.ExecutionControl;
import org.zmpp.iff.FormChunk;
import org.zmpp.iff.WritableFormChunk;
import org.zmpp.io.IOSystem;
import org.zmpp.vm.MachineFactory;
import org.zmpp.vm.SaveGameDataStore;
import org.zmpp.windowing.BufferedScreenModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.Writer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AutoSaveTest {

    private static final String ZORK1 = "src/main/assets/games/Zork I.z3";
    private static final String ZORK2 = "src/main/assets/games/Zork II.z3";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void resumesGameInNewMachine() throws Exception {
        ZGame first = start(ZORK1);
        first.readIntro();
        first.send("open the mailbox");
        first.send("take the leaflet");
        first.send("north");
        byte[] state = first.saveState();
        assertNotNull(state);

        ZGame second = start(ZORK1);
        second.readIntro();
        assertTrue(second.loadState(state));
        assertEquals("North of House", second.currentRoom());
        String inv = second.send("inventory");
        assertTrue(inv, inv.contains("leaflet"));
        String south = second.send("west");
        assertTrue(south, south.contains("West of House"));
    }

    @Test
    public void rejectsStateFromAnotherGame() throws Exception {
        ZGame zork2 = start(ZORK2);
        zork2.readIntro();
        byte[] state = zork2.saveState();

        ZGame zork1 = start(ZORK1);
        zork1.readIntro();
        assertFalse(zork1.loadState(state));
        assertFalse(zork1.loadState(new byte[] {1, 2, 3}));
        assertFalse(zork1.loadState(null));
        assertTrue(zork1.send("look").contains("West of House"));
    }

    @Test
    public void storesStateAndRecapPerStoryFile() throws Exception {
        File dir = folder.newFolder("autosave");
        AutoSave save = new AutoSave(dir, "/games/Zork I.z3");
        assertFalse(save.exists());
        assertNull(save.loadState());

        save.save(new byte[] {4, 5, 6}, "You are in a field.");
        assertTrue(save.exists());
        assertArrayEquals(new byte[] {4, 5, 6}, new AutoSave(dir, "/games/Zork I.z3").loadState());
        assertEquals("You are in a field.", save.loadRecap());
        assertFalse(new AutoSave(dir, "/games/Zork II.z3").exists());

        save.save(null, "game over");
        assertFalse(save.exists());
        assertEquals("", save.loadRecap());
    }

    private static ZGame start(String path) throws Exception {
        MachineFactory.MachineInitStruct init = new MachineFactory.MachineInitStruct();
        init.storyFile = new FileInputStream(path);
        init.nativeImageFactory = inputStream -> null;
        init.saveGameDataStore = new SaveGameDataStore() {
            public boolean saveFormChunk(WritableFormChunk formchunk) { return false; }
            public FormChunk retrieveFormChunk() { return null; }
        };
        init.ioSystem = new IOSystem() {
            public Reader getInputStreamReader() { return null; }
            public Writer getTranscriptWriter() { return null; }
        };
        BufferedScreenModel screenModel = new BufferedScreenModel();
        init.statusLine = screenModel;
        init.screenModel = screenModel;
        init.keyboardInputStream = new org.zmpp.io.InputStream() {
            public void close() { }
            public String readLine() { return null; }
        };
        ExecutionControl executionControl = new ExecutionControl(init);
        executionControl.resizeScreen(100, 500);
        screenModel.init(executionControl.getMachine(), executionControl.getZsciiEncoding());
        executionControl.run();
        return new ZGame(executionControl, screenModel);
    }
}