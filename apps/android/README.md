# PlateLab for Android — 0.7.0

A native Android prototype with bundled ML Kit Latin text recognition, photo import, live phone-camera sampling, manual transcription, review, and JSON export. The macOS release remains separate.

## First-launch experience

A branded welcome screen appears until the user finishes or skips onboarding. Take a quick tour explains photo import, the live phone camera, recognition, human review, history, JSON export, appearance, and optional support in eight steps. Each step highlights the corresponding workspace control and offers Next, Back, and Skip tour; the last step offers Finish tour. The walkthrough does not open files, start a camera, request permission, or export reports.

Finishing or skipping is remembered across launches. The current step survives activity recreation during the tour. About → Replay tour opens the welcome screen again without replacing the current photo or reading; an active camera is paused first. The welcome and tour match the chosen theme, allow larger text to scroll, keep navigation buttons visible, and respect the system animation setting. In compact landscape layouts, the explanatory card uses the safe screen height instead of obscuring a highlighted control.

## Optional Google account

Open **Account · Guest** to sign in with Google or continue as a guest. Sign-out and confirmed PlateLab account deletion are available on the account screen. Recognition still works without an account. This release does not upload readings or photos, write database profiles, or provide cloud backup; local history remains shared device history. See [Firebase configuration and tests](../../docs/firebase.md). A clean checkout without the ignored `app/google-services.json` builds in guest mode.

## Features

- Saved light/dark appearance control; switching preserves the current photo and reading.
- Scrollable layouts with buttons that grow for larger text, stacked controls on narrow screens, and a centered workspace on tablets.
- Android 6.0 (API 23) or newer.
- Import a photo through the system document picker; no broad photo-library permission.
- Start the rear camera after granting camera permission. Read the newest frame up to once per second; pause freezes the latest completed reading's image for review.
- Plate-format filtering and adjacent-fragment joining preserve the original OCR text. They do not verify registration or reconstruct hidden characters.
- Review a reading, select an alternative, or store a separate manual transcription. Missing OCR confidence is displayed as unavailable.
- Save the newest 200 text-only records with AES-GCM encryption and an Android Keystore key in app-private storage. Source images are not retained in history, and old history entries cannot show the original photograph.
- Export JSON to a location selected in Android's document picker. Export includes original OCR, selected candidate, manual transcription, and review status.
- PlateLab branding and direct Bitcoin support via a QR code, wallet URI, or copy address. Donations are confirmed in the user's wallet; the app does not verify completion.

Photo and camera processing runs on the device using the bundled recognition model. Optional weather uses the internet permission and requests approximate foreground location only when Use my location is selected. Location access is not required for OCR; city search works without it. No API key, paid inference service, Google Play registration, or login is required to build and install locally.

This Android prototype does **not** support imported video files or RTSP/IP-camera feeds. It has not been evaluated for highway recognition accuracy. The model differs from Apple Vision, so results and OCR scores will differ from the macOS app. Recognition requires a clear, sufficiently large view of visible characters.

## Build

Install JDK 17 and an Android SDK with platform 36 and build-tools 35.0.0. Google requires acceptance of its Android SDK licence before the SDK can be used. No paid developer account is required.

Open `apps/android/` in Android Studio (or run the Gradle commands below from that directory), or set `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
```

The local package is `app/build/outputs/apk/release/app-release.apk`. Debugging is disabled. It retains the existing development signing certificate so previous local installations can update; this does not make it a production/store release. A production release needs a private release signing key and its matching Firebase fingerprint. Keep signing keys outside Git.

To install on a connected phone with USB debugging enabled:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

Alternatively, transfer the APK to your Android phone and open it there. Android asks you to allow installation from the application opening the file. The APK cannot run on macOS or an iPhone.

