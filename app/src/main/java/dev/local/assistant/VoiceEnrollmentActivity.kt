package dev.local.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.*
import dev.local.assistant.security.DeviceSecurity
import dev.local.assistant.speech.SpeakerVerifier
import java.util.concurrent.Executors

class VoiceEnrollmentActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val embeddings = mutableListOf<FloatArray>()
    private lateinit var status: TextView
    private lateinit var record: Button

    override fun onCreate(state: Bundle?) {
        super.onCreate(state); DeviceSecurity.protect(this)
        stopService(Intent(this, WakeListeningService::class.java))
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(36, 52, 36, 36); setBackgroundColor(Color.rgb(16,27,23)) }
        fun label(text: String, size: Float = 15f) = TextView(this).also { it.text=text; it.textSize=size; it.setTextColor(Color.WHITE); it.setPadding(0,12,0,12); root.addView(it) }
        label("Recognize my voice", 28f)
        label("Record three different phrases in a quiet room. Samples are converted to one local voiceprint and the raw audio is discarded. This reduces false wakes; recordings or similar voices can still fool it, so it never unlocks Android or authorizes calls.")
        status = label(if (SpeakerVerifier.enrolled(this)) "A voice profile is active." else "No voice profile yet.")
        record = Button(this).apply { text = "Record sample 1 of 3"; isAllCaps = false; setOnClickListener { capture() } }; root.addView(record)
        root.addView(Button(this).apply { text = "Remove voice profile"; isAllCaps = false; setOnClickListener {
            SpeakerVerifier.remove(this@VoiceEnrollmentActivity); embeddings.clear(); status.text="Voice matching is off."; record.text="Record sample 1 of 3"
        } })
        root.addView(Button(this).apply { text="Back"; isAllCaps=false; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        ScreenStyle.apply(this)
    }

    private fun capture() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 31); return
        }
        record.isEnabled = false; status.text = "Speak naturally for four seconds…"
        worker.execute {
            val result = runCatching {
                val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 8192))
                val data = FloatArray(16_000 * 4); val buffer = ShortArray(2048); var offset = 0
                try {
                    audio.startRecording(); check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    while (offset < data.size) {
                        val count = audio.read(buffer, 0, minOf(buffer.size, data.size - offset)); check(count > 0)
                        for (index in 0 until count) data[offset++] = buffer[index] / 32768f
                    }
                } finally { runCatching { audio.stop() }; audio.release() }
                SpeakerVerifier.embedding(this, data)
            }
            runOnUiThread {
                result.onSuccess { vector ->
                    embeddings.add(vector)
                    if (embeddings.size == 3) {
                        runCatching { SpeakerVerifier.enroll(this, embeddings) }
                            .onSuccess { status.text="Voice profile saved. Reopen Shoune to resume listening." }
                            .onFailure { status.text=it.message ?: "Enrollment failed." }
                        record.text="Record again"; embeddings.clear()
                    } else {
                        status.text="Sample ${embeddings.size} saved. Change the phrase slightly."
                        record.text="Record sample ${embeddings.size + 1} of 3"
                    }
                }.onFailure { status.text=it.message ?: "Could not analyze that sample." }
                record.isEnabled = true
            }
        }
    }

    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
