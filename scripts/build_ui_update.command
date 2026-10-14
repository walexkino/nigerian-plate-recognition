#!/bin/bash
# Run locally in Terminal if Codex's automatic permission service is unavailable.
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"
python3 apps/android/scripts/build.py --check
if [[ -d /Library/Developer/CommandLineTools/SDKs/MacOSX15.4.sdk ]]; then
    export SDKROOT=/Library/Developer/CommandLineTools/SDKs/MacOSX15.4.sdk
fi
if [[ -x .venv/bin/python ]]; then
    .venv/bin/python apps/macos/scripts/build.py
else
    python3 apps/macos/scripts/build.py
fi
if command -v node >/dev/null 2>&1; then
    node --test apps/macos/tests/*.test.js
fi
printf '\nBuilt Android 0.3.1 and macOS 0.3.1. Finder will show both outputs.\n'
open -R "$project_dir/dist/android/PlateLab-Android-0.3.1.apk"
open -R "$project_dir/dist/macos/PlateLab.app"