## Verification

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
# Requires a connected Android device or emulator:
./gradlew connectedDebugAndroidTest
```

Unit tests cover normalization, format filtering, absent readings, fragment geometry, separate vehicles, deduplication, and unavailable scores. Instrumentation tests exercise the bundled model on a generated text fixture, preservation of OCR during manual review and persistence, the complete photo workflow, and live synthetic-camera processing with pause. Tests use generated text, never private dataset photographs.

## Privacy

Camera permission is requested only when Start camera is selected. Camera capture is stopped when the app leaves the foreground. Photos are selected by the user and decoded at a bounded working resolution. Text history is encrypted with AES-GCM using an Android Keystore key in app-private storage; Android cloud backup is disabled. Existing JSON history migrates atomically on first successful load. Unreadable or modified history is preserved and cannot be overwritten by new readings. Encryption keys are device-local; losing app storage or the key makes this history unrecoverable. Exporting deliberately creates a report at the location chosen by the user, which can include a cloud-backed document provider.

History contains plate text and timestamps. Clearing this app's storage or uninstalling it removes local history. Export important results beforehand. System screenshots, exported reports, and activity in other applications are outside the app's private-history storage.

## Local weather

Tap **Local weather** near the theme button, then **Use my location**, or type a street, address or city, then select a suggestion. Current modeled conditions and a three-day forecast include temperature, feels-like, wind, humidity, highs/lows, and rain probability, with the forecast time and timezone. Coordinates are rounded before Open-Meteo requests; no weather location is stored on disk or included in plate reports. Missing values display as unavailable. Requests are canceled when the panel closes or the app leaves the foreground. See the root README for attribution, data flow and noncommercial API terms.

### Verified local build

The Android 0.5.0 APK was compiled with SDK 36 and checked with 16 Java unit tests and Android lint (no errors). Android 15 emulator tests verified the bundled OCR model on a generated plate, photo decoding and recognition, manual review/persistence, history after a new activity launch, and live synthetic-camera processing with pause retaining its original frame. A physical phone has not yet been tested.

On this Mac, `python3 apps/android/scripts/build.py --check` uses the approved project-local SDK/JDK tools and copies the verified, non-debuggable APK and SHA-256 checksum to `dist/android/`.

Version 0.3.1 includes both the appearance update and the first-launch guided tour. Native instrumentation verifies first-launch welcome, skipping, replay, all eight steps, Back, rotation restoration, and preserving a reviewed photo during replay. Navigation visibility is checked at phone size, 320dp with a 1.5 font scale, tablet landscape, and phone landscape in both themes.

## Detailed plate capture and appearance

Enable **Distant plates** before importing a photo or opening the camera. The camera exposes device-supported zoom and tap-to-focus, with **Capture detailed photo** to pause on a still image. Drag a plate area in that image, then select **Recognize photo**. You can also select an area in an imported photo. Distant imports retain up to 4096 pixels on the long edge; standard imports retain up to 1280. Plate boxes and exported region metadata refer to the full displayed source, not the cropped OCR coordinates. See `../../docs/distant-plates.md` for limits and checks.

The native interface uses glass-inspired gradient panels, soft borders, and rounded buttons, with an opaque readable fallback rather than requiring a recent Android blur API. Address suggestions use Photon/OpenStreetMap; forecast requests use Open-Meteo. Searches and selection remain in memory and are not written to plate reports.

## Connection indicator and security checks

A small green **Online** or red **Offline** label appears in the workspace and account screen. It follows Android's validated internet status, updates on network changes, and is refreshed when returning to the app. Wi-Fi association alone does not count as internet. Local recognition remains available offline. Both themes use readable status colours and explicit text.

The account screen blocks screen capture and filters obscured button taps. Sign-out cannot remain stuck indefinitely waiting for a credential provider. The local packaging script verifies that the APK has debugging and backups disabled, prohibits cleartext/test network settings, and verifies its signature before copying the download.

Version 0.7.0 adds history-encryption roundtrip, plaintext migration, tamper rejection, interrupted-write recovery, and emulator-only disconnect/reconnect checks. The network test skips physical phones. See [security boundaries](../../SECURITY.md) and [Firebase tests](../../docs/firebase.md). Samsung Auto Blocker and normal sideload warnings remain controlled by Android.
