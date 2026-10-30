package dev.local.assistant
import dev.local.assistant.speech.SpokenNumbers
fun main() {
    check(SpokenNumbers.minutes("five") == 5)
    check(SpokenNumbers.minutes("twenty five") == 25)
    check(SpokenNumbers.minutes("ninety-nine") == 99)
    check(SpokenNumbers.minutes("1440") == 1440)
    check(SpokenNumbers.minutes("one two") == -1)
    check(SpokenNumbers.minutes("twenty twenty") == -1)
    check(SpokenNumbers.minutes("") == -1)
    check(SpokenNumbers.minutes("zero") == 0)
    check(SpokenNumbers.phone("plus nine one nine eight seven six five four three two one zero") == "+919876543210")
    check(SpokenNumbers.phone("nine oh one") == "901")
    check(SpokenNumbers.phone("+91 (987) 654-3210") == "+919876543210")
    check(SpokenNumbers.phone("mom").isEmpty())
    println("12 Kotlin spoken-number checks passed")
}
