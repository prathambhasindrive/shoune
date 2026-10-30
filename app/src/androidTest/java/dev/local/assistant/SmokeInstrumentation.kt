package dev.local.assistant
import dev.local.assistant.speech.WakePhrase

import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.File

/** Device smoke checks without external test libraries. No calls are placed. */
class SmokeInstrumentation : Instrumentation() {
    private var testAudio = false
    private var testModel = false
    private var prepareLockTest = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        testAudio = arguments?.getString("audio") == "true"
        testModel = arguments?.getString("model") == "true"
        prepareLockTest = arguments?.getString("lock") == "true"
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var activity: Activity? = null
        try {
            val context = targetContext
            if (prepareLockTest) {
                context.getSharedPreferences("shoune", 0).edit().putBoolean("background", true).commit()
                context.startForegroundService(Intent(context, WakeListeningService::class.java))
                val deadline = SystemClock.elapsedRealtime() + 30000
                while (!WakeListeningService.running && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
                check(WakeListeningService.running) { "Foreground wake service did not start" }
                result.putString("stream", "PASS: production wake service prepared for physical lock test.\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            val listeningPrefs = context.getSharedPreferences("shoune", 0)
            listeningPrefs.edit().remove("defaultListeningV2").putBoolean("background", false).commit()
            dev.local.assistant.speech.ListeningPreferences.initialize(context)
            check(dev.local.assistant.speech.ListeningPreferences.enabled(context)) { "New default should be on" }
            listeningPrefs.edit().putBoolean("background", false).commit()
            dev.local.assistant.speech.ListeningPreferences.initialize(context)
            check(!dev.local.assistant.speech.ListeningPreferences.enabled(context)) { "Explicit Stop must survive reopen" }
            context.stopService(Intent(context, WakeListeningService::class.java))
            waitForIdleSync()
            if (testAudio) {
                val segments = mutableListOf<String>()
                org.vosk.Model(File(context.filesDir, "speech-model").path).use { model ->
                    org.vosk.Recognizer(model, 16000f).use { recognizer ->
                        File(context.filesDir, "test-speech.pcm").inputStream().use { input ->
                            val buffer = ByteArray(4096)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (recognizer.acceptWaveForm(buffer, count)) segments.add(org.json.JSONObject(recognizer.result).optString("text"))
                            }
                        }
                        segments.add(org.json.JSONObject(recognizer.finalResult).optString("text"))
                    }
                }
                val decoded = segments.joinToString(" ").trim()
                check(WakePhrase.command(decoded) != null) { "Wake alias missed: $decoded" }
                check(dev.local.assistant.commands.CommandText.normalize(decoded) == "flashlight on") { "Command missed: $decoded" }
                val pcm = File(context.filesDir, "test-speech.pcm").readBytes()
                val samples = FloatArray(pcm.size / 2) { index ->
                    val low = pcm[index * 2].toInt() and 0xff
                    val high = pcm[index * 2 + 1].toInt()
                    ((high shl 8) or low).toShort() / 32768f
                }
                val embedding = dev.local.assistant.speech.SpeakerVerifier.embedding(context, samples)
                try {
                    dev.local.assistant.speech.SpeakerVerifier.enroll(context, listOf(embedding, embedding, embedding))
                    check(dev.local.assistant.speech.SpeakerVerifier.verify(context, samples)) { "Speaker verification rejected its enrollment sample" }
                } finally { dev.local.assistant.speech.SpeakerVerifier.remove(context) }
                result.putString("stream", "PASS: synthetic 16 kHz speech decoded and CAMPPlus speaker verification executed on device: $decoded\n")
                finish(Activity.RESULT_OK, result)
                return
            }
            if (testModel) {
                val model = File(dev.local.assistant.inference.LocalBrain.directory(context), "test__qwen.gguf")
                check(model.exists()) { "Push test model before running -e model true" }
                fun awaitResult(action: ((Result<String>) -> Unit) -> Unit): String {
                    val latch = java.util.concurrent.CountDownLatch(1)
                    var output: Result<String>? = null
                    action { output = it; latch.countDown() }
                    check(latch.await(180, java.util.concurrent.TimeUnit.SECONDS)) { "Native model timed out" }
                    return output!!.getOrThrow()
                }
                val prefs = context.getSharedPreferences("brain", 0)
                val previous = prefs.getString("selected", null)
                try {
                    awaitResult { dev.local.assistant.inference.LocalBrain.select(context, model, it) }
                    val answer = awaitResult { dev.local.assistant.inference.LocalBrain.answer(context, "Say hello in one short sentence.", it) }
                    check(answer.isNotBlank())
                    result.putString("stream", "PASS: real native model load and generation: $answer\n")
                } finally {
                    awaitResult { dev.local.assistant.inference.LocalBrain.disable(context, it) }
                    prefs.edit().putString("selected", previous).commit()
                }
                finish(Activity.RESULT_OK, result)
                return
            }
            check(dev.local.assistant.commands.CommandText.normalize("Shoune, turn on the flashlight!") == "flashlight on")
            check(dev.local.assistant.commands.CommandText.normalize("Shoune switch the torch off") == "flashlight off")
            check(dev.local.assistant.commands.CommandText.normalize("launch You Tube") == "open you tube")
            check(dev.local.assistant.commands.AppNames.key("You Tube") == dev.local.assistant.commands.AppNames.key("YouTube"))
            var captured: Intent? = null
            var feedback = ""
            val controller = dev.local.assistant.commands.CommandController(context,
                reply = { feedback = it }, launch = { captured = it }, requestPermission = { error("Missing permission $it") }, stop = {})
            runOnMainSync {
                controller.execute("Shoune call one two three four five six seven eight nine zero")
                check(controller.awaitingConfirmation && captured == null)
                controller.execute("yes")
                check(captured == null && controller.awaitingConfirmation) { "Generic yes must not dial" }
                controller.execute("Shoune confirm call")
                check(captured?.action == Intent.ACTION_CALL && captured?.dataString == "tel:1234567890")
                check(!controller.awaitingConfirmation)
                captured = null
                controller.execute("hello shoune play daft punk on spotify")
                check(captured?.dataString?.startsWith("spotify:search:") == true)
                captured = null
                controller.execute("shoune play kotlin tutorial on youtube")
                check(captured?.dataString?.startsWith("https://www.youtube.com/results") == true)
                captured = null
                controller.execute("shoune open instagram profile openai")
                check(captured?.dataString == "https://www.instagram.com/openai/")
                captured = null
                controller.execute("shoune open whatsapp chat with nine one nine eight seven six five four three two one zero")
                check(captured?.dataString == "https://wa.me/919876543210")
                controller.close()
            }
            val lockedController = dev.local.assistant.commands.CommandController(context,
                reply = { feedback = it }, launch = { error("Locked command launched an activity") },
                requestPermission = { error("Locked command requested a permission") }, stop = {}, isUnlocked = { false })
            runOnMainSync {
                for (request in listOf("call one two three", "confirm call", "open settings", "show notifications", "tell me a story")) {
                    lockedController.execute(request)
                    check(feedback.contains("Unlock")) { "Lock protection failed for $request" }
                }
                lockedController.close()
            }
            check(context.applicationInfo.loadLabel(context.packageManager).toString() == "Shoune")
            check(WakePhrase.command("hey shoune volume up") == "volume up")
            check(WakePhrase.command("hello there shoune volume up") == "volume up")
            check(WakePhrase.command("good morning sean") == "")
            check(WakePhrase.command("hey shawn") == "")
            check(WakePhrase.command("sean go home") == "go home")
            check(WakePhrase.command("hey local volume up") == null)
            check(WakePhrase.command("shownothing") == null)
            check(dev.local.assistant.speech.AssistantPreferences.setCommander(context, "Pratham"))
            check(dev.local.assistant.speech.AssistantPreferences.commander(context) == "Pratham")
            check(!dev.local.assistant.speech.AssistantPreferences.setCommander(context, "<script>"))
            check(context.assets.open("speech-model/am/final.mdl").use { it.read() } >= 0) { "Missing bundled speech model" }
            val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            check(info.requestedPermissions.orEmpty().none { it == "android.permission.INTERNET" }) { "Unexpected internet permission" }
            val launched = startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = launched
            waitForIdleSync()
            val deadline = SystemClock.elapsedRealtime() + 90000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                runOnMainSync { ready = views(launched.window.decorView).filterIsInstance<TextView>().any { it.text.toString().startsWith("Ready.") } }
                if (!ready) SystemClock.sleep(250)
            }
            check(ready) { "Offline model did not become ready" }
            check(File(context.filesDir, "speech-model/am/final.mdl").exists()) { "Model not installed" }
            runOnMainSync {
                check(launched.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
                check(launched.findViewById<Button>(R.id.microphone).background is android.graphics.drawable.Animatable)
                // Render only our public icon asset; never capture the protected conversation screen.
                val icon = launched.getDrawable(R.drawable.shoune_mark)!!
                val bitmap = android.graphics.Bitmap.createBitmap(432, 432, android.graphics.Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 432, 432)
                icon.draw(android.graphics.Canvas(bitmap))
                File(context.getExternalFilesDir(null), "shoune-bot.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
                check(views(launched.window.decorView).none { it is EditText }) { "Typed command field remains" }
                check(launched.findViewById<Button>(R.id.microphone).contentDescription == "Start voice conversation")
            }
            fun command(text: String) {
                runOnMainSync {
                    // Inject recognizer output at the command boundary; acoustic accuracy is a device test.
                    MainActivity::class.java.getDeclaredMethod("execute", String::class.java).apply {
                        isAccessible = true
                    }.invoke(launched, text)
                }
                waitForIdleSync()
            }
            fun screenContains(fragment: String): Boolean {
                var found = false
                runOnMainSync { found = views(launched.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains(fragment) } }
                return found
            }
            command("Shoune what time is it")
            check(screenContains(":")) { "Time command did not update status" }
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                context.getSystemService(android.hardware.camera2.CameraManager::class.java).cameraIdList.any {
                    context.getSystemService(android.hardware.camera2.CameraManager::class.java).getCameraCharacteristics(it)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }) {
                try {
                    command("Shoune turn on the flashlight")
                    check(screenContains("flashlight on")) { "Torch on failed" }
                } finally { command("Shoune turn the flashlight off") }
                check(screenContains("flashlight off")) { "Torch off failed" }
            }
            command("set timer for zero minutes")
            check(screenContains("Choose between 1 and 1440")) { "Invalid timer was not rejected" }
            command("call mom")
            check(screenContains("one digit at a time")) { "Unsupported contact was not rejected" }
            command("open app-that-does-not-exist")
            check(screenContains("Found 0 matches")) { "Missing app feedback absent" }
            command("call one two three four five six seven eight nine zero")
            check(screenContains("confirm call")) { "Spoken call confirmation absent" }
            command("cancel")
            check(screenContains("Call cancelled")) { "Call cancellation failed" }
            runOnMainSync { launched.findViewById<android.widget.Switch>(R.id.hands_free).isChecked = true }
            SystemClock.sleep(3000)
            check(context.getSharedPreferences("shoune", 0).getBoolean("background", false))
            check(context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == 41 }) { "Foreground listening notification absent" }
            runOnMainSync { launched.findViewById<android.widget.Switch>(R.id.hands_free).isChecked = false }
            SystemClock.sleep(1000)
            check(!context.getSharedPreferences("shoune", 0).getBoolean("background", true))
            check(context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.id == 41 }) { "Stop did not remove foreground notification" }
            command("Shoune open settings")
            check(screenContains("Opened settings")) { "Installed app launch failed" }
            val permissions = startActivitySync(Intent(context, PermissionsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            runOnMainSync {
                val labels = views(permissions.window.decorView).filterIsInstance<TextView>().joinToString(" ") { it.text }
                check(labels.contains("Shoune permissions") && labels.contains("Accessibility controls") && labels.contains("Background battery access"))
                permissions.finish()
            }
            result.putString("stream", "PASS: persistent explicit stop, secure screen, strict call confirmation, voice-only UI, model loading, time, timer validation, flashlight, call cancellation, app lookup, foreground notification and stop. Default startup and lock survival are verified as device-level checks.\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL: " + error.stackTraceToString())
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            activity?.let { screen -> runOnMainSync { screen.finish() } }
        }
    }

    private fun views(root: View): List<View> = buildList {
        add(root)
        if (root is ViewGroup) for (index in 0 until root.childCount) addAll(views(root.getChildAt(index)))
    }
}
