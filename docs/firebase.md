# Optional Android accounts — 0.6.0

Open **Account · Guest** in the Android workspace, then choose Google's sign-in button or **Continue as guest**. Signing in is optional. A successful Google credential is exchanged with Firebase Authentication before the account is shown as signed in. Returning sessions are managed by the Firebase SDK. The app does not receive a Google password and requests no access to Drive, Gmail, contacts, or other Google account services.

The account screen offers sign-out and a separately confirmed deletion of the PlateLab authentication account. Account deletion does not delete the person's Google account. Reauthentication is required if Firebase considers the session too old. Signing out also clears Credential Manager's app session state.

## What is connected

- Google sign-in is configured for the locally built Android APK's package and signing certificate.
- The local APK contains the Firebase client configuration needed to contact the configured project. The configuration file remains outside Git. Client configuration identifies the Firebase project; it is not an administrator credential or a substitute for access rules.
- The live Firestore database was created as Standard edition in Madrid (`europe-southwest1`) on the Spark plan, with all client reads and writes denied.
- macOS remains at 0.5.0 and has no account screen in this update.

**Cloud backup is not enabled.** This release does not include the Firestore client SDK, create profile documents, upload readings, or sync between devices. Local text history is device history shared by people using the same app installation; it is not separated by the signed-in account. Signing in, signing out, and deleting the authentication account leave that local history intact. Clearing app storage or uninstalling removes local history.

## Build configuration

Register your own Android app in Firebase using the exact application ID from `apps/android/app/build.gradle`, enable Google sign-in, and add the certificate fingerprint of the APK you intend to install. Download the updated `google-services.json` into `apps/android/app/`. That exact path is ignored by Git. Never use or embed a Firebase service-account private key in an app.

Normal builds with this file use the Google services plugin to generate Firebase resources. Without the file, the public source builds in guest mode, with a disabled sign-in button and an explanation on the account screen. You can check that path explicitly with `-PplatelabGuestBuild` in Gradle. The local package script uses the connected configuration when available. Firebase BoM 34.19.0 retains the app's Android 6 minimum; BoM 35 raises the minimum Android API level.

## Database rules prepared for review

`backend/firebase/firestore.rules` is a proposed profile-only ruleset, **not deployed automatically**. It permits a Google-authenticated user to get, create, update, or delete their own `users/{uid}` profile only. Profiles allow a bounded display name and server timestamps; creation time cannot be changed. Account listing, other people's profiles, extra fields such as roles, and all nested plate-history records remain denied.

The live database stays locked until a reviewed ruleset is deliberately published. Before enabling backup, implement its explicit opt-in, user-scoped storage, bounded data model, conflict handling, deletion behaviour, and further rules tests. This profile-only proposal does not enable backup.

## Local verification

Use Java 21 and Node 22 or later for the current Firebase emulator tools. In `backend/firebase/`, run `npm install` and `npm test`. Tests use the demo project `demo-platelab` and loopback Firestore, with invented identities only. They check owner access, cross-account and guest denial, immutable timestamps, forbidden fields, and blocked backup paths. They do not change the live project.

For Android account instrumentation, run the local Auth emulator from `backend/firebase/firebase.json`, then build with `-PplatelabAuthEmulatorTest` and install the app and test APKs on an Android emulator. Pass `-e firebaseAuthEmulator true` to the instrumentation runner. This opt-in build overlay permits unencrypted HTTP **only** to the Android emulator's host-loopback alias (`10.0.2.2`); normal packages prohibit cleartext traffic. Do not distribute an emulator test build. Account instrumentation uses unsigned synthetic Google tokens accepted by the local Auth emulator, not production Google accounts. Without the explicit test flag these account tests are skipped.

The normal APK must be rebuilt after instrumentation using `python3 apps/android/scripts/build.py --check`. A physical device with current Google Play services and a Google account is still needed to verify the real Google chooser, consent, and production Firebase exchange. Local emulator success does not establish that production OAuth consent has been tested.

References: [Firebase Google authentication](https://firebase.google.com/docs/auth/android/google-signin), [Credential Manager button flow](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Firebase rules tests](https://firebase.google.com/docs/rules/unit-tests).

Verified locally for this update: 16 Android unit tests, Android lint with zero errors, 15 full instrumentation tests plus a focused four-test account run (16 distinct instrumentation cases overall), five Firestore rules tests, and an unconfigured guest build. Account previews use invented identities. Production Google consent still requires a physical-phone check.
