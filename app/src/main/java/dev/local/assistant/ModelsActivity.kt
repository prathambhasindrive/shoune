package dev.local.assistant
import dev.local.assistant.inference.LocalBrain

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.*

class ModelsActivity : Activity() {
    private lateinit var body: LinearLayout
    private lateinit var status: TextView
    private var lastStatus = "Download once. Keep your answers on your phone."
    private val ink = Color.rgb(237, 242, 229)
    override fun onCreate(state: Bundle?) { super.onCreate(state); dev.local.assistant.security.DeviceSecurity.protect(this); render() }
    override fun onResume() { super.onResume(); render() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(parent: LinearLayout, value: String, size: Float = 14f): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(ink); setPadding(0,dp(8),0,dp(8)); parent.addView(this)
    }
    private fun button(parent: LinearLayout, title: String, action: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; setOnClickListener { action() }; parent.addView(this)
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(14),dp(18),dp(14))
        background = GradientDrawable().apply { setColor(Color.rgb(26,40,33)); cornerRadius = dp(22).toFloat(); setStroke(dp(1), Color.rgb(55,76,60)) }
        body.addView(this, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(18) })
    }
    private fun link(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (e: Exception) { result(Result.failure(IllegalStateException("Install a browser to open model downloads."))) }
    }
    private fun result(value: Result<String>) {
        lastStatus = value.getOrElse { it.message ?: "That operation could not complete." }
        if (!isDestroyed) render()
    }
    private fun render() {
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24),dp(28),dp(24),dp(24)); setBackgroundColor(Color.rgb(16,27,23)) }
        label(body, "SHOUNE / LOCAL INTELLIGENCE", 11f)
        label(body, "A mind of your choosing.", 30f)
        status = label(body, if (LocalBrain.busy()) "Working on your model…" else lastStatus)
        val selected = LocalBrain.selected(this)
        val active = card()
        label(active, "CONNECTED MODEL", 11f)
        label(active, selected?.let { LocalBrain.label(it) } ?: "No model selected", 20f)
        label(active, "Your speech recognizer is already built in. Add a language model here for questions and offline answers. Phone controls continue to work separately.")
        button(active, "Import downloaded GGUF") {
            if (LocalBrain.busy()) return@button
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 70)
        }
        if (selected != null) {
            button(active, "Test selected model") {
                status.text = "Asking your model to introduce itself…"
                LocalBrain.answer(this, "Introduce yourself in one short sentence.", ::result)
            }
            button(active, "Disconnect model") { LocalBrain.disable(this, ::result) }
        }
        if (LocalBrain.busy()) button(active, "Cancel current operation") { LocalBrain.cancel(); status.text = "Cancelling…" }
        label(body, "Choose a starting point", 22f)
        val small = card()
        label(small, "Qwen 2.5 · 0.5B", 22f)
        label(small, "START SMALL • Q4_K_M • about 491 MB", 11f)
        label(small, "The first choice to try on your Realme. Short answers, modest reasoning. Leave at least 1 GB free for the file and import copy.")
        button(small, "Download small model ↗") { link(SMALL_DOWNLOAD) }
        button(small, "Publisher & model license ↗") { link("https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF") }
        val large = card()
        label(large, "Qwen 2.5 · 1.5B", 22f)
        label(large, "LARGER OPTION • Q4_K_M • about 1.1 GB", 11f)
        label(large, "More capable but slower and heavier. Try only after the smaller model works; memory varies by phone. Leave at least 2.5 GB free.")
        button(large, "Download larger model ↗") { link(LARGE_DOWNLOAD) }
        button(large, "Publisher & model license ↗") { link("https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF") }
        label(body, "Your model library", 22f)
        val files = LocalBrain.files(this)
        if (files.isEmpty()) label(body, "Nothing imported yet. Open a download above, then return here and choose Import downloaded GGUF.")
        files.forEach { file ->
            val item = card()
            label(item, LocalBrain.label(file), 18f)
            label(item, "${file.length() / (1024 * 1024)} MB" + if (selected == file) " · Connected" else " · Available")
            button(item, if (selected == file) "Connected" else "Use this model") {
                if (selected == file) return@button
                status.text = "Loading and checking this model…"
                LocalBrain.select(this, file, ::result)
            }
            button(item, "Remove from Shoune") {
                AlertDialog.Builder(this).setTitle("Remove this model?").setMessage("This deletes Shoune's imported copy. The original download stays in Downloads.")
                    .setPositiveButton("Remove") { _, _ -> LocalBrain.remove(this, file, ::result) }.setNegativeButton("Cancel", null).show()
            }
        }
        label(body, "Downloads use your browser and need internet. Shoune itself has no internet permission. Import a single GGUF instruct model up to 2 GB; split models and vision projectors are not supported. Some GGUF architectures may be incompatible. AI answers can be wrong.")
        button(body, "Back to Shoune") { finish() }
        setContentView(ScrollView(this).apply { addView(body) })
    }
    @Deprecated("Platform document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode != 70 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        lastStatus = "Importing model…"
        LocalBrain.importModel(this, uri, { size -> if (!isDestroyed) status.text = "Importing… ${size / (1024 * 1024)} MB" }, ::result)
        render()
    }
    companion object {
        const val SMALL_DOWNLOAD = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf?download=true"
        const val LARGE_DOWNLOAD = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
    }
}
