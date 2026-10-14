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
