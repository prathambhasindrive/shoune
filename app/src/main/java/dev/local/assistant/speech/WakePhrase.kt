package dev.local.assistant.speech

object WakePhrase {
    // The bundled English model transcribes the custom name phonetically.
    private val prefix = Regex("^(?:hey )?(?:shoune|shawn|sean|shown|shone)(?:\\s+|$)")
    fun command(text: String): String? {
        val normalized = text.lowercase(java.util.Locale.US).trim()
        val match = prefix.find(normalized) ?: return null
        return normalized.substring(match.range.last + 1).trim()
    }
}
