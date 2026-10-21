/*
 * audioIF-extended additions to ZMPP 1.5.
 *
 * Everything the app needs beyond upstream ZMPP lives in this file so the
 * upstream sources stay unchanged and upstream fixes can be copied in
 * directly. The only edit to an upstream file is the visibility of
 * MachineImpl.restart(boolean) (private -> package-private), marked there
 * with an "audioIF-extended" comment. See zmpp/UPSTREAM.md.
 *
 * Licensed under the same BSD terms as ZMPP.
 */
package org.zmpp.vm;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers used by the audioIF app: reading the story dictionary (for command
 * suggestions) and capturing/restoring the full game state at a line-input
 * prompt (for interpreter-level undo and automatic resume).
 */
public final class AudioIfExtensions {

  /** Header word holding the address of the main dictionary. */
  private static final int HEADER_DICTIONARY = 0x08;

  private AudioIfExtensions() { }

  /**
   * Returns all words in the story's main dictionary, decoded to strings.
   * Words longer than the story's entry size (6 letters in v1-3, 9 in v4+)
   * come back truncated, as they are stored.
   * @param machine the machine
   * @return the dictionary words
   */
  public static List<String> getDictionaryWords(final Machine machine) {
    // Layout (Z-Machine Standard 13.2): separator count, separators, entry
    // length, entry count (signed; negative means unsorted), entries.
    int address = machine.readUnsigned16(HEADER_DICTIONARY);
    final int numSeparators = machine.readUnsigned8(address);
    address += 1 + numSeparators;
    final int entryLength = machine.readUnsigned8(address);
    final int numEntries = Math.abs((short) machine.readUnsigned16(address + 1));
    address += 3;
    final int textBytes = machine.getVersion() <= 3 ? 4 : 6;
    final List<String> words = new ArrayList<String>(numEntries);
    for (int i = 0; i < numEntries; i++) {
      words.add(machine.decode2Zscii(address + i * entryLength, textBytes).trim());
    }
    return words;
  }

  /**
   * Captures the complete game state while the machine is waiting for line
   * input. Unlike Machine.save(), the stored PC is the read instruction itself.
   * @param machine the machine
   * @return the captured state
   */
  public static PortableGameState captureInputState(final Machine machine) {
    final PortableGameState state = new PortableGameState();
    state.captureMachineState(machine, machine.getPC());
    return state;
  }

  /**
   * Restores a state from {@link #captureInputState(Machine)}. Call
   * ExecutionControl.run() afterwards so the machine waits for input again.
   * @param machine the machine (must be a MachineImpl)
   * @param state the state to restore
   */
  public static void restoreInputState(final Machine machine,
      final PortableGameState state) {
    // Same steps as MachineImpl.restore(): reset without clearing the screen.
    ((MachineImpl) machine).restart(false);
    state.transferStateToMachine(machine);
    // transferStateToMachine() skips past a save instruction's store/branch
    // byte; here the read instruction itself must run again.
    machine.setPC(state.getProgramCounter());
    machine.setRunState(MachineRunState.RUNNING);
  }
}
