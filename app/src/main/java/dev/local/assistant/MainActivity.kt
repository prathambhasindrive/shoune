package dev.local.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

class MainActivity : Activity(), RecognitionListener {
    private lateinit var status: TextView
    private lateinit var load: Button
    private var model: Model? = null
    private var speech: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var transcript: TextView
    private lateinit var listeningState: TextView
    private var resumeAfterSpeech = false
    private var visible = false
    private var offlineVoice = false
    @Volatile private var busy = false
    @Volatile private var destroyed = false
    private var acceptingSpeech = false
    private var syncingSwitch = false
    private val worker = Executors.newSingleThreadExecutor()
    private val modelDir get() = File(filesDir, "speech-model")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dev.local.assistant.security.DeviceSecurity.protect(this)
        dev.local.assistant.speech.ListeningPreferences.initialize(this)
        window.statusBarColor = android.graphics.Color.rgb(16, 27, 23)
        window.navigationBarColor = android.graphics.Color.rgb(16, 27, 23)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        transcript = findViewById(R.id.transcript)
        listeningState = findViewById(R.id.listening_state)
        load = findViewById(R.id.settings)
        findViewById<Button>(R.id.local_models).setOnClickListener { startActivity(Intent(this, ModelsActivity::class.java)) }
        findViewById<Button>(R.id.microphone).setOnClickListener {
            if (acceptingSpeech) { stopListening(); show("Paused. Tap when you're ready.") }
            else startListening()
        }
        findViewById<Switch>(R.id.hands_free).setOnCheckedChangeListener { toggle, enabled ->
            if (syncingSwitch) return@setOnCheckedChangeListener
            if (enabled) {
                if (model == null || busy) { toggle.isChecked = false; reply("I'm still getting ready.") }
                else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    syncingSwitch = true; toggle.isChecked = false; syncingSwitch = false
                    getSharedPreferences("shoune", MODE_PRIVATE).edit().putBoolean("audioPermissionAsked", true).apply()
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                } else {
                    stopListening()
                    try {
                        startForegroundService(Intent(this, WakeListeningService::class.java))
                        getSharedPreferences("shoune", MODE_PRIVATE).edit().putBoolean("background", true).apply()
                    } catch (error: Exception) {
                        toggle.isChecked = false
                        reply("Android could not start listening. Check Permissions.")
                        return@setOnCheckedChangeListener
                    }
                    show("Say Shoune, then tell me what you need.")
                    listeningState.text = "Hands-free is on"
                }
            } else {
                getSharedPreferences("shoune", MODE_PRIVATE).edit().putBoolean("background", false).apply()
                stopService(Intent(this, WakeListeningService::class.java))
                listeningState.text = "Tap to speak"
            }
        }
        load.setOnClickListener { startActivity(Intent(this, PermissionsActivity::class.java)) }
        load.setOnLongClickListener {
            AlertDialog.Builder(this).setTitle("Voice settings")
                .setItems(arrayOf("Set up spoken replies", "Permissions and background", "Repair speech model")) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent("com.android.settings.TTS_SETTINGS"))
                        1 -> startActivity(Intent(this, PermissionsActivity::class.java))
                        2 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), IMPORT_MODEL)
                    }
                }.setNegativeButton("Close", null).show()
            true
        }
        tts = TextToSpeech(this) { result ->
            runOnUiThread {
                if (!destroyed && result == TextToSpeech.SUCCESS) {
                    val engine = tts
                    val voice = engine?.voices?.firstOrNull {
                        it.locale.language == "en" && !it.isNetworkConnectionRequired
                    }
                    offlineVoice = voice != null && engine?.setVoice(voice) == TextToSpeech.SUCCESS
                    if (model != null && !acceptingSpeech && !WakeListeningService.running && status.text.toString().startsWith("Ready."))
                        show(if (offlineVoice) "Ready. What can I help you with?" else "Ready. Set up spoken replies in Permissions for an offline English voice.")
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onError(id: String?) { onDone(id) }
            override fun onDone(id: String?) { runOnUiThread {
                if (resumeAfterSpeech && visible && !destroyed) {
                    resumeAfterSpeech = false
                    startListening()
                }
            } }
        })
        if (File(modelDir, "am/final.mdl").exists()) initializeModel()
        else installBundledModel()
    }

    private fun show(message: String) = runOnUiThread {
        if (!destroyed) status.text = message
    }

    private fun reply(message: String) {
        show(message)
        if (offlineVoice && !destroyed) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "reply")
        else if (resumeAfterSpeech && visible) {
            resumeAfterSpeech = false
            android.os.Handler(mainLooper).postDelayed({ if (visible && !destroyed) startListening() }, 700)
        }
    }

    private fun initializeModel() {
        busy = true
        show("Loading offline recognizer…")
        worker.execute { loadModel() }
    }

    private fun installBundledModel() {
        busy = true
        show("Preparing offline English speech. This happens once…")
        worker.execute {
            val staging = File(filesDir, "bundled-model-install")
            try {
                staging.deleteRecursively()
                copyAssetFolder("speech-model", staging)
                check(File(staging, "am/final.mdl").exists()) { "Speech model not bundled" }
                modelDir.deleteRecursively()
                check(staging.renameTo(modelDir)) { "Could not install bundled model" }
                if (!destroyed) loadModel()
            } catch (error: Exception) {
                busy = false
                show("Import an English Vosk model ZIP to enable offline listening.")
            } finally {
                staging.deleteRecursively()
            }
        }
    }

    private fun copyAssetFolder(path: String, destination: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            assets.open(path).use { input -> destination.outputStream().use { input.copyTo(it) } }
        } else {
            destination.mkdirs()
            children.forEach { copyAssetFolder("$path/$it", File(destination, it)) }
        }
    }

    // Run only on the single model worker; publish native model ownership on the UI thread.
    private fun loadModel() {
        try {
            val loaded = Model(modelDir.absolutePath)
            runOnUiThread {
                if (destroyed) loaded.close()
                else {
                    model = loaded
                    show(if (offlineVoice) "Ready. What can I help you with?" else "Ready. Add an offline English voice in Settings for spoken replies.")
                }
                busy = false
                resumeDefaultListening()
            }
        } catch (error: Exception) {
            busy = false
            show("Model failed to load. Import a valid Vosk English ZIP.")
        }
    }

    @Deprecated("Uses the platform document picker for this initial milestone")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != IMPORT_MODEL || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (busy || model != null) {
            reply("Restart the app before replacing a loaded model.")
            return
        }
        busy = true
        load.isEnabled = false
        show("Importing speech model…")
        worker.execute {
            val staging = File(filesDir, "model-import")
            try {
                staging.deleteRecursively()
                check(staging.mkdirs()) { "Could not create import folder" }
                var total = 0L
                val input = contentResolver.openInputStream(uri) ?: throw IOException("Cannot open ZIP")
                ZipInputStream(input).use { zip ->
                    val buffer = ByteArray(16384)
                    var entries = 0
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        check(++entries <= 10000) { "Too many ZIP entries" }
                        val out = File(staging, entry.name)
                        check(out.canonicalPath.startsWith(staging.canonicalPath + File.separator)) { "Unsafe ZIP path" }
                        if (entry.isDirectory) { out.mkdirs(); continue }
                        out.parentFile?.mkdirs()
                        out.outputStream().use { output ->
                            while (true) {
                                val count = zip.read(buffer)
                                if (count == -1) break
                                total += count
                                check(total <= 300L * 1024 * 1024) { "Model exceeds 300 MB" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                val source = if (File(staging, "am/final.mdl").exists()) staging else {
                    staging.listFiles { file -> file.isDirectory }?.singleOrNull()
                        ?: throw IOException("Expected one model folder")
                }
                check(File(source, "am/final.mdl").exists()) { "Not a Vosk model" }
                Model(source.absolutePath).close()
                modelDir.deleteRecursively()
                check(source.renameTo(modelDir)) { "Could not install model" }
                if (!destroyed) loadModel() else busy = false
            } catch (error: Exception) {
                busy = false
                show("Import failed: " + error.message)
            } finally {
                staging.deleteRecursively()
                runOnUiThread { if (!destroyed) load.isEnabled = true }
            }
        }
    }

    private fun startListening() {
        if (WakeListeningService.running) {
            startService(Intent(this, WakeListeningService::class.java).setAction(WakeListeningService.LISTEN_NOW))
            show("I’m listening…")
            return
        }
        val currentModel = model
        if (busy || currentModel == null) {
            reply("Import the speech model and wait for it to load."); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        stopListening()
        tts?.stop()
        try {
            recognizer = Recognizer(currentModel, 16000f)
            val service = SpeechService(recognizer, 16000f)
            speech = service
            acceptingSpeech = true
            if (service.startListening(this, 10000)) { show("I’m listening…"); listeningState.text = "Listening" }
            else { stopListening(); reply("Could not start the microphone.") }
        } catch (error: Exception) {
            stopListening(); reply("Could not start the microphone.")
        }
    }

    private fun stopListening() {
        acceptingSpeech = false
        if (::listeningState.isInitialized) listeningState.text = "Tap to speak"
        speech?.stop()
        speech?.shutdown()
        speech = null
        recognizer?.close()
        recognizer = null
    }

    private fun recognized(json: String) {
        if (!acceptingSpeech || destroyed) return
        try {
            val text = JSONObject(json).optString("text")
            if (text.isNotEmpty()) { transcript.text = text.replaceFirstChar { it.uppercase() }; stopListening(); execute(text) }
        } catch (error: Exception) {
            stopListening(); reply("Could not understand the command.")
        }
    }

    override fun onPartialResult(hypothesis: String) {
        if (acceptingSpeech) runCatching { JSONObject(hypothesis).optString("partial") }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { transcript.text = it }
    }
    override fun onResult(hypothesis: String) = recognized(hypothesis)
    override fun onFinalResult(hypothesis: String) = recognized(hypothesis)
    override fun onError(error: Exception) {
        if (acceptingSpeech) { stopListening(); reply("Microphone error. Try again.") }
    }
    override fun onTimeout() {
        if (acceptingSpeech) { stopListening(); show("Listening finished. Tap Listen to try again.") }
    }

    private val commands: dev.local.assistant.commands.CommandController by lazy {
        dev.local.assistant.commands.CommandController(this,
            reply = { message -> resumeAfterSpeech = commandsAwaiting(); reply(message) },
            launch = { startActivity(it) },
            requestPermission = { requestPermissions(arrayOf(it), 5) },
            stop = { stopListening() })
    }
    private fun commandsAwaiting(): Boolean = commands.awaitingConfirmation
    private fun execute(raw: String) = commands.execute(raw)

    override fun onResume() {
        super.onResume(); visible = true
        if (android.animation.ValueAnimator.areAnimatorsEnabled())
            (findViewById<Button>(R.id.microphone).background as? android.graphics.drawable.Animatable)?.start()
        dev.local.assistant.speech.VoiceSessionState.observer = { heard, message ->
            if (heard.isNotBlank()) transcript.text = heard
            show(message)
            listeningState.text = if (WakeListeningService.running) "Listening for Shoune" else "Tap to speak"
        }
        if (::status.isInitialized) {
            syncingSwitch = true
            findViewById<Switch>(R.id.hands_free).isChecked = WakeListeningService.running
            syncingSwitch = false
            listeningState.text = if (WakeListeningService.running) "Listening for Shoune" else "Tap to speak"
            resumeDefaultListening()
        }
    }
    override fun onPause() { (findViewById<Button>(R.id.microphone).background as? android.graphics.drawable.Animatable)?.stop(); commands.clearPending(); dev.local.assistant.speech.VoiceSessionState.observer = null; visible = false; resumeAfterSpeech = false; stopListening(); super.onPause() }
    private fun resumeDefaultListening() {
        if (!visible || destroyed || model == null || busy || WakeListeningService.running ||
            !dev.local.assistant.speech.ListeningPreferences.enabled(this)) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            findViewById<Switch>(R.id.hands_free).isChecked = true
        } else {
            val prefs = getSharedPreferences("shoune", MODE_PRIVATE)
            if (!prefs.getBoolean("audioPermissionAsked", false)) {
                prefs.edit().putBoolean("audioPermissionAsked", true).apply()
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            } else show("Allow microphone access in Permissions to start default listening.")
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) resumeDefaultListening()
    }
    override fun onDestroy() {
        destroyed = true
        commands.close()
        stopListening()
        tts?.shutdown()
        val currentModel = model
        model = null
        worker.execute { currentModel?.close() }
        worker.shutdown()
        super.onDestroy()
    }

    companion object { private const val IMPORT_MODEL = 12 }
}

