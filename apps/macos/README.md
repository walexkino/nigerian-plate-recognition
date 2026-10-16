# PlateLab for macOS — 0.7.1

Native Swift, WKWebView, and Apple Vision recognition for images, videos, and live RTSP/IP-camera feeds. Includes region selection, candidate inspection, manual review, JSON export, saved light/dark appearance, and optional Bitcoin support.

## Contents

- `native/`: Swift application host, recognition engine, and live-feed decoder.
- `ui/`: HTML, CSS, and JavaScript loaded by the native app; the interface preview is `ui/index.html`.
- `scripts/`: packaging, optional local preview server, and support-QR generation.
- `tests/`: JavaScript workflow tests and native Swift smoke/layout checks.
- `requirements-live.txt`: optional FFmpeg decoder for live feeds.

The original source icon is in `../../shared/branding/`. Android does not use this UI or the Swift code.

## Build and run

Run from the repository root on macOS 13 or newer, with Apple's command-line developer tools:

```sh
python3 apps/macos/scripts/build.py
open dist/macos/PlateLab.app
```

For live-feed support:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r apps/macos/requirements-live.txt
.venv/bin/python apps/macos/scripts/build.py
```

The output is an ad-hoc signed local app for the build machine's architecture. Source builds may include a selection of the user's local dataset samples; pass `--without-samples` to omit them. Generated packages, dataset files, and user media stay out of Git.

If the installed Swift compiler needs a matching SDK on this Mac, prefix the build command with `SDKROOT=/Library/Developer/CommandLineTools/SDKs/MacOSX15.4.sdk`.

For an optional local browser preview after building, run `python3 apps/macos/scripts/dev_server.py` and visit http://127.0.0.1:58070. The native app itself needs no server or Python runtime.

## Checks

```sh
node --test apps/macos/tests/*.test.js
```

Native layout checks use an empty workspace and the support panel:

```sh
SDKROOT=/Library/Developer/CommandLineTools/SDKs/MacOSX15.4.sdk swiftc apps/macos/tests/appearance_webkit.swift -module-cache-path build/macos/module-cache -o build/macos/appearance-webkit
build/macos/appearance-webkit "$PWD/apps/macos/ui" "$PWD/outputs/audit"
```

Text history is held in memory on macOS; export important readings before quitting. Recognition scores are uncalibrated and all candidates need review. See the root README and research guides for limitations.

## Local weather

Choose **Weather** in the navigation. Use approximate location after granting permission, or search for and select a city. Current modeled conditions and the next three days use Open-Meteo over HTTPS. Temperature uses °C and wind uses km/h. Location is rounded to two decimal places before transmission and is not saved in recognition reports or on disk. Weather needs internet; OCR remains local. See the root README for attribution, privacy, and the free noncommercial API terms.

## Distant plates and glass-style interface

Enable **Distant plates**, then select a tight plate region. Native recognition reads the corresponding region from the original file, decoded with a 4096-pixel limit, rather than enlarging the small preview. A distant-mode live connection requests a bounded 2560-pixel feed and preserves the exact displayed frame for OCR. Pause the live feed to select a plate. This does not control a camera's optical zoom or reconstruct absent characters.

Weather search now suggests streets, addresses and cities using Photon/OpenStreetMap after a short typing pause. The glass-style surfaces retain opaque text, and honor reduced-motion/transparency preferences.

## Optional account and connection status

Choose **Account** to sign in with email/password, create an account, reset a password, use Google, or continue as a guest. New email accounts can verify their address and recheck it in the app. Google users can add a separate PlateLab password while keeping the same account. Display names are not unique login identifiers. Google authentication opens in the system authentication browser, with PKCE, state, and nonce protection; Firebase verifies the Google credential before the app shows a signed-in account. Sign out and separately confirmed account deletion are available. The local Mac build retains credentials in native memory only, for up to one hour or until the app closes. It does not upload plate readings or enable database backup. Local workspace readings remain shared workspace data.

For a configured source build, register an Apple app in Firebase with the matching bundle ID, enable Google sign-in, and save the downloaded `GoogleService-Info.plist` at `apps/macos/GoogleService-Info.plist`. This path is ignored by Git. The build derives the native OAuth callback scheme when a Google client ID is present. Email/password also works with Firebase configuration that has no Google OAuth client. Enable Email/Password and the matching password policy in Firebase Authentication. Without it, guest mode remains available. No web client secret or service-account key belongs in this app. See [Firebase setup](../../docs/firebase.md).

The header shows green **Online** after a successful empty HTTPS connectivity check, or red **Offline** if access cannot be verified. A path change, returning to the window, or a 30-second interval triggers another check. The check sends no account details, plates or location. A blocked connectivity endpoint can show Offline even if another service works; the indicator does not disable OCR. Browser previews label unverified network access as **Connected**.

The native bridge accepts messages only from the packaged main page. Main-page navigation, subframes, new windows, scripts, and content loading are restricted. The packaged app uses hardened runtime and remains ad-hoc signed for local use; this is not Apple notarization. See [security boundaries](../../SECURITY.md).

Password entry happens in native secure macOS sheets, never HTML fields or JavaScript bridge payloads. Firebase receives the password over HTTPS and manages its salted hash. The app does not persist a password/hash, log it, or include it in reports. New/added passwords require 6–128 characters and confirmation; fields clear on submission or dismissal. The rest of the Mac session remains memory-only.
