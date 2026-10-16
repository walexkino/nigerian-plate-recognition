# Optional accounts — macOS 0.7.1 / Android 0.8.1

Both native apps offer email/password sign-in and registration as the primary optional account flow, with Google as an alternative and guest recognition. Email is the login identifier; display names are not unique usernames. Firebase verifies the password or Google credential before the app displays a signed-in account. No Google password is handled by PlateLab, and only basic identity scopes are requested. Sign-out and separately confirmed deletion of the PlateLab authentication account are available. Account deletion does not delete the person's Google account or local plate readings.

Android uses Credential Manager and Firebase Authentication. Returning sessions are managed by the Firebase SDK. The Mac uses the system authentication browser (ASWebAuthenticationSession), authorization-code PKCE, fresh state/nonce checks, and Firebase's HTTPS credential exchange. Mac credentials stay in native memory for at most one hour or until closing the app; no Google/Firebase token enters the WebView or local preferences. Mac authentication does not require purchasing an Apple developer membership for this local prototype.

## What is connected

- Android is registered using its exact package name and the local APK's existing signing certificate.
- The Mac is registered as an Apple app using its bundle ID and a native Google OAuth client, with the corresponding callback scheme in its bundle.
- Locally built packages include their project client configuration. Configuration files stay outside Git; client IDs/API keys are public identifiers, not administrator secrets or a substitute for security rules.
- The live Firestore database is Standard edition in Madrid (`europe-southwest1`) on Spark. Its rules were inspected on 2026-10-16: all client reads and writes are denied.

**Cloud backup is not enabled.** Neither app includes a Firestore client, writes profiles, uploads plate readings/photos, or syncs between devices. Local history is shared device/workspace history, not separated by the signed-in account. Signing in/out or deleting an authentication account leaves local readings intact. Android encrypts its saved text history using Android Keystore; Mac readings stay in memory unless deliberately exported.

## Local configuration

For Android, enable Google sign-in in your Firebase project, register the exact application ID from `apps/android/app/build.gradle`, and add the signing certificate fingerprint of the APK you intend to install. Save the updated client configuration to `apps/android/app/google-services.json`. The Google services plugin generates Firebase resources when this ignored file exists. Without it, source builds offer guest mode; `-PplatelabGuestBuild` explicitly checks that path. Firebase BoM 34.19.0 preserves Android 6 support.

For Mac, register an Apple app with the exact bundle ID, and save its downloaded configuration to `apps/macos/GoogleService-Info.plist`. The build checks its bundle/client IDs, copies it into the local package, and derives the reverse-client-ID callback scheme. Without it the account screen explains that sign-in is unavailable and keeps guest mode usable. Do not use an Android or web OAuth client for the Mac native callback. No client secret or administrator/service-account key belongs in either app.

## Database rules

`backend/firebase/firestore.rules` is the safe default deployable policy and denies all client data access. It matches the live database. The apps do not require these rules to be relaxed for authentication.

`backend/firebase/firestore.profiles-proposal.rules` is a separately named, undeployed future proposal. It permits only Google-authenticated owner access to a bounded `users/{uid}` display name and server timestamps. Creation time is immutable; roles, extra fields, listing accounts, other people's profiles and nested reading backups remain denied. It is not selected by `firebase.json` and does not enable backup.

Before implementing backup, define explicit opt-in, user-scoped records, retention, conflict/deletion handling and additional rules tests. Do not deploy the proposal as a substitute for that work. App Check is not enforced for these local builds; see [security boundaries](../SECURITY.md).

## Verification

With Java 21 and Node 22 or later, run `npm install` then `npm test` in `backend/firebase/`. Six cases test both the default deny-all policy and the isolated future profile proposal against demo projects/local Firestore. No live database data is changed.

Android account tests require the local Auth emulator configured in `backend/firebase/firebase.json`. Build using `-PplatelabAuthEmulatorTest`, install the debug app/test APKs on the Android emulator, and pass `-e firebaseAuthEmulator true` to the instrumentation runner. This explicit test overlay permits HTTP only to emulator host alias `10.0.2.2`; normal packages stay HTTPS-only. Synthetic Google tokens accepted by the local Auth emulator are test fixtures, never production credentials. The connection-toggle test skips physical phones. Rebuild the download using `python3 apps/android/scripts/build.py --check`, which verifies package security before copying it to `dist/android/`.

