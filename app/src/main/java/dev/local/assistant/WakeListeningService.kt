package dev.local.assistant
import dev.local.assistant.speech.WakePhrase
import dev.local.assistant.speech.OfflineSpeechLoop
import dev.local.assistant.speech.SpeakerVerifier

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import java.io.File
import java.util.concurrent.Executors

/** User-started microphone service. Software wake phrase, not a low-power DSP hotword. */
class WakeListeningService : Service(), RecognitionListener {
    private val worker = Executors.newSingleThreadExecutor()
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speech: OfflineSpeechLoop? = null
    private var tts: TextToSpeech? = null
    private var offlineVoice = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var speaking = false
    private var utterance = ""
    private var micRetries = 0
    @Volatile private var closed = false
    private var lastCommandAt = 0L
    @Volatile private var awakeUntil = 0L
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Voice listening", NotificationManager.IMPORTANCE_LOW))
        startForeground(ID, notification("Starting offline wake listening…"))
        running = true
        wakeLock = getSystemService(android.os.PowerManager::class.java)
            .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Shoune:VoiceListening").apply { acquire() }
        tts = TextToSpeech(this) { result ->
            android.os.Handler(mainLooper).post {
                if (!closed && result == TextToSpeech.SUCCESS) {
                    val engine = tts
                    val voice = engine?.let { dev.local.assistant.speech.AssistantPreferences.applyVoice(this, it) }
                    offlineVoice = voice != null
                }
            }
        }
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) = Unit
            override fun onError(id: String?) = onDone(id)
            override fun onDone(id: String?) {
                main.postDelayed({
                    if (!closed && id == utterance) {
                        // Discard our spoken reply before accepting the user's next utterance.
                        speech?.reset()
                        speaking = false
                        speech?.setPause(false)
                        if (awakeUntil != 0L) awakeUntil = android.os.SystemClock.elapsedRealtime() + 12000
                    }
                }, 250)
            }
        })
        worker.execute {
            try {
                val loaded = Model(File(filesDir, "speech-model").absolutePath)
                android.os.Handler(mainLooper).post {
                    if (closed) loaded.close() else {
                        model = loaded
                        try {
                            recognizer = Recognizer(loaded, 16000f)
                            check(startSpeechLoop())
                            update("Listening for Shoune")
                        } catch (error: Exception) { update("Microphone unavailable. Stop and try again."); stopSelf() }
                    }
                }
            } catch (error: Exception) { stopSelf() }
        }
    }

    private fun startSpeechLoop(): Boolean {
        val current = recognizer ?: return false
        speech?.shutdown()
        val loop = OfflineSpeechLoop(this, current, { json, audio ->
            val text = runCatching { JSONObject(json).optString("text") }.getOrDefault("")
            val candidate = text.isNotBlank() && (WakePhrase.command(text) != null || android.os.SystemClock.elapsedRealtime() <= awakeUntil)
            if (candidate && (!SpeakerVerifier.enrolled(this) || SpeakerVerifier.verify(this, audio)))
                main.post { handle(json) }
        }, { failure -> main.post { onError(failure) } })
        speech = loop
        return loop.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            getSharedPreferences("shoune", MODE_PRIVATE).edit().putBoolean("background", false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == LISTEN_NOW) {
            awakeUntil = android.os.SystemClock.elapsedRealtime() + 12000
            respond("Yes ${dev.local.assistant.speech.AssistantPreferences.commander(this)}")
        }
        return START_STICKY
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, WakeListeningService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Shoune is listening")
            // Other notification listeners must not receive commands, contact numbers or model answers.
            .setContentText("Listening for Shoune · Tap to open").setContentIntent(open).setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Shoune is listening")
                .setContentText("Unlock to see details").build())
            .addAction(Notification.Action.Builder(null, "Stop listening", stop).build()).build()
    }

    private fun update(message: String) {
        if (!closed) {
            getSystemService(NotificationManager::class.java).notify(ID, notification(message))
            dev.local.assistant.speech.VoiceSessionState.publish(message)
        }
    }

    private fun respond(message: String) {
        update(message)
        if (closed) return
        if (offlineVoice) {
            speaking = true
            speech?.setPause(true)
            utterance = "reply-" + android.os.SystemClock.elapsedRealtimeNanos()
            if (tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, utterance) == TextToSpeech.ERROR) {
                speaking = false
                speech?.setPause(false)
            }
        }
    }

    private fun handle(hypothesis: String) {
        if (closed || speaking || tts?.isSpeaking == true) return
        val text = runCatching { JSONObject(hypothesis).optString("text") }.getOrDefault("")
        if (text.isBlank()) return
        val now = android.os.SystemClock.elapsedRealtime()
        val wakeCommand = WakePhrase.command(text)
        if (wakeCommand == null && now > awakeUntil) return
        if (wakeCommand == "") {
            awakeUntil = now + 12000
            respond("Yes ${dev.local.assistant.speech.AssistantPreferences.commander(this)}")
            return
        }
        if (now - lastCommandAt < 500) return
        lastCommandAt = now
        val command = wakeCommand ?: text.trim()
        awakeUntil = 0
        dev.local.assistant.speech.VoiceSessionState.publish("Heard you", if (dev.local.assistant.security.DeviceSecurity.unlocked(this)) text else "")
        commands.execute(command)
        if (commands.awaitingConfirmation) awakeUntil = now + 30000
    }

    private val commands by lazy {
        dev.local.assistant.commands.CommandController(this,
            reply = { respond(it) },
            launch = { intent ->
                // Accessibility is an Android-approved context for user-requested background navigation.
                val access = PhoneAccessibilityService.current
                    ?: throw IllegalStateException("Enable Shoune accessibility in Permissions for background app opening and calls")
                check(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "Unlock your phone first" }
                access.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            requestPermission = { respond("Open Shoune Permissions and allow ${it.substringAfterLast('.').lowercase()}") },
            stop = {
                getSharedPreferences("shoune", MODE_PRIVATE).edit().putBoolean("background", false).apply()
                stopSelf()
            })
    }

    override fun onPartialResult(hypothesis: String) = Unit
    override fun onResult(hypothesis: String) { micRetries = 0; handle(hypothesis) }
    override fun onFinalResult(hypothesis: String) = handle(hypothesis)
    override fun onError(exception: Exception) {
        if (closed) return
        speech?.stop()
        update("Microphone interrupted. Retrying…")
        main.postDelayed({
            if (!closed) {
                if (++micRetries <= 3 && runCatching { startSpeechLoop() }.getOrDefault(false))
                    update("Listening for Shoune")
                else stopSelf()
            }
        }, 1500)
    }
    override fun onTimeout() = Unit
    override fun onDestroy() {
        closed = true
        main.removeCallbacksAndMessages(null)
        running = false
        dev.local.assistant.speech.VoiceSessionState.publish("Listening stopped. Turn on Listen for Shoune to resume.")
        commands.close()
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        speech?.stop(); speech?.shutdown(); speech = null
        recognizer?.close(); recognizer = null
        tts?.shutdown()
        val current = model; model = null
        worker.execute { current?.close() }; worker.shutdown()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
            private set
        const val LISTEN_NOW = "dev.local.assistant.LISTEN_NOW"
        private const val CHANNEL = "wake-listening"
        private const val ID = 41
        private const val STOP = "dev.local.assistant.STOP_LISTENING"
    }
}
