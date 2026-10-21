# audioIF-extended
An extended fork of [drossimarinho/audioIF](https://github.com/drossimarinho/audioIF).

audio IF - Audio Interactive Fiction - Play story games in a dynamic listen-talk gameplay - Android

Google Play link: https://play.google.com/store/apps/details?id=org.redrossistudios.audioif

Interactive fiction, Text adveture games, Story games, Interactive fiction interpreter, Z-Machine Interpreter, Z-Code games.

Play your favorite interactive fiction games in a listen-talk way.
At each step the text adventure is read for you, and then waits for your voice command to play the game.

2 modes:

-Text mode: regular interactive fiction gameplay with reading and writing<br />
-Audio mode: dynamic listen-talk gameplay

I tried to make the app easy to use for blind people. 
The "start audio mode" button is really big in the middle of the screen, so after you open the app, if you press somewhere in the middle of your screen the audio mode should start.
On the audio mode, the app scans for the supported game files on your device, and speaks out loud the name of each game file with a number identifier. After the enumeration of the games, there will be a beep, then you should say the game number, it will load that game.
When it's time for the player to speak, the app makes a beep sound. You can interrupt the story speech by clicking anywhere in the middle of screen, click anywhere on the screen also to start the speech recognition in case it is stopped.
Speech recognition runs inline, without a popup: the app starts listening as soon as it finishes reading the story text. If your speech isn't understood, the app says "Sorry, I didn't catch that. Please try again." and listens again. Spoken directions and common verbs are converted to short commands: "north west" or "go north west" becomes NW, "up" becomes U, "examine lamp" or "check lamp" becomes "x lamp", and so on. A conversion is only used if the game recognizes the short form; games without "x" (such as Zork I) get "examine" instead. If the game doesn't understand a command (for example "You can't see any such thing." or "That's not a verb I recognise."), the app offers up to three similar commands from the game's vocabulary, for example "Did you mean one, take the lamp, two, take the lake, three, something else?" Say the number to run that command, say "something else" to be asked again, or just say a new command. Long-press the screen to show the keyboard and type a command instead; this pauses voice input.

The app asks for microphone permission the first time audio mode starts. Voice commands don't work without it.

If there is any problem or recommendation please let me know.

Text to speech configuration: Tap "Reading voice" on the menu to choose any installed voice for your language (tap a voice to hear it) and set the speed and pitch. Audio mode uses these settings. "Reset to default" goes back to your phone's text-to-speech defaults (Android Settings → Accessibility → Text-to-speech output).

Included and downloadable games:
Zork I, Zork II and Zork III come with the app, so you can start playing right away. They appear in the game list in both audio and text mode.
Tap "Download Infocom games" on the menu to download more games: the other text-only Infocom classics (Planetfall, Enchanter, Trinity, A Mind Forever Voyaging and more) and Colossal Cave Adventure. Tap a game to download it; it then appears in the game list.

How to easily find games:
If you click on the top button of the app "Browse supported games", it will bring you to the website https://ifarchive.org/indexes/if-archiveXgamesXzcode.html where the game files are directly available for download. Once the game file is downloaded, you just need to go back to the app and start audio or text mode, and the file scanner is going to find your downloaded games.

Game lists:
https://ifarchive.org/indexes/if-archiveXgamesXzcode.html

https://ifdb.tads.org/search?sortby=rcu&newSortBy.x=0&newSortBy.y=0&searchfor=-format%3AZ-Machine


How to play quick guide:
http://www.microheaven.com/ifguide/step3.html


Supported formats: Z-Machine games<br />
-.z[1-8] <br />
-.zblorb<br />
-.zlb


The core of this application uses the ZMPP interpreter project: https://sourceforge.net/projects/zmpp/

## Changes in this fork
- Audio mode uses an inline `SpeechRecognizer` instead of the Google speech popup. Listening starts when text-to-speech finishes (via `UtteranceProgressListener`), and recognition errors trigger a spoken retry prompt.
- Spoken commands are normalized by `CommandNormalizer` (`audioif/src/main/java/org/redrossistudios/audioif/helpers/CommandNormalizer.java`). It lowercases the input, strips punctuation, expands contractions ("what's" → "what is"), and drops polite or filler words at either end ("please", "can you", "I would like to", "now", "again"). It then simplifies words: "my/this/that" → "the", "everything" → "all", "upstairs" → "up". Directions are mapped, including motion phrases such as "walk to the north" or "head back east" → `E`. Verbs are mapped as well: examine / look at / check / inspect → `x` (or `examine` if the game has no `x`), what's in / look inside → `look in`, pick up → `take`, put down / get rid of → `drop`, switch → `turn`, plus inventory, look around, score, save and restore. Newer mappings come from Zork II playtests: "try to…", "I think I'll…" and gerunds ("going north") are accepted; "take a look at", "peek into", "glance under" and "what is on/under/behind" become `x`/`look in`/`look under`/`look behind`; "explore", "is there…" and "examine the room" become `look`; "walk north across the field" and "take the southwest path" become the direction; "stick/slide/insert the X in Y" become `put`; "pull the mat out from under the door" becomes `pull the mat`. Whole-input phrases such as "get out of here" (→ `exit`/`out`), "go inside" (→ `in`) and "climb up" (→ `U`, because bare `climb up` makes Zork II print a garbled object name) are added with `putExact`. Phrases match whole words at the start of the input, and the longest match wins. "Terminal" phrases such as "what am I carrying" ignore the rest of the sentence. To add a mapping, call `put("spoken phrase", "command")` (or `putTerminal`) in its static block. Separate fallbacks with `|` (for example `"x|examine"`); the first one whose verb the game knows is used.
- Natural-language session (`CommandSession`, `audioif/src/main/java/org/redrossistudios/audioif/helpers/CommandSession.java`). Audio mode, text mode and the play harness all send input through it. `ZGame` connects it to ZMPP. It adds features the old games lack:
  - Compound commands: "open the mailbox and take the leaflet", "go north, then east". Processing stops at the first rejected step.
  - Repeats: "go south twice", "wait 3 times" (up to 5).
  - Room memory: rooms come from the status line (or the room heading). Any command that changes rooms ("go inside", "climb the stairs", "exit") is recorded as a way between them. "Go back" retraces the last move, "keep going" repeats it, and "go to the kitchen" walks the shortest known route, stopping if something unexpected happens. "Leave" or "get out of here" uses the game's exit or out.
  - Landmarks: if the game rejects "follow the trail" or "go to the stairs", the room description is searched for a sentence that mentions the thing and gives a direction ("A narrow trail leads north" → north; "above" → up). If the current room doesn't mention it, the app walks to the nearest known room whose description did. In brief mode the app fetches the full description first.
  - Questions about exits ("which way can I go?") look around and list the ways you've already used from here.
  - Chatter after a command ("go north, that looks interesting") is ignored.
  - Pronouns: if the game doesn't understand "it"/"them", the command is retried with the last noun you used, e.g. "(I took it to mean the leaflet.)".
  - Help, hint and undo for games that don't have them. Undo uses up to 10 snapshots captured by `AudioIfExtensions.captureInputState` / `restoreInputState` (see below), and says what was taken back ("Took back: open the door. You are in Kitchen."). Look, inventory and score don't use up an undo step.
  - Walkthrough hints for Zork I, II and III (see "Walkthrough hints" below). Saying "help" or "hint" tells you what to do next, more precisely each time you ask: first where the next step is, then how to get there or which things to use, then the exact command ("At Up a Tree, try: get egg. From here, go north, north, then up."). The hint starts vague again once you've done that step. "Commands", "list commands" or "how do I play" read the general command help.
  - Explanations: questions ("why is…") get a note that the game can't answer questions. "Use X on Y" offers real verbs, chosen by the objects: keys → unlock, open, put; holes and slots → put; lamps → light. "What do you want to turn the bolt with?" gets a note to say the whole command. Other rejections get numbered suggestions. Pick one by saying or typing its number. Suggestions use words from recent replies, and implausible ones (a non-verb replacing the verb, a direction swapped for a different direction) are dropped.
  - Text mode shows what was actually sent in brackets, e.g. `> check the mailbox  [examine the mailbox]`, and shows app notes in italics, so it is a quick way to test voice-command handling.
- Rejected commands get spoken suggestions from `CommandSuggester` (`audioif/src/main/java/org/redrossistudios/audioif/helpers/CommandSuggester.java`). After each command, the game's response is checked against a list of rejection phrases (add more with `CommandSuggester.addRejectionPhrase`). Sentence-level errors such as "There was no verb in that sentence!" get no word suggestions. If it matches, the word the game names (`the word "X"`) or the unknown words in the command (or, if every word is known, the object words) are replaced with dictionary words within a small edit distance (typos and swapped letters count as 1). Results are ranked by distance, then shared prefix, and the top three are read out as numbered options. The dictionary is read through `AudioIfExtensions.getDictionaryWords`. Z-Machine dictionaries store only the first 6 (v1-3) or 9 (v4+) letters of each word, so shortened entries are expanded back to full words seen in the game's output; entries that can't be expanded are skipped. Object words are only suggested if the game has mentioned them, so suggestions name real things. Typos of words up to 4 letters allow edit distance 1; longer words allow 2.
- Infocom games. Versions come from the [Obsessively Complete Infocom Catalog](https://eblong.com/infocom/), using the "Text Adventure Masterpieces of Infocom" (1996) release of each game.
  - Zork I, II and III are bundled in `audioif/src/main/assets/games/`. Microsoft released them under the MIT License in November 2025; the license is in `LICENSE-zork.txt` next to them. On start, `GameLibrary` copies them into the app's private `games` folder, which `FileScanner` lists before the device-wide scan.
  - The other 27 Masterpieces games are still copyrighted by Microsoft, so they're not stored in this repository. The "Download Infocom games" screen (`DownloadGamesActivity`) downloads them from eblong.com on request, after a one-time notice. `GameDownloader` checks each file against the catalog MD5 before saving it.
  - Colossal Cave Adventure is downloadable too: Graham Nelson's Inform port (`Advent.z5`, Release 9 / 060321) from the [IF Archive](https://ifarchive.org/if-archive/games/zcode/Advent.z5). It's the standard 350-point version, and its parser accepts full sentences, which suits voice input.
  - Not included: the graphical `.z6` games (Arthur, Journey, Zork Zero), and Hitchhiker's Guide and Shogun, which aren't on Masterpieces. Where Masterpieces had separate Mac and PC files, the PC file is used; Sherlock is the PC no-sound build.
  - The game list is in `InfocomCatalog`; add a `Game` entry (title, file name, MD5) to offer another download.
  - Saved games now go next to the game file, with its extension replaced by `.sav`. The old code cut the path at the first dot, which broke for paths containing dots such as the app's private folder.
- Automatic resume. After every command, and whenever you leave the game screen, `AutoSave` stores the game state (Quetzal format, via `ZGame.saveState`) and the last text shown in the app's private `files/autosave` folder, one save per story file. Reopening the same game restores it, says "Welcome back…", and reads the recap. To start over, say or type `restart`. A save from a different release of a story file is ignored, and the save is deleted when a game ends. This is separate from the in-game `save`/`restore` commands, which still write a `.sav` file next to the game.
- Reading voice screen (`VoiceSettingsActivity`): lists the TTS engine's installed voices for the device language, with speed and pitch sliders (0.5x–2.0x). `VoiceSettings` saves the choice and applies it in audio mode after `setLanguage`. A saved voice that's later uninstalled is ignored.
- The game list scan (`FileScanner`) runs on a background thread with a "Searching for games..." spinner. It used to walk all of shared storage on the UI thread, which froze audio mode (an ANR) on phones with many files. It now skips hidden folders and `Android/`, and stops 8 folders deep.
- The menu no longer opens the "All files access" settings page on every launch. Bundled and downloaded games don't need that permission, so the app asks once with an explanation and an "Open settings" button.
- "Classic adventure" visual theme. A dark ink/parchment palette with amber accents (`res/values/colors.xml`, `styles.xml`), serif story text, rounded buttons with Material icons, and styled dialogs. The menu was redesigned with a title header and a large primary audio-mode button. Audio mode shows a status pill ("Reading… tap to skip", "Listening… say a command", "Paused — tap the story to listen"). In text mode, command echoes are shown in amber. The game picker is a compact titled dialog that hides file extensions.
- ZMPP changes are isolated for easy upstream merges. The `zmpp` module is upstream ZMPP 1.5 (the latest SourceForge commit, from 2011). The app's additions to it live in one new file, `zmpp/src/main/java/org/zmpp/vm/AudioIfExtensions.java`, and the only edit to an upstream file is one marked visibility change in `MachineImpl`. `zmpp/UPSTREAM.md` records the base commit, the local changes, and how to pull in upstream fixes.
- Build toolchain upgraded to Android Gradle Plugin (AGP) 9.4.1 (see below).
- Headless play harness for testing command handling (`audioif/src/test/java/.../harness/PlayHarness.java`). It plays a game on the desktop JVM through the same input path as text mode (`CommandSession` → `CommandNormalizer`/`CommandSuggester` → `ZGame` → ZMPP).
  - Write one command per line to a text file, then run `gradlew :audioif:testDebugUnitTest --tests "*PlayHarness*" -Pplay=C:\path\commands.txt`. Optional flags: `-PplayGame=<story file>` (default: bundled Zork I) and `-PplaySeed=<n>`.
  - Each run replays the file from the start with a seeded random generator, so results are repeatable.
  - Output goes to `audioif/build/play/`: `transcript.md` (with `[sent: …]` and **REJECTED** markers), `transcript.json`, `latest.txt` and `summary.txt` (number of inputs and rejected inputs). App notes appear as "(app)" lines.
  - A replay of a 148-command Zork I session played by an AI agent went from 78 rejected inputs to 15 after these changes. A 180-command Zork II session went from 35 rejected inputs to 13.
  - Without `-Pplay` the test is skipped.

### Android Gradle Plugin upgrade
The original project used AGP 3.2.0, which can't parse the manifest `<queries>` element. Android 11+ needs that element to let the app find the speech recognition service (`android.speech.RecognitionService`), so the build was upgraded to the latest stable AGP:

| | Before | After |
|---|---|---|
| Android Gradle Plugin | 3.2.0 | 9.4.1 |
| Gradle wrapper | 4.6 | 9.6.0 |
| JDK | 8 | 17+ |
| compileSdk | 33 | 36 |
| targetSdk | 33 | 36 (see below) |
| minSdk | 24 | 24 |
| Build tools | 28.0.2 (pinned) | 36.0.0 (AGP default) |
| Repositories | `jcenter()`, `google()` | `google()`, `mavenCentral()` |

Changes required by the upgrade:
- **Support Library → AndroidX** (AGP 9 no longer supports the old Support Library or Jetifier):
  - `com.android.support:appcompat-v7:27.1.1` → `androidx.appcompat:appcompat:1.8.0`
  - `com.android.support.constraint:constraint-layout:1.0.2` → `androidx.constraintlayout:constraintlayout:2.2.2`
  - `espresso-core:2.2.2` → `androidx.test.espresso:espresso-core:3.7.0`; `junit:4.12` → `4.13.2`
  - Java imports (`android.support.v4`/`v7` → `androidx.core`/`androidx.appcompat`) and layout XML tags (`android.support.constraint.ConstraintLayout` → `androidx.constraintlayout.widget.ConstraintLayout`) were updated to match.
  - `android.useAndroidX=true` is set in the new `gradle.properties`.
- **Dependency configurations:** `compile`/`testCompile`/`androidTestCompile` → `implementation`/`testImplementation`/`androidTestImplementation`.
- **Namespaces:** the `package` attribute was removed from both `AndroidManifest.xml` files and replaced by `namespace` in each module's `build.gradle` (`org.redrossistudios.audioif`, `org.zmpp`).
- **DSL updates:** `compileSdkVersion`/`minSdkVersion`/`targetSdkVersion` → `compileSdk`/`minSdk`/`targetSdk`; `buildToolsVersion` and the empty `productFlavors` block were removed; `targetSdk`, `versionCode` and `versionName` were removed from the `zmpp` library module because AGP no longer accepts them for libraries.
- **ProGuard:** `proguard-android.txt` → `proguard-android-optimize.txt` (AGP 9 dropped the old default file). Minification is still disabled.
- **targetSdk 36:** Google Play Protect on current Android versions blocks sideloaded apps that target an old API level ("built for an older version of Android"), so `targetSdk` was raised from 33 to 36. Apps targeting 35+ are drawn edge-to-edge, and the keyboard no longer resizes the window automatically. `helpers/EdgeToEdge.applySystemBarPadding()` is called after `setContentView` in every activity. It pads the content by the status/navigation bar, display cutout and keyboard insets so that nothing ends up under the system bars or the keyboard.

## Building
Requirements: JDK 17 or newer, and an Android SDK with platform 36 and build-tools 36.0.0. Set `ANDROID_HOME` or create a `local.properties` file containing `sdk.dir`.

```
./gradlew assembleDebug
```

The APK is written to `audioif/build/outputs/apk/debug/`.

### Walkthrough hints

The hints come from the IF Archive's step-by-step Zork walkthroughs (`solutions/Sols2.zip`). They aren't stored in this repository. For a personal build, fetch them before building:

```
powershell -File tools/fetch-walkthroughs.ps1
```

This copies `Zork I.txt`, `Zork II.txt` and `Zork III.txt` into `audioif/src/main/assets/walkthroughs/`, which is git-ignored. Without them the app builds and runs normally, and "help" reads the general command help.

How it works (`helpers/WalkthroughParser`, `WalkthroughIndex`, `HintCatalog`, `HintAdvisor`, `HintLoader`):
- When a game starts, `HintLoader` replays its walkthrough in a hidden copy of the game on a background thread. `GameStateReader` records, for each step, which object parents, attributes and globals it changed. Steps that fail (for example a random fight or a timed event) are retried from earlier snapshots, with extra waits if needed.
- During the replay, the first time the walkthrough reaches a room, every direction is tried and then undone. This gives a map of real exits. Zork's map isn't symmetric (east from North of House leads behind it, but west from there enters the kitchen), so routes only use moves that the replay checked.
- A goal is a step that changes lasting state without moving the player. The next hint is the first goal whose changes aren't already true in your game, so hints follow your progress even if you play in a different order.
- The first load takes a while (up to tens of seconds on a phone). Until then "help" says hints are loading. The result is cached in the app's `files/hints` folder by game checksum, so later loads are instant.
- Only Z-machine story files (`.z1`–`.z8`) with a matching walkthrough get hints.

To pull in changes from the original project:

```
git remote add upstream https://github.com/drossimarinho/audioIF.git
git fetch upstream
git merge upstream/master
```