Mac tests cover a standard PKCE vector, cryptographic proof uniqueness, nonce binding, callback origin/state/duplicate rejection, and native-page bridge policy. JavaScript tests cover guest/account controls, cancellation and preserving account-free recognition reports. Layout checks cover workspace, support and account panels at five widths in both themes.

The real Mac Google-to-Firebase sign-in completed successfully during local verification. Android emulator account tests do not replace a physical-phone Google chooser/consent check. See the changelog for versions and [security boundaries](../SECURITY.md) for deployment limits.

References: [Firebase Android Google authentication](https://firebase.google.com/docs/auth/android/google-signin), [Credential Manager button flow](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Google native OAuth/PKCE](https://developers.google.com/identity/protocols/oauth2/native-app), [Firebase Auth REST](https://firebase.google.com/docs/reference/rest/auth), [Firebase rules tests](https://firebase.google.com/docs/rules/unit-tests).

## Password accounts

The live PlateLab project was verified on 2026-10-16: **Email/Password** and **Google** are enabled, and passwordless email links are disabled. Password policy uses **Require enforcement**, a minimum of **6** and maximum of **128** characters, matching both apps. Email enumeration protection remains enabled (verified on 2026-10-16). Authentication does not relax the locked Firestore rules.

Firebase Authentication manages salted password hashes. Firestore is not a password store. Password requests use HTTPS; neither app stores passwords or hashes in history, preferences, reports, logs or the database. Mac password sheets use NSSecureTextField and never pass credentials through JavaScript. Android masks password fields, opts them out of saved state and keyboard learning, and clears them on submission/backgrounding. No custom reversible password encryption is used.

Registration includes a display name, email and confirmed 6–128-character password. The app sends an email verification request; users open the emailed link and choose **I've verified my email**. Resends have a cooldown. Verification is shown explicitly; the locked database does not grant unverified or verified users data access. Password reset uses Firebase's hosted email action page and gives the same neutral app response for registered and unknown addresses. Existing Google users can choose **Add email & password** while signed in, retaining the same Firebase UID.

Firebase hashing is documented in [Firebase user import](https://firebase.google.com/docs/auth/admin/import-users). See [password policies](https://firebase.google.com/docs/auth/android/password-auth#recommended_set_a_password_policy) and [email enumeration protection](https://docs.cloud.google.com/identity-platform/docs/admin/email-enumeration-protection).

## Local email-flow tests and emulator limitation

The Android email test class adds six cases for registration/display name/verification, correct vs incorrect passwords, invalid-input rejection, secure field state after recreation, reset responses and existing-Google credential linking. Mac native transport tests exercise the production HTTPS request contract through a test-only URLProtocol that routes to the loopback Auth emulator. They verify account creation, names, verification links, reset links, wrong-password rejection, same-UID linking, cross-UID link rejection and no credentials in page payloads. UI tests also check that no password input exists in the WebView. Native layout QA checks all four account sheets.

firebase-tools **15.34.0** has an Auth emulator limitation: `signUp` rejects an already registered email before processing an authenticated `idToken` link, including when it belongs to that same user. Both production apps use the documented authenticated link flow (Android through its current SDK, Mac through REST). For local tests only, `npm run auth:compat` applies a version-checked compatibility patch to the installed development emulator. It allows an existing email only when the parsed ID token's UID matches its owner, and preserves that user's verification/name. It still rejects linking someone else's email, which the native test explicitly checks. Restart the local emulator after applying it. The patch is not an app resource, SDK change, server deployment, or production fallback. After stopping the emulator, `npm run auth:restore` restores the original development dependency.

Only invented `example.test` identities are used, and email action codes come from the local emulator. Emulator tests do not establish real email delivery or production account-linking behaviour. A person should create their own account or add their own password in the installed app; the coding agent does not enter or generate real credentials.
