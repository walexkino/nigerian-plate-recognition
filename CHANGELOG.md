# Changelog

## Repository organization — 2026-10-14

- Separate platform code, builds, and tests into `apps/macos/` and `apps/android/`.
- Group shared source branding under `shared/branding/` and research tools/requirements under `research/`.
- Update build commands, test imports, and local package paths; retain the existing repository licence and synthetic documentation image.
- Add `© 2026 Akinwale` to app footers and the Android welcome screen, and match the repository copyright notice.

## 0.3.1 / Android 0.3.1 — 2026-10-14

- Display `© 2026 Akinwale` in both app footers, on the Android welcome screen, and in Android About.
- Match the repository licence copyright attribution; keep the MIT grant unchanged.

## Android 0.3.0 — 2026-10-14

- Add a branded first-launch welcome with a plate illustration, quick-tour action, and skip option.
- Add eight guided steps that highlight real controls and explain the complete Android workflow.
- Keep Next, Back, and Skip available; remember completion/skip and restore the current tour step after rotation.
- Offer Replay tour from About while preserving the current photo and reading.
- Match light/dark appearance, support scrolling explanations and larger text, and respect animation settings.
- Add onboarding instrumentation checks and touch feedback for workspace buttons.

## 0.3.0 — 2026-10-14

- Add a light/dark appearance button with a saved preference and system appearance on first launch.
- Keep navigation visible on compact windows; wrap header actions, plate text, and dialog contents.
- Allow smaller macOS windows and improve photo-area scaling.

## Android 0.2.0 — 2026-10-14

- Add saved light/dark appearance controls without replacing the active photo or reading.
- Stack paired buttons on narrow screens or with larger text; remove fixed button heights.
- Scale the photo viewport and center the workspace on larger screens.
- Match dialogs and system bars to the chosen appearance.

## 0.2.1 — 2026-10-14

- Add a custom PlateLab application icon for Finder and the macOS Dock, with standard and Retina resolutions.

## 0.2.0 — 2026-10-14

- Add live RTSP/IP-camera recognition with bounded frame processing, pause/resume, and disconnect.
- Add a Bitcoin support panel with a wallet action, verified QR code, and copy-address button.
- Keep Safari on the donation panel instead of navigating to an unsupported Bitcoin URI; check for a wallet in the native app.
- Fix manual review when OCR returns no plate candidate, stale crop/review state, candidate ranking, video import failures, frame timestamps, and short-video scanning.
- Unlock alternative readings when live recognition pauses.
- Share the full semantic version and build number between the UI and macOS bundle through `apps/macos/ui/release.js`.

## 0.1.0

- Initial macOS recognition studio with Apple Vision, image/video import, crop selection, review, and JSON export.

## Android 0.1.0

- Add a native Android prototype with bundled ML Kit photo and live phone-camera OCR.
- Port plate-format checks and fragment joining, review, manual transcription, and JSON export.
- Add bounded private text history, camera permission handling, and the PlateLab launcher icon.
- Keep the Mac release unchanged; imported videos and RTSP feeds remain desktop features in this initial Android version.
