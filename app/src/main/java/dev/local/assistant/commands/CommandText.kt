package dev.local.assistant.commands

import dev.local.assistant.speech.WakePhrase
import java.util.Locale

/** Deterministic aliases only: model-generated answers never become device commands. */
object CommandText {
    fun normalize(raw: String): String {
        val clean = raw.lowercase(Locale.US).replace(Regex("[,.!?]"), " ").trim()
            .replace(Regex("\\s+"), " ")
        val text = (WakePhrase.command(clean) ?: clean)
            .removePrefix("please ").removeSuffix(" please").trim()
        val light = Regex("^(?:turn |switch )?(on|off) (?:the )?(?:flash ?light|torch)$").matchEntire(text)
            ?: Regex("^(?:turn |switch )?(?:the )?(?:flash ?light|torch) (on|off)$").matchEntire(text)
        if (light != null) return "flashlight ${light.groupValues[1]}"
        return when {
            text.startsWith("launch ") -> "open " + text.removePrefix("launch ")
            text.startsWith("open the ") -> "open " + text.removePrefix("open the ").removeSuffix(" app")
            text.startsWith("open ") -> text.removeSuffix(" app")
            text.startsWith("dial ") -> "call " + text.removePrefix("dial ")
            text == "increase volume" || text == "turn up the volume" -> "volume up"
            text == "decrease volume" || text == "turn down the volume" -> "volume down"
            text == "resume music" || text == "play music" -> "media play"
            text == "pause music" || text == "stop music" -> "media pause"
            text == "next song" || text == "skip song" -> "media next"
            text == "previous song" -> "media previous"
            else -> text
        }
    }
}
