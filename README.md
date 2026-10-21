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
Speech recognition runs inline, without a popup: the app starts listening as soon as it finishes reading the story text. If your speech isn't understood, the app says "Sorry, I didn't catch that. Please try again." and listens again. Spoken directions and common verbs are converted to short commands: "north west" or "go north west" becomes NW, "up" becomes U, "examine lamp" becomes "x lamp", and so on. A conversion is only used if the game recognizes the short form. If the game doesn't understand a command (for example "You can't see any such thing." or "That's not a verb I recognise."), the app offers up to three similar commands from the game's vocabulary, for example "Did you mean one, take the lamp, two, take the lake, three, something else?" Say the number to run that command, say "something else" to be asked again, or just say a new command. Long-press the screen to show the keyboard and type a command instead; this pauses voice input.

The app asks for microphone permission the first time audio mode starts. Voice commands don't work without it.

If there is any problem or recommendation please let me know.

Text to speech configuration: It gets the default configuration from your phone, so if you want to change speed of speech or language you can do it on android settings, language and input, text to speech output.

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
- Spoken commands are normalized by `CommandNormalizer` (`audioif/src/main/java/org/redrossistudios/audioif/helpers/CommandNormalizer.java`). It maps the eight compass directions, up/down (with or without a leading "go"), and the verbs examine / look at → `x`, inventory → `i`, wait → `z`, again → `g`. Phrases only match whole words at the start of the utterance, and the longest match wins. A mapping is skipped if the loaded game's dictionary doesn't contain the short form. To add a mapping, add a `put("spoken phrase", "command")` line to its static block.
- Rejected commands get spoken suggestions from `CommandSuggester` (`audioif/src/main/java/org/redrossistudios/audioif/helpers/CommandSuggester.java`). After each command, the game's response is checked against a list of rejection phrases (add more with `CommandSuggester.addRejectionPhrase`). If it matches, the unknown words in the command (or, if every word is known, the object words) are replaced with dictionary words within a small edit distance (typos and swapped letters count as 1). Results are ranked by distance, then shared prefix, and the top three are read out as numbered options. The dictionary is read through new ZMPP methods `Dictionary.getEntryWord` and `Machine.getDictionaryWords`. Z-Machine dictionaries store only the first 6 (v1-3) or 9 (v4+) letters of each word, so a suggestion may be a shortened word; the game still accepts it.
- Build toolchain upgraded to Android Gradle Plugin (AGP) 9.4.1 (see below).

### Android Gradle Plugin upgrade
The original project used AGP 3.2.0, which can't parse the manifest `<queries>` element. Android 11+ needs that element to let the app find the speech recognition service (`android.speech.RecognitionService`), so the build was upgraded to the latest stable AGP:

| | Before | After |
|---|---|---|
| Android Gradle Plugin | 3.2.0 | 9.4.1 |
| Gradle wrapper | 4.6 | 9.6.0 |
| JDK | 8 | 17+ |
| compileSdk | 33 | 36 |
| targetSdk | 33 | 33 (unchanged, so runtime behavior stays the same) |
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
