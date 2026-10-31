package dev.local.assistant.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.vosk.Recognizer
import java.util.concurrent.atomic.AtomicBoolean

/** One microphone owner feeds both Vosk and optional local speaker verification. */
class OfflineSpeechLoop(
    private val context: Context,
    private val recognizer: Recognizer,
    private val result: (String, FloatArray) -> Unit,
    private val error: (Exception) -> Unit,
    private val partial: (String) -> Unit = {}
) {
    private val active = AtomicBoolean(false)
    @Volatile private var paused = false
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null

    fun start(): Boolean {
        if (!active.compareAndSet(false, true)) return false
        return runCatching {
            check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "Microphone permission is required"
            }
            val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 8192)).also { it.startRecording() }
            check(recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone did not start" }
            thread = Thread(::loop, "ShouneSpeechLoop").also { it.start() }
            true
        }.getOrElse { active.set(false); error(Exception(it)); false }
    }

    private fun loop() {
        val buffer = ShortArray(2048)
        val utterance = FloatArray(16_000 * 15)
        var used = 0
        try {
            while (active.get()) {
                val count = recorder?.read(buffer, 0, buffer.size) ?: break
                if (count <= 0 || paused) continue
                for (index in 0 until count) {
                    if (used < utterance.size) utterance[used++] = buffer[index] / 32768f
                }
                if (recognizer.acceptWaveForm(buffer, count)) {
                    val audio = utterance.copyOf(used)
                    used = 0
                    result(recognizer.result, audio)
                } else partial(recognizer.partialResult)
            }
        } catch (failure: Exception) { if (active.get()) error(failure) }
    }

    fun setPause(value: Boolean) { paused = value }
    fun reset() { recognizer.reset() }
    fun stop() { active.set(false); runCatching { recorder?.stop() } }
    fun shutdown() { stop(); thread?.join(1500); recorder?.release(); recorder = null; thread = null }
}
