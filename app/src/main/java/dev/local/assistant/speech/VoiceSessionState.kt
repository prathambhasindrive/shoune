package dev.local.assistant.speech

/** Main-thread-only, in-memory status. No recordings or conversation history are persisted. */
object VoiceSessionState {
    var heard = ""
        private set
    var status = ""
        private set
    var observer: ((String, String) -> Unit)? = null
    fun publish(message: String, transcript: String = heard) {
        heard = transcript
        status = message
        observer?.invoke(heard, status)
    }
}
