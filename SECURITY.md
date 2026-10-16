# PlateLab security boundaries

No app can guarantee immunity from attack. These controls reduce exposure; they do not establish a penetration-test certification or protect a compromised operating system.

## Data and accounts

Plate recognition runs on the device. Neither app includes a cloud history feature or writes Firestore profiles. The live Firestore database was inspected on 2026-10-16 and rejects all client reads and writes. `backend/firebase/firestore.rules`, the default deployable file, has the same deny-all policy. Authenticated users do not bypass it. The separately named profile rules proposal is not deployed.

Signing in is optional and does not create private per-account local workspaces. People using the same installation can see that device's local readings. Sign-out and authentication-account deletion preserve local history. Exported JSON is plaintext and goes to the destination the user selects.

Android history uses AES-GCM with fresh nonces and an Android Keystore key. Existing plaintext history migrates through an atomic write; unreadable, tampered or keyless encrypted history is preserved and blocks replacement. Cloud backup is disabled. The account screen blocks screenshots/recents captures and filters obscured button taps. Those protections do not apply to the recognition workspace or deliberate exports.

Mac history is held in memory. Google sign-in uses ASWebAuthenticationSession and authorization-code PKCE with fresh state and nonce. Firebase verifies the returned Google credential over HTTPS. Tokens stay in native memory and are never sent to the WebView, saved to preferences, logged, or placed in recognition reports. Mac sessions expire within one hour and end on app termination. Google passwords never pass through PlateLab. Android's persistent account session is managed by the Firebase SDK.

## App and network restrictions

The Mac native bridge accepts only the exact packaged main document and rejects subframes. Navigation is allowlisted, new WebViews are denied, and a content security policy restricts scripts, frames, objects, connections and forms. The local bundle uses hardened runtime but is ad-hoc signed, not notarized.

Normal Android packages disallow cleartext traffic and backups. The distribution script builds a non-debuggable APK and checks the compiled manifest, network policy, and signature. The existing development signing certificate is retained for local update compatibility; a controlled production signing key remains a separate distribution step. Emulator-only debug builds permit HTTP to `10.0.2.2`; these are never copied as the distributable package.

Weather, address suggestions, Google sign-in, Firebase calls, and the Mac internet probe use HTTPS. The empty Mac connectivity probe contacts Google's `www.gstatic.com/generate_204` and sends no plate, account or location data. Android uses the operating system's internet validation. Connectivity status is informational and does not promise that every service is available.

User-supplied Mac live-camera URLs may use RTSP or HTTP on a trusted local network. These protocols are not encrypted. Prefer RTSPS/HTTPS where supported; do not expose a camera directly to the public internet. Camera URLs can contain credentials, so the app excludes them from reports and suppresses decoder diagnostics. Another privileged local process may still inspect a decoder's command-line arguments.

## Configuration and remaining deployment work

Firebase client configuration and OAuth client IDs identify an app/project; they are not administrator secrets. They remain outside source control in this repository. Never embed a Firebase service-account private key or a web client secret in either app. Database protection must come from server-side rules and account permissions.

Firebase App Check enforcement is not enabled for these local sideloaded/ad-hoc builds. Adding enforcement requires a working attestation provider for each distributed app and testing before enforcement to avoid locking out legitimate users. The current deny-all database rules still block every client data request. Authentication endpoints remain subject to Firebase's anti-abuse and quota controls; this audit does not claim abuse-proof public sign-in.

Project administrators and Admin SDK/service-account credentials can bypass Firestore rules. Protect the project owner's Google account with two-step verification, keep project membership minimal, and never publish administrator keys. Administrative access and API-key restrictions were not exhaustively audited in this change.

Production Android Google consent still needs verification on a physical phone with Google Play services. The Mac production sign-in completed successfully during local verification. Automated tests use synthetic identities/local emulators; destructive account tests never target the live project. App-store signing, notarization, and independent security testing remain separate work.

Report a suspected vulnerability privately to the repository owner. Do not publish tokens, camera credentials, plate records, or location details in a public issue.
