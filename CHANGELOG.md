# Changelog

## macOS 0.7.0 / Android 0.8.0 — 2026-10-16

- Make email/password the primary optional account action, with account creation, display names, verification, password reset, and Google as an alternative. Keep guest recognition.
- Allow existing Google users to add a separate PlateLab password to the same account. Keep Firestore locked and plate history local.
- Delegate salted password hashing to Firebase Authentication and send credentials over HTTPS; never store passwords or hashes in the app/database/history/reports/logs.
- Use native secure password sheets on Mac, separate from the WebView. On Android, mask passwords, exclude them from saved state/keyboard learning, and clear them after use.
- Require 12–128 characters and matching confirmation for new/added passwords; preserve neutral reset responses and email-enumeration protection.
- Add local emulator tests for registration, verification, password reset, wrong-password rejection, identity-preserving linking and password-state protection. Document the opt-in local emulator compatibility patch separately from production app code.
- Increment macOS build and Android version code to 9.

## macOS 0.6.0 / Android 0.7.0 — 2026-10-16

- Add green Online / red Offline indicators to both native apps; local recognition remains available without internet. Distinguish browser-preview network hints from verified access.
- Add optional Mac Google sign-in using the system authentication browser, PKCE, state, nonce, and Firebase verification. Keep Mac credentials in memory for up to one hour or until closing; retain guest mode and confirmed account deletion.
- Restrict the Mac native bridge and navigation to the packaged main page, add a content security policy, and package with hardened runtime.
- Encrypt Android text history with AES-GCM and Android Keystore, migrate existing history atomically, and preserve unreadable/tampered files. Protect the account screen from screenshots and obscured taps.
- Distribute a non-debuggable Android APK with the existing local signing certificate. Verify cleartext/backup settings and signature before copying it to the download folder.
- Keep deployable Firestore rules deny-all to match the locked live database; move the future profile proposal into a separately named file and test both. No cloud backup or plate uploads are enabled.
- Increment macOS build and Android version code to 8.

## Android 0.6.0 — 2026-10-16

- Add an optional Google sign-in account screen using Credential Manager and Firebase Authentication, with guest access, sign-out, and confirmed authentication-account deletion.
- Keep plate evidence and text history local; cloud backup and profile writes are not enabled. macOS remains 0.5.0.
- Support public-source guest builds without the locally supplied Firebase client configuration. Keep normal builds HTTPS-only.
- Prepare profile-only Firestore access rules and local tests; the live database remains locked pending deliberate publication.
- Increment Android version code to 7 while retaining Android 6 compatibility.

## 0.5.0 / Android 0.5.0 — 2026-10-15

- Add detailed plate capture: Android camera zoom, tap-to-focus, still capture, up-to-4096-pixel imports, and plate-area selection with full-image evidence mapping.
- Add original-file region OCR on macOS and bounded high-detail live feeds without upscaling; detailed live recognition uses the exact displayed frame.
- Add street/address suggestions while typing, delayed requests, stale-response cancellation, in-memory search caching, and optional bias toward the last selected approximate area.
- Attribute address search to Photon/OpenStreetMap and retain Open-Meteo forecast attribution; no paid account or API key is added.
- Refresh both themes with glass-inspired panels, soft highlights, rounded controls, readable dialogs, and macOS reduced-transparency/motion support.
- Increment macOS build to 7 and Android version code to 6.

## 0.4.0 / Android 0.4.0 — 2026-10-15

- Add optional local weather with one-shot approximate location and city search on both platforms.
- Show current modeled conditions, feels-like temperature, humidity, wind, and a three-day forecast with highs/lows and rain probability.
- Add forecast timestamps, explicit refresh, provider attribution, missing-data handling, and offline/permission error states.
- Keep weather independent of plate reports and on-device OCR; round coordinates and avoid background tracking or persistent weather-location storage.
- Update macOS location descriptions and Android internet/approximate-location permissions.
- Increment macOS to build 6 and Android to version code 5.

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
