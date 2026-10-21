package org.redrossistudios.audioif.helpers;

import org.zmpp.ExecutionControl;
import org.zmpp.vm.PortableGameState;
import org.zmpp.windowing.AnnotatedText;
import org.zmpp.windowing.BufferedScreenModel;

import java.util.Collections;
import java.util.List;

/**
 * Adapts a running ZMPP machine to {@link CommandSession.Game}. Create it after
 * {@code executionControl.run()} has reached the first input prompt.
 */
public final class ZGame implements CommandSession.Game {

    private final ExecutionControl executionControl;
    private final BufferedScreenModel screenModel;
    private volatile String room;
    private List<String> dictionaryWords;

    public ZGame(ExecutionControl executionControl, BufferedScreenModel screenModel) {
        this.executionControl = executionControl;
        this.screenModel = screenModel;
        screenModel.addStatusLineListener((objectDescription, status) -> {
            if (objectDescription != null && !objectDescription.trim().isEmpty()) {
                room = objectDescription.trim();
            }
        });
    }

    /** Text printed before the first prompt; sends a blank line if the game is waiting for a key. */
    public String readIntro() {
        String intro = readOutput();
        return intro.length() > 1 ? intro : send(" ");
    }

    /** Sends a line; an empty response means the game wants more input (e.g. "press a key"). */
    @Override
    public String send(String command) {
        String text = "";
        for (int i = 0; i < 5 && text.length() <= 1; i++) {
            executionControl.resumeWithInput(i == 0 ? command : " ");
            text = readOutput();
        }
        return text;
    }

    @Override
    public boolean contains(String word) {
        try {
            return executionControl.getMachine().lookupToken(0, word) != 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public List<String> dictionaryWords() {
        if (dictionaryWords == null) {
            try {
                dictionaryWords = executionControl.getMachine().getDictionaryWords();
            } catch (RuntimeException e) {
                dictionaryWords = Collections.emptyList();
            }
        }
        return dictionaryWords;
    }

    @Override
    public String currentRoom() {
        return room;
    }

    @Override
    public Object snapshot() {
        try {
            return executionControl.getMachine().captureInputState();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public boolean restore(Object snapshot) {
        if (!(snapshot instanceof PortableGameState)) {
            return false;
        }
        try {
            executionControl.getMachine().restoreInputState((PortableGameState) snapshot);
            executionControl.run();
            readOutput();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String readOutput() {
        StringBuilder result = new StringBuilder();
        for (AnnotatedText text : screenModel.getLowerBuffer()) {
            result.append(text.getText());
        }
        String s = result.toString();
        // Drop the trailing ">" prompt.
        return s.length() > 2 ? s.substring(0, s.length() - 2).trim() : s;
    }
}
