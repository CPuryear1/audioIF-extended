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
Speech recognition runs inline, without a popup: the app starts listening as soon as it finishes reading the story text. If your speech isn't understood, the app says "Sorry, I didn't catch that. Please try again." and listens again. Saying a diagonal direction such as "north west" or "southwest" is converted to its short command (NW, NE, SE, SW). Long-press the screen to show the keyboard and type a command instead; this pauses voice input.

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
- Build migrated to Gradle 9.6, Android Gradle Plugin 9.4.1 and AndroidX (compileSdk 36, targetSdk 33, minSdk 24).

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
