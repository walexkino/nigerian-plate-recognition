# PlateLab for macOS — 0.3.1

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
