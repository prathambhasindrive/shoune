# Verification and acceptance

## Verified on Realme 3 Pro (RMX1851, Android 11 / API 30)

- Debug app and instrumentation APK build successfully with JDK 17 / SDK 36.
- Upgrade install succeeds without removing application data.
- Device smoke suite passes: bundled Vosk loading, voice-only UI, no INTERNET permission, wake aliases/phrase normalization, time, invalid timers, invalid contact/absent app feedback, installed Settings app launch, flashlight on/off, call confirmation/cancellation, hands-free preference, ongoing service notification and explicit stop.
- Confirmation launch is captured by a test callback, verifying ACTION_CALL and the intended number without placing a real call. No external call was placed by automated testing.
- A synthetic English 16 kHz PCM clip spoken as “Shawn. Turn on the flashlight.” decodes through the phone's actual Vosk engine as “sean turn on the flashlight”. Wake normalization and the command router accept it. This is file-input acoustic testing, not a microphone recording of the user's voice.
- Qwen 2.5 0.5B Q4_K_M loads through the packaged native engine and generates “Hello! How can I assist you today?” on the Realme. The test restores the previous selected-model preference afterward. Test model SHA256: `74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db`, matching the official publisher file.

## Running checks

Build `assembleDebug assembleDebugAndroidTest`, install both APKs, and use the device serial explicitly when multiple devices/emulators are attached:

```powershell
adb -s SERIAL shell am instrument -w dev.local.assistant.test/dev.local.assistant.SmokeInstrumentation
```

The device suite expects microphone, camera and CALL_PHONE grants. It toggles hands-free off at the end and briefly opens Android Settings. Read the PASS/FAIL stream; adb's process exit code alone does not establish test success.

Optional native generation test: place a compatible GGUF at app-private `files/language-models/test__qwen.gguf`, then run the same runner with `-e model true`. Optional audio decoder test: place raw mono 16-bit 16 kHz PCM at app-private `files/test-speech.pcm`, then run with `-e audio true`. These are instrumentation-only paths; production UI has no command-injection endpoint. Do not include personal recordings or model weights in source control.

## Hands-on acceptance still required

1. Say “Shoune” alone, wait for its response, then request a command. Also test one-sentence commands. Check recognition with your accent and ordinary room noise.
2. Test a real contact call deliberately, confirming the displayed/spoken recipient. Automated tests do not place calls.
3. Enable Accessibility, unlock the phone and test opening apps/calling while another app is visible.
4. Enable hands-free, press Home, then turn the screen off and test wake/flashlight. Check extended idle and overnight battery drain on Realme. No overnight endurance claim is made.
5. Confirm Android has an installed offline English TTS voice; the app's Permissions page links to setup. Check replies with internet unavailable.
6. Import a model using the Android file picker, select it, ask a general question and cancel an answer. Native inference is verified, but every third-party GGUF architecture is not.
7. Repeat lifecycle, permissions and audio tests on the later OnePlus device and newer Android releases.

## References

- Android microphone foreground services: https://developer.android.com/develop/background-work/services/fgs/service-types
- Vosk Android: https://alphacephei.com/vosk/android
- Llamatik: https://github.com/ferranpons/Llamatik
- Official tested model: https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/blob/main/qwen2.5-0.5b-instruct-q4_k_m.gguf

OpenDroid/DroidClaw code was not needed for this V1 repair and was not copied.
