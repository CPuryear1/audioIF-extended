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
- Spoken commands are normalized by `CommandNormalizer` (`audioif/src/main/java/org/redrossistudios/audioif/helpers/CommandNormalizer.java`). It maps the eight compass directions, up/down (with or without a leading "go"), and the verbs examine / look at / check / check out / inspect / study → `x` (falling back to `examine` when the game has no `x`), look inside / look into → `look in`, inventory → `i`, wait → `z`, again → `g`. Phrases only match whole words at the start of the utterance, and the longest match wins. A mapping is skipped if the loaded game's dictionary doesn't contain the short form. Text mode uses the same normalizer and suggestions (shared through `CommandInput`): the story shows the command actually sent in brackets, e.g. `>check the mailbox  [examine the mailbox]`, and a rejected command lists numbered alternatives you can pick by typing the number. This makes text mode a quick way to test voice-command handling. To add a mapping, add a `put("spoken phrase", "command")` line to its static block. Separate fallbacks with `|` (for example `"x|examine"`); the first one whose verb the game knows is used.
- Rejected commands get spoken suggestions from `CommandSuggester` (`audioif/src/main/java/org/redrossistudios/audioif/helpers/CommandSuggester.java`). After each command, the game's response is checked against a list of rejection phrases (add more with `CommandSuggester.addRejectionPhrase`). If it matches, the unknown words in the command (or, if every word is known, the object words) are replaced with dictionary words within a small edit distance (typos and swapped letters count as 1). Results are ranked by distance, then shared prefix, and the top three are read out as numbered options. The dictionary is read through new ZMPP methods `Dictionary.getEntryWord` and `Machine.getDictionaryWords`. Z-Machine dictionaries store only the first 6 (v1-3) or 9 (v4+) letters of each word, so a suggestion may be a shortened word; the game still accepts it.
- Infocom games. Versions come from the [Obsessively Complete Infocom Catalog](https://eblong.com/infocom/), using the "Text Adventure Masterpieces of Infocom" (1996) release of each game.
  - Zork I, II and III are bundled in `audioif/src/main/assets/games/`. Microsoft released them under the MIT License in November 2025; the license is in `LICENSE-zork.txt` next to them. On start, `GameLibrary` copies them into the app's private `games` folder, which `FileScanner` lists before the device-wide scan.
  - The other 27 Masterpieces games are still copyrighted by Microsoft, so they're not stored in this repository. The "Download Infocom games" screen (`DownloadGamesActivity`) downloads them from eblong.com on request, after a one-time notice. `GameDownloader` checks each file against the catalog MD5 before saving it.
  - Colossal Cave Adventure is downloadable too: Graham Nelson's Inform port (`Advent.z5`, Release 9 / 060321) from the [IF Archive](https://ifarchive.org/if-archive/games/zcode/Advent.z5). It's the standard 350-point version, and its parser accepts full sentences, which suits voice input.
  - Not included: the graphical `.z6` games (Arthur, Journey, Zork Zero), and Hitchhiker's Guide and Shogun, which aren't on Masterpieces. Where Masterpieces had separate Mac and PC files, the PC file is used; Sherlock is the PC no-sound build.
  - The game list is in `InfocomCatalog`; add a `Game` entry (title, file name, MD5) to offer another download.
  - Saved games now go next to the game file, with its extension replaced by `.sav`. The old code cut the path at the first dot, which broke for paths containing dots such as the app's private folder.
- Reading voice screen (`VoiceSettingsActivity`): lists the TTS engine's installed voices for the device language, with speed and pitch sliders (0.5x–2.0x). `VoiceSettings` saves the choice and applies it in audio mode after `setLanguage`. A saved voice that's later uninstalled is ignored.
- The game list scan (`FileScanner`) runs on a background thread with a "Searching for games..." spinner. It used to walk all of shared storage on the UI thread, which froze audio mode (an ANR) on phones with many files. It now skips hidden folders and `Android/`, and stops 8 folders deep.
- The menu no longer opens the "All files access" settings page on every launch. Bundled and downloaded games don't need that permission, so the app asks once with an explanation and an "Open settings" button.
- "Classic adventure" visual theme. A dark ink/parchment palette with amber accents (`res/values/colors.xml`, `styles.xml`), serif story text, rounded buttons with Material icons, and styled dialogs. The menu was redesigned with a title header and a large primary audio-mode button. Audio mode shows a status pill ("Reading… tap to skip", "Listening… say a command", "Paused — tap the story to listen"). In text mode, command echoes are shown in amber. The game picker is a compact titled dialog that hides file extensions.
- Build toolchain upgraded to Android Gradle Plugin (AGP) 9.4.1 (see below).

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

To pull in changes from the original project:

```
git remote add upstream https://github.com/drossimarinho/audioIF.git
git fetch upstream
git merge upstream/master
```
