# Shoune

Personal Kotlin Android voice assistant for unrooted Android 8+ devices. Active source: `E:\Projects\Assistant`. Open this directory in Android Studio.

## Start using it

1. Open **Permissions** and allow microphone, flashlight/camera and phone calls. Contacts are optional for name-based calls. Enable Shoune Accessibility for background app opening, calls and screen navigation. Notification access is only needed for notification counts.
2. Install an offline English TTS voice through Android Settings if spoken replies are unavailable. Vosk English speech recognition is bundled with the APK.
3. **Listen for Shoune is on by default** after microphone permission and speech-model initialization. This upgrade enables the default once; a later explicit Stop is remembered. Say **Shoune**, wait for the listening reply, then speak; or say the name and command in one sentence. “Hey” is optional.
4. Home/other apps leave the foreground microphone service running. Use **Stop listening** in its notification, turn off the switch, or say **Shoune stop listening** to stop. On Realme, allow background activity and unrestricted battery use in system settings.

## Commands

- “Shoune turn on the flashlight”; “Shoune turn the torch off”.
- “Shoune open YouTube”; “launch Calculator”. App labels are matched without spaces/punctuation, but ambiguous names are rejected.
- “Shoune call” followed by individual digits or an exact contact name. Say exactly “confirm call” or “cancel” after the reply. A generic “yes” does not dial. Multiple contact numbers require a number rather than a guessed choice. No call is placed before confirmation.
- “Shoune volume up”, “what time is it”, “set timer for five minutes”.
- “Shoune go home”, “go back”, “recent apps”, “scroll down”, “quick settings”, “show notifications”. Accessibility must be enabled.
- “Shoune how many notifications” with notification access enabled.
- “Shoune open local models”, “open permissions”, “cancel answer”.

Commands work without an LLM. Conversational answers require a separately selected model. This V1 uses a bounded command vocabulary; arbitrary natural-language phone workflows are not implemented.

## Local models

The separate **Local models** page contains official Qwen GGUF download and license links. Download in your browser, return to Shoune, choose **Import downloaded GGUF**, then **Use this model**. The app validates the file and loads it through Llamatik/llama.cpp. Start with Qwen 2.5 0.5B Q4_K_M (491 MB). The 1.5B option needs more memory and is not performance-tested here. User-selected single GGUF instruct files are supported up to 2 GB, subject to engine compatibility. Model files are not included in the APK.

Imports copy into private app storage; removing an imported model does not delete the browser download. Text generation stays local, is limited to short responses, and never dispatches device actions. Shoune has no INTERNET permission. Browser downloads and launched third-party apps have their own network behavior.

## Build and verification

Use JDK 17, the included Gradle 8.13 wrapper, Android Gradle Plugin 8.13.2, Kotlin 2.2.21 and SDK 36. Target SDK remains 35; minimum SDK is 26.

```powershell
$env:JAVA_HOME=(Get-ChildItem '.tools\jdk17' -Directory | Select-Object -First 1).FullName
.\gradlew.bat assembleDebug assembleDebugAndroidTest --max-workers=2
```

The bundled `.tools` JDK is local development setup, not part of the app. Any compatible JDK 17 can replace it. APK: `app/build/outputs/apk/debug/app-debug.apk`.

See `docs/ARCHITECTURE.md` for extension points and `docs/TESTING.md` for test commands, evidence and remaining acceptance checks.

## Platform limits

Background listening uses a visible microphone foreground service and a partial wake lock, with battery cost. This is software recognition, not a dedicated low-power hardware hotword. Android/Realme can terminate it; force-stop and reboot require reopening the app. Do not describe it as guaranteed 24/7 availability. Background app launches/calls require enabled Accessibility and an unlocked phone. While locked, only time, flashlight and stopping are accepted; private model answers and notification controls are blocked. The app cannot bypass the lock screen or obtain root-only controls.

The English recognizer maps Shoune to phonetic aliases such as Shawn, Sean or shown. Your pronunciation/noise conditions still need hands-on testing; similarly pronounced speech can cause false wakes.

## Security and bot icon

See SECURITY.md for the threat model and current limitations. App screens block screenshots and non-secure display capture; Android 12+ overlays are hidden and obscured touches are filtered. Backups/device transfer are disabled. Notification text contains no conversations or phone numbers. The Gradle distribution is SHA256-pinned.

The circular mint-and-gold robot is the launcher icon and the home voice button. Its eyes blink inside the app while the activity is visible; Android launcher icons use the static artwork. System-disabled animations are respected.

Source repository: https://github.com/prathambhasindrive/shoune
