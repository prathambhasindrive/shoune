package dev.local.assistant.commands

import java.util.Locale

/** Accommodates recognizer spacing ("you tube", "whats app") without guessing an app. */
object AppNames {
    fun key(label: String): String = label.lowercase(Locale.US).filter { it.isLetterOrDigit() }
}
