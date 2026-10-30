package dev.local.assistant.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Local speaker similarity filter. It reduces false wakes; it is not device authentication. */
object SpeakerVerifier {
    private const val MODEL = "speaker/campplus_en.onnx"
    private const val THRESHOLD = 0.58f
    private val lock = Any()
    private var extractor: SpeakerEmbeddingExtractor? = null

    private fun engine(context: Context): SpeakerEmbeddingExtractor = synchronized(lock) {
        extractor ?: SpeakerEmbeddingExtractor(context.assets,
            SpeakerEmbeddingExtractorConfig(model = MODEL, numThreads = 2, debug = false, provider = "cpu"))
            .also { extractor = it }
    }

    private fun voiceprint(context: Context) = File(context.filesDir, "identity/voiceprint.bin")
    fun enrolled(context: Context) = voiceprint(context).isFile

    fun embedding(context: Context, samples: FloatArray): FloatArray {
        require(samples.size >= 24_000) { "Speak for at least 1.5 seconds." }
        return synchronized(lock) {
            val current = engine(context)
            val stream = current.createStream()
            try {
                stream.acceptWaveform(samples, 16_000)
                stream.inputFinished()
                require(current.isReady(stream)) { "The voice sample was too short or unclear." }
                normalize(current.compute(stream))
            } finally { stream.release() }
        }
    }

    fun enroll(context: Context, embeddings: List<FloatArray>) {
        require(embeddings.size >= 3) { "Record three samples." }
        val dimension = embeddings.first().size
        require(dimension > 0 && embeddings.all { it.size == dimension }) { "Voice samples did not match." }
        val average = FloatArray(dimension)
        embeddings.forEach { vector -> vector.indices.forEach { average[it] += vector[it] / embeddings.size } }
        val normalized = normalize(average)
        val target = voiceprint(context)
        target.parentFile?.mkdirs()
        val temporary = File(target.path + ".part")
        temporary.outputStream().use { output ->
            val buffer = ByteBuffer.allocate(8 + normalized.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(0x53485631); buffer.putInt(normalized.size)
            normalized.forEach(buffer::putFloat)
            output.write(buffer.array())
        }
        check(temporary.renameTo(target)) { "Could not save the voice profile." }
    }

    fun verify(context: Context, samples: FloatArray): Boolean = runCatching {
        val enrolled = read(context) ?: return false
        cosine(enrolled, embedding(context, samples)) >= THRESHOLD
    }.getOrDefault(false)

    fun remove(context: Context) { voiceprint(context).delete() }

    private fun read(context: Context): FloatArray? = runCatching {
        val bytes = voiceprint(context).readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.int == 0x53485631)
        val size = buffer.int
        require(size in 1..4096 && bytes.size == 8 + size * 4)
        FloatArray(size) { buffer.float }
    }.getOrNull()

    private fun normalize(input: FloatArray): FloatArray {
        val magnitude = sqrt(input.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1e-8f)
        return FloatArray(input.size) { input[it] / magnitude }
    }
    private fun cosine(a: FloatArray, b: FloatArray): Float = if (a.size != b.size) -1f else a.indices.sumOf { (a[it] * b[it]).toDouble() }.toFloat()
}
