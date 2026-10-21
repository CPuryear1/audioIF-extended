# ZMPP in audioIF-extended

This module is the Java Z-machine interpreter [ZMPP](https://sourceforge.net/projects/zmpp/) 1.5 by Wei-ju Wu (BSD license, see the file headers).

## Upstream base

- Repository: `https://git.code.sf.net/p/zmpp/zmpp` (branch `master`)
- Base commit: `60b8d37` "more fixes for storeb/storew/loadb/loadw" (2011-11-11). This is the latest upstream commit; upstream has had no changes since.
- Apart from the local changes below, the files in `src/main/java/org/zmpp` match that commit. Two files differ only cosmetically: `CpuImpl` and `InstructionDecoder` use short names where upstream writes `Cpu.VariableType` and `java.lang.…` in full.
- The successor project, [zmpp2](https://github.com/weiju/zmpp2), is a Scala rewrite (last updated in 2017) with a different API. It isn't a drop-in update.

## Local changes

Keep app-specific code out of upstream files so upstream fixes can be copied in unchanged.

| File | Change |
|---|---|
| `src/main/java/org/zmpp/vm/AudioIfExtensions.java` | **New.** All app additions: `getDictionaryWords` (for command suggestions), and `captureInputState` / `restoreInputState` (for undo and automatic resume). It uses only ZMPP's public API, plus the hook below. |
| `src/main/java/org/zmpp/vm/MachineImpl.java` | `restart(boolean)` changed from `private` to package-private so `restoreInputState` can reset the machine without clearing the screen, as `restore()` does. Marked with an `audioIF-extended` comment. |
| `build.gradle`, `src/main/AndroidManifest.xml` | Android build setup (namespace, SDK levels, AndroidX). Not part of upstream ZMPP. |

To list every marked edit: `git grep -n "audioIF-extended" -- zmpp`.

## Pulling in an upstream fix

1. Clone upstream: `git clone https://git.code.sf.net/p/zmpp/zmpp`.
2. Diff `src/main/java/org/zmpp` against this module, ignoring whitespace and line endings: `git diff --no-index -w --ignore-cr-at-eol <upstream>/src/main/java/org/zmpp zmpp/src/main/java/org/zmpp`. Expect to see only the changes listed above.
3. Copy the changed upstream files in. Then redo the `MachineImpl.restart` visibility change if that file was replaced.
4. Run `gradlew assembleDebug :audioif:testDebugUnitTest`. The play harness tests exercise the extensions.
