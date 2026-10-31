package dev.local.assistant.speech

object WakePhrase {
    // The bundled English model transcribes the custom name phonetically.
    private val name = Regex("(?:^|\\s)(?:hey|ok|okay)\\s+(?:shoune|shawn|sean|shown|shone)(?:\\s+|$)")
    fun command(text: String): String? {
        val normalized = text.lowercase(java.util.Locale.US).replace(Regex("[,.!?]"), " ").replace(Regex("\\s+"), " ").trim()
        val match = name.find(normalized) ?: return null
        return normalized.substring(match.range.last + 1).trim()
    }
}
