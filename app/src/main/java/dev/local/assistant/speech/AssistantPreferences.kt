package dev.local.assistant.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** Personalization shared by the foreground activity and wake-listening service. */
object AssistantPreferences {
    private const val STORE = "assistant_personalization"
    private const val COMMANDER = "commander_name"
    private const val VOICE = "tts_voice"

    fun commander(context: Context): String =
        context.getSharedPreferences(STORE, 0).getString(COMMANDER, "Pratham") ?: "Pratham"

    fun setCommander(context: Context, raw: String): Boolean {
        val value = raw.trim().replace(Regex("\\s+"), " ")
        if (!value.matches(Regex("[A-Za-z][A-Za-z '-]{0,29}"))) return false
        context.getSharedPreferences(STORE, 0).edit().putString(COMMANDER, value).apply()
        return true
    }

    fun offlineEnglishVoices(engine: TextToSpeech): List<Voice> = engine.voices.orEmpty()
        .filter { it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired }
        .sortedWith(compareBy<Voice>({ it.locale.displayCountry }, { it.name }))

    fun applyVoice(context: Context, engine: TextToSpeech): Voice? {
        val voices = offlineEnglishVoices(engine)
        val saved = context.getSharedPreferences(STORE, 0).getString(VOICE, null)
        val selected = voices.firstOrNull { it.name == saved } ?: voices.firstOrNull()
        return selected?.takeIf { engine.setVoice(it) == TextToSpeech.SUCCESS }
    }

    fun setVoice(context: Context, name: String) {
        context.getSharedPreferences(STORE, 0).edit().putString(VOICE, name).apply()
    }
}
