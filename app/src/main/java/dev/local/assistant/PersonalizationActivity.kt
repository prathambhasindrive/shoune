package dev.local.assistant

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.inputmethod.EditorInfo
import android.widget.*
import dev.local.assistant.security.DeviceSecurity
import dev.local.assistant.speech.AssistantPreferences

/** Settings may use typing; the assistant command surface itself remains voice-only. */
class PersonalizationActivity : Activity() {
    private var tts: TextToSpeech? = null
    private lateinit var root: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        DeviceSecurity.protect(this)
        renderShell()
        tts = TextToSpeech(this) { result -> runOnUiThread {
            if (result == TextToSpeech.SUCCESS) renderVoices() else status.text = "Android text-to-speech is unavailable."
        } }
    }

    private fun label(value: String, size: Float = 14f) = TextView(this).also {
        it.text = value; it.textSize = size; it.setTextColor(Color.rgb(232, 242, 234)); it.setPadding(0, 12, 0, 12); root.addView(it)
    }

    private fun renderShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32); setBackgroundColor(Color.rgb(16, 27, 23)) }
        label("Voice & identity", 28f)
        label("Wake animation", 18f)
        val animationPrefs = getSharedPreferences("appearance", 0)
        val animationChoices = RadioGroup(this)
        listOf("Pulse", "Sway", "None").forEach { choice ->
            animationChoices.addView(RadioButton(this).apply {
                id = android.view.View.generateViewId()
                text = choice
                isChecked = animationPrefs.getString("wakeAnimation", "Pulse") == choice
                setOnClickListener { animationPrefs.edit().putString("wakeAnimation", choice).apply() }
            })
        }
        root.addView(animationChoices)
        label("What should Shoune call you?", 18f)
        val name = EditText(this).apply {
            setText(AssistantPreferences.commander(this@PersonalizationActivity)); hint = "Commander name"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); imeOptions = EditorInfo.IME_ACTION_DONE
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        root.addView(name)
        root.addView(Button(this).apply { text = "Save name"; isAllCaps = false; setOnClickListener {
            status.text = if (AssistantPreferences.setCommander(this@PersonalizationActivity, name.text.toString()))
                "Saved. Shoune will answer: Yes ${AssistantPreferences.commander(this@PersonalizationActivity)}."
            else "Use 1–30 English letters, spaces, apostrophes or hyphens."
        } })
        root.addView(Button(this).apply { text = if (dev.local.assistant.speech.SpeakerVerifier.enrolled(this@PersonalizationActivity)) "Retrain my voice" else "Train Shoune for my voice"; isAllCaps = false; setOnClickListener {
            startActivity(android.content.Intent(this@PersonalizationActivity, VoiceEnrollmentActivity::class.java))
        } })
        label("Offline reply voice", 18f)
        label("These voices are installed by Android. Network voices are excluded. Use Preview before choosing.")
        status = label("Loading installed voices…")
        setContentView(ScrollView(this).apply { addView(root) })
        ScreenStyle.apply(this)
    }

    private fun renderVoices() {
        val engine = tts ?: return
        val voices = AssistantPreferences.offlineEnglishVoices(engine)
        if (voices.isEmpty()) { status.text = "No offline English voices found. Install voice data from Permissions."; return }
        status.text = "${voices.size} offline English voices found."
        val group = RadioGroup(this)
        val current = AssistantPreferences.applyVoice(this, engine)?.name
        voices.forEach { voice ->
            val title = listOf(voice.locale.displayCountry.takeIf { it.isNotBlank() }, voice.name).filterNotNull().joinToString(" · ")
            group.addView(RadioButton(this).apply {
                text = title; setTextColor(Color.WHITE); isChecked = voice.name == current
                setOnClickListener {
                    AssistantPreferences.setVoice(this@PersonalizationActivity, voice.name)
                    AssistantPreferences.applyVoice(this@PersonalizationActivity, engine)
                    engine.speak("Hello ${AssistantPreferences.commander(this@PersonalizationActivity)}. This is my voice.", TextToSpeech.QUEUE_FLUSH, null, "voice-preview")
                    status.text = "Selected $title"
                }
            })
        }
        root.addView(group)
        root.addView(Button(this).apply { text = "Back to Shoune"; isAllCaps = false; setOnClickListener { finish() } })
    }

    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }
}
