package dev.local.assistant.inference

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.OpenableColumns
import com.llamatik.library.platform.LlamaBridge
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Serial native-engine ownership. Generated text is never dispatched as a phone action. */
object LocalBrain {
    private val work = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val occupied = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)
    @Volatile private var nativeReady = false
    private var loaded: String? = null
    private val idleRelease = Runnable {
        work.execute {
            // The same serial worker owns inference and release, preventing native use-after-free.
            if (!occupied.get() && nativeReady) {
                runCatching { LlamaBridge.shutdown() }
                nativeReady = false
                loaded = null
            }
        }
    }
    fun directory(context: Context) = File(context.filesDir, "language-models").apply { mkdirs() }
    fun files(context: Context) = directory(context).listFiles()?.filter { it.extension == "gguf" }?.sortedBy { it.name }.orEmpty()
    fun selected(context: Context): File? {
        val name = context.getSharedPreferences("brain", 0).getString("selected", null) ?: return null
        if (name != File(name).name) return null
        return File(directory(context), name).takeIf { it.isFile }
    }
    fun label(file: File) = file.name.substringAfter("__", file.name).removeSuffix(".gguf")
    fun busy() = occupied.get()
    fun cancel() {
        cancelled.set(true)
        if (nativeReady) runCatching { LlamaBridge.nativeCancelGenerate() }
    }
    private fun job(callback: (Result<String>) -> Unit, action: () -> String) {
        if (!occupied.compareAndSet(false, true)) { callback(Result.failure(IllegalStateException("Shoune is still working. Try again shortly."))); return }
        cancelled.set(false)
        main.removeCallbacks(idleRelease)
        work.execute {
            val result = runCatching { action() }
            occupied.set(false)
            main.post { callback(result); main.postDelayed(idleRelease, 120_000) }
        }
    }
    fun importModel(context: Context, uri: Uri, progress: (Long) -> Unit, callback: (Result<String>) -> Unit) {
        val app = context.applicationContext
        job(callback) {
            var name = "Local model.gguf"
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
            require(name.endsWith(".gguf", true)) { "Choose a single .gguf model file, not a ZIP or a speech model." }
            val safeName = name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(100).removeSuffix(".gguf")
            val target = File(directory(app), UUID.randomUUID().toString() + "__" + safeName + ".gguf")
            val temporary = File(target.path + ".part")
            try {
                val available = StatFs(directory(app).path).availableBytes - 128L * 1024 * 1024
                val limit = minOf(available, 2L * 1024 * 1024 * 1024)
                var total = 0L
                var lastProgress = 0L
                app.contentResolver.openInputStream(uri)?.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            check(!cancelled.get()) { "Import cancelled" }
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= limit) { "Not enough free space, or the model exceeds the 2 GB limit for this build." }
                            output.write(buffer, 0, count)
                            if (total - lastProgress >= 8 * 1024 * 1024) {
                                lastProgress = total
                                val size = total
                                main.post { progress(size) }
                            }
                        }
                    }
                } ?: error("Could not open the chosen file")
                require(total > 1024 * 1024) { "This model file is incomplete or invalid." }
                RandomAccessFile(temporary, "r").use {
                    val magic = ByteArray(4); it.readFully(magic)
                    require(String(magic, Charsets.US_ASCII) == "GGUF") { "This is not a GGUF model." }
                    val version = Integer.reverseBytes(it.readInt())
                    require(version in 2..3) { "Unsupported GGUF version." }
                }
                check(temporary.renameTo(target)) { "Could not save the model" }
                "Imported ${label(target)}. Tap Use to connect it to Shoune."
            } finally { temporary.delete() }
        }
    }
    private fun load(file: File) {
        if (loaded == file.path) return
        if (nativeReady) LlamaBridge.shutdown()
        loaded = null
        LlamaBridge.updateGenerateParams(0.4f, 128, 0.9f, 40, 1.1f, 1024, 3, true, false, 128, 0)
        nativeReady = true
        check(LlamaBridge.initGenerateModel(file.path)) { "This model could not load. Choose a smaller, compatible GGUF instruct model." }
        loaded = file.path
    }
    fun select(context: Context, file: File, callback: (Result<String>) -> Unit) {
        val app = context.applicationContext
        job(callback) {
            require(file in files(app)) { "Model not found" }
            load(file)
            check(!cancelled.get()) { "Selection cancelled" }
            app.getSharedPreferences("brain", 0).edit().putString("selected", file.name).apply()
            "${label(file)} is ready for offline answers."
        }
    }
    fun disable(context: Context, callback: (Result<String>) -> Unit) {
        val app = context.applicationContext
        job(callback) {
            if (nativeReady) LlamaBridge.shutdown()
            loaded = null
            app.getSharedPreferences("brain", 0).edit().remove("selected").apply()
            "Local AI disabled. Voice phone controls still work."
        }
    }
    fun remove(context: Context, file: File, callback: (Result<String>) -> Unit) {
        val app = context.applicationContext
        job(callback) {
            require(file in files(app)) { "Model not found" }
            if (loaded == file.path) { LlamaBridge.shutdown(); loaded = null }
            if (selected(app) == file) app.getSharedPreferences("brain", 0).edit().remove("selected").apply()
            check(file.delete()) { "Could not delete model" }
            "Model removed from Shoune. Your original download is unchanged."
        }
    }
    fun answer(context: Context, question: String, callback: (Result<String>) -> Unit) {
        val app = context.applicationContext
        job(callback) {
            val file = selected(app) ?: error("Choose a model on the Local Models page first.")
            load(file)
            check(!cancelled.get()) { "Cancelled" }
            val messages = listOf("system" to "You are Shoune, a concise offline voice assistant. Answer in English in at most three short sentences. You cannot perform phone actions or see the screen. Do not claim to have performed an action.", "user" to question.take(1800))
            val prompt = LlamaBridge.applyChatTemplate(messages, true)
                ?: messages.joinToString("\n") { it.first + ": " + it.second } + "\nassistant: "
            val result = LlamaBridge.generate(prompt)
            check(!cancelled.get()) { "Cancelled" }
            result.trim().takeIf { it.isNotBlank() } ?: error("The model returned no answer. Try a different instruct model.")
        }
    }
}
