package dev.local.assistant.speech

import android.content.Context

/** One-time upgrade to the requested default. A subsequent explicit Stop is always respected. */
object ListeningPreferences {
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("shoune", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("defaultListeningV2", false))
            prefs.edit().putBoolean("background", true).putBoolean("defaultListeningV2", true).apply()
    }
    fun enabled(context: Context) = context.getSharedPreferences("shoune", Context.MODE_PRIVATE).getBoolean("background", true)
}
