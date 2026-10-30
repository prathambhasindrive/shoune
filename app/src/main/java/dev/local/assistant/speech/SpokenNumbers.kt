package dev.local.assistant.speech

import java.util.Locale

/** Bounded English number parsing for durations and digit-by-digit dialing. */
object SpokenNumbers {
    private val small = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val tens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    fun minutes(phrase: String): Int {
        val text = phrase.lowercase(Locale.US).trim().replace('-', ' ')
        if (text.matches(Regex("[0-9]{1,4}"))) return text.toInt()
        if (text == "a" || text == "an") return 1
        var value = 0
        var previous = -1
        var seen = false
        for (token in text.split(Regex("\\s+"))) {
            val unit = small.indexOf(token)
            val ten = tens.indexOf(token)
            val number = if (unit >= 0) unit else if (ten >= 0) (ten + 2) * 10 else return -1
            if (seen && !(previous >= 20 && previous % 10 == 0 && number in 1..9)) return -1
            value += number
            previous = number
            seen = true
        }
        return if (seen) value else -1
    }
    fun phone(phrase: String): String {
        val text = phrase.lowercase(Locale.US).trim()
        if (text.matches(Regex("\\+?[0-9 ()-]+"))) return text.replace(Regex("[ ()-]"), "")
        val digits = StringBuilder()
        for (token in text.split(Regex("\\s+"))) {
            if (token == "plus" && digits.isEmpty()) { digits.append('+'); continue }
            val digit = small.indexOf(if (token == "oh") "zero" else token)
            if (digit !in 0..9) return ""
            digits.append(digit)
        }
        return digits.toString()
    }
}
