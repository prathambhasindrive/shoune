# Shoune security baseline

This is a personal-use V1, not a completed security audit. Do not report private vulnerabilities by posting contact numbers, recordings, keys or other personal data in a public issue.

## Protected boundaries

- No INTERNET permission, no remote command listener, no audio recording archive. External model downloads run in the browser. Generated model text never becomes a phone action.
- Wake names are not authentication: anyone nearby or a recording can say them. Calls, navigation, app opening, contacts, notifications and AI responses require an unlocked device. On a locked phone only time, flashlight and stop are allowed.
- Every call requires the exact spoken phrase `confirm call` within 30 seconds. Generic yes/confirm responses do not dial. Pending foreground confirmations are cleared when leaving the screen and after shutdown; lock state is checked again at execution.
- All app screens use FLAG_SECURE to restrict screenshots, casting and recent-task previews. Android 12+ hides overlay windows; obscured touches are filtered. These are Android best-effort defenses, not protection from root or a compromised OS.
- Foreground notifications contain only generic listening status, never utterances, numbers or generated answers. Live conversation UI exists only in process memory.
- Private Activities and the microphone service are not exported. Accessibility and notification listener services require Android system binding permissions. Pending intents are immutable and explicit.
- App backup and Android cloud/device transfer are excluded; cleartext traffic is disabled as defense in depth. No unrestricted storage permission or clipboard access.
- Local model import uses the system document picker, app-private storage, space and size limits, a temporary file, GGUF header/version checking and atomic rename. Speech ZIP import rejects path traversal and bounds expanded size and entry count.
- The Gradle wrapper distribution has an official SHA256 pin. Git excludes local SDK paths, IDE caches, environment files, native model downloads, APKs and signing keys.

## Limitations and next work

Voice recognition can mishear and is not speaker verification. Even with confirmation, a replay attack on an unlocked phone remains possible. Device unlock is not per-call biometric authentication. Native GGUF parsing is not sandboxed in a separate process; load only trusted publisher models. Header validation does not prove a model is safe. Third-party native dependencies still need ongoing vulnerability review; no independent penetration test has been performed.

The test APK is debuggable and uses the Android debug key. It is for this personal device. Production distribution requires a private release signing key, a non-debuggable release build, dependency auditing and separate release testing. Never commit that key or its passwords.

Continuous microphone access consumes battery. Android may stop it; this app does not bypass force-stop, device lock or Android foreground-start rules. After reboot/force-stop, open Shoune to resume. A user Stop is always honored rather than immediately restarting covertly.

## Validation

Instrumentation checks default-on initialization/automatic startup, preservation of explicit Stop, FLAG_SECURE, rejection of generic call confirmation and locked-command rejection (injected lock state). Existing Realme command and native model tests remain documented in docs/TESTING.md. Android lint is run with this change. Real microphone recognition, lock-screen behavior and overnight battery reliability still need hands-on testing.

References: https://developer.android.com/security/fraud-prevention/activities and https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
