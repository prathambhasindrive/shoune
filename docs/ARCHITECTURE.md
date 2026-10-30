# Architecture and extension points

`app/src/main/java/dev/local/assistant/` contains stable Android component entry points. Their class names remain unchanged so existing Accessibility and notification settings survive updates.

| Location | Responsibility |
| --- | --- |
| `MainActivity.kt` | Home UI, speech-model installation, push-to-talk lifecycle and permission entry points |
| `WakeListeningService.kt` | Foreground microphone lifetime, wake window, TTS echo suppression, notification and stop action |
| `commands/CommandText.kt` | Pure deterministic normalization of recognized utterances |
| `commands/CommandController.kt` | Shared foreground/background command routing, permissions, call confirmation and model fallback |
| `commands/AppNames.kt` | Stable app-label comparison without speculative fuzzy matching |
| `speech/WakePhrase.kt` | Optional Hey and phonetic wake-name aliases |
| `speech/SpokenNumbers.kt` | Bounded duration and digit-by-digit phone parsing |
| `speech/VoiceSessionState.kt` | Main-thread-only live status; no conversation persistence |
| `inference/LocalBrain.kt` | Serialized model import/load/generation/unload and cancellation |
| `ModelsActivity.kt` | Model catalogue, external downloads, SAF import and local model library |
| `PermissionsActivity.kt` | Permission status and Android settings links |
| `PhoneAccessibilityService.kt` | Android-protected global navigation and scroll actions |
| `ShouneNotificationService.kt` | Android-protected notification counts |
| `res/` | XML home UI, adaptive launcher icon, styles and service metadata |
| `src/androidTest/` | Device command, lifecycle, speech decode and native inference checks |

## Add a command

Add conservative spoken aliases in `CommandText`. Add the action once in `CommandController`; both microphone paths use it. If it opens an Android activity, call the injected `launch` function. Activity mode launches directly; background mode launches through the enabled Accessibility service and checks the keyguard. Preserve explicit confirmation for calling. Add an instrumentation assertion with a captured launch callback so tests do not dial real numbers.

Keep model output separate from action dispatch. A conversational answer must never be reinterpreted as a trusted command.

## Microphone and threading

Push-to-talk owns a Vosk recognizer only when the background service is inactive. With hands-free active, the home orb requests a wake window from that service. Enabling hands-free releases the Activity recorder before starting the service. Background TTS pauses and resets recognition to prevent the reply becoming the next command. The native Vosk and LLM model loading runs off the main thread. Android UI/status callbacks return to the main thread. Shutdown releases microphone, recognizer, model and wake lock.

LLM operations run on a serial executor. Imported files are copied to a temporary file, bounded by space/size, checked for a GGUF header/version and atomically renamed. Native loading is the compatibility check. The native library may still reject unsupported or damaged models. No network code or remote model server is used.

## Dependencies

Vosk Android 0.3.47 provides offline English recognition; the bundled model is Vosk small English 0.15. Llamatik Android 1.7.0 packages llama.cpp for GGUF inference. Android installed offline TTS supplies spoken replies. Review dependency and model licenses before redistributing this personal-use build.

Security helpers live in `security/DeviceSecurity.kt`. Default-on migration and persisted stop semantics live in `speech/ListeningPreferences.kt`. The bot vector is shared between the static adaptive launcher icon and animated in-app voice button.
