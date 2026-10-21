package org.redrossistudios.audioif.helpers;

import org.zmpp.ExecutionControl;
import org.zmpp.iff.FormChunk;
import org.zmpp.iff.WritableFormChunk;
import org.zmpp.io.IOSystem;
import org.zmpp.vm.MachineFactory;
import org.zmpp.vm.SaveGameDataStore;
import org.zmpp.windowing.BufferedScreenModel;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.io.Writer;

/** A game with no screen or storage, for replaying walkthroughs and tests. */
public final class HeadlessGame {

    public final ZGame game;
    public final String intro;

    private HeadlessGame(ZGame game, String intro) {
        this.game = game;
        this.intro = intro;
    }

    /** Starts the story; a non-zero seed makes the game's random numbers repeatable. */
    public static HeadlessGame start(byte[] story, int seed) throws Exception {
        MachineFactory.MachineInitStruct init = new MachineFactory.MachineInitStruct();
        init.storyFile = new ByteArrayInputStream(story);
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
        if (seed != 0) {
            executionControl.getMachine().random((short) -Math.abs(seed));
        }
        ZGame game = new ZGame(executionControl, screenModel);
        return new HeadlessGame(game, game.readIntro());
    }

    /** Adapts a fresh headless game to {@link WalkthroughIndex.Player}. */
    public static WalkthroughIndex.PlayerFactory players(byte[] story) {
        return seed -> {
            ZGame game = start(story, seed).game;
            if (game.currentRoom() == null) {
                // The status line is only drawn after the first turn; the start room is needed for routes.
                game.send("look");
            }
            return new WalkthroughIndex.Player() {
                public String send(String command) { return game.send(command); }
                public String room() { return HintAdvisor.roomId(game.currentRoom(), game.stateVector()); }
                public int[] state() { return game.stateVector(); }
                public Object snapshot() { return game.snapshot(); }
                public void restore(Object snapshot) { game.restore(snapshot); }
            };
        };
    }
}
