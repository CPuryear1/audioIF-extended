package org.redrossistudios.audioif.helpers;

import java.util.List;

/**
 * Turns what the player said or typed into either a command for the game or a
 * reply for the player. Shared by audio and text mode so both behave the same.
 */
public final class CommandInput {

    public static final String ASK_AGAIN = "OK, what would you like to do?";

    /** Exactly one of {@link #command} and {@link #reply} is non-null. */
    public static final class Result {
        public final String command;
        public final String reply;

        private Result(String command, String reply) {
            this.command = command;
            this.reply = reply;
        }
    }

    private CommandInput() { }

    /**
     * @param input        recognized speech or typed text
     * @param pending      suggestions offered after the previous command, or null
     * @param dictionary   the loaded game's dictionary (may be null)
     */
    public static Result resolve(String input, List<String> pending, CommandNormalizer.Dictionary dictionary) {
        if (pending != null) {
            int choice = CommandSuggester.parseChoice(input);
            if (choice >= 1 && choice <= pending.size()) {
                return new Result(pending.get(choice - 1), null);
            }
            if (choice == pending.size() + 1 || input.trim().equalsIgnoreCase("something else")) {
                return new Result(null, ASK_AGAIN);
            }
        }
        return new Result(CommandNormalizer.normalize(input, dictionary), null);
    }
}
