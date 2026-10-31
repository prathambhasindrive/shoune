package dev.local.assistant.commands
import dev.local.assistant.inference.LocalBrain
import dev.local.assistant.speech.SpokenNumbers

import dev.local.assistant.*
import android.content.Context
import android.content.ContextWrapper
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import java.text.DateFormat
import java.util.Date
import java.net.URLEncoder


/** Shared command policy for foreground and background speech; all calls require confirmation. */
class CommandController(
    context: Context,
    private val reply: (String) -> Unit,
    private val launch: (Intent) -> Unit,
    private val requestPermission: (String) -> Unit,
    private val stop: () -> Unit,
    private val isUnlocked: () -> Boolean = { dev.local.assistant.security.DeviceSecurity.unlocked(context) }
) : ContextWrapper(context) {
    private var pendingCall: String? = null
    private var callDeadline = 0L
    var awaitingConfirmation = false
        private set
    private var closed = false
    fun clearPending() { pendingCall = null; awaitingConfirmation = false }
    fun close() { closed = true; clearPending() }
    override fun startActivity(intent: Intent) = launch(intent)
    fun execute(raw: String) {
        if (closed) return
        val text = CommandText.normalize(raw)
        CommandHistory.add(this, raw)
        try {
            // While locked, permit only torch, time and stopping. Never speak private model answers.
            if (!isUnlocked() &&
                text !in setOf("flashlight on", "flashlight off", "time", "what time is it", "stop", "stop listening", "media play", "media pause", "media next", "media previous")) {
                clearPending(); reply("Unlock your phone to use this command."); return
            }
            pendingCall?.let { number ->
                if (android.os.SystemClock.elapsedRealtime() > callDeadline) {
                    pendingCall = null; awaitingConfirmation = false; reply("That call request expired. Please ask again."); return
                }
                when (text) {
                    "confirm call" -> {
                        pendingCall = null
                        awaitingConfirmation = false
                        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number)))
                        reply("Calling"); return
                    }
                    "cancel", "no", "stop" -> { pendingCall = null; awaitingConfirmation = false; reply("Call cancelled."); return }
                    else -> { awaitingConfirmation = true; reply("Say confirm call or cancel."); return }
                }
            }
            awaitingConfirmation = false
            DeviceControls.execute(this, text, launch)?.let { reply(it); return }
            when {
                text in listOf("features", "what can you do", "tell me your features", "help") -> reply(Features.spoken)
                text == "open features" -> startActivity(Intent(this, FeaturesActivity::class.java))
                text.matches(Regex("open youtube in (brave|chrome) and play .+")) -> {
                    val browser = if (text.contains(" in brave ")) "com.brave.browser" else "com.android.chrome"
                    val query = text.substringAfter(" and play ")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query,"UTF-8"))).setPackage(browser))
                    reply("Opening YouTube results in ${if (browser.contains("brave")) "Brave" else "Chrome"}. Select a result to play.")
                }
                text.matches(Regex("open chatgpt in (chrome|brave)( and .+)?")) -> {
                    val browser = if (text.contains("in brave")) "com.brave.browser" else "com.android.chrome"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")).setPackage(browser))
                    reply("Opening ChatGPT. Once it loads, focus the prompt field and say type followed by your question.")
                }
                text == "command history" || text == "open history" -> startActivity(Intent(this, HistoryActivity::class.java))
                text.startsWith("search for ") || text.startsWith("search browser for ") -> {
                    val query = text.substringAfter("for ").trim()
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"))))
                    reply("Opening browser results for $query")
                }
                text == "open chatgpt" -> { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"))); reply("Opening ChatGPT") }
                text.startsWith("create note ") -> {
                    startActivity(Intent(Intent.ACTION_SEND).setType("text/plain").setPackage("com.google.android.keep")
                        .putExtra(Intent.EXTRA_TEXT, raw.substringAfter("note ", text.removePrefix("create note "))))
                    reply("Opening a Google Keep note draft. Save it in Keep.")
                }
                text.startsWith("walk to ") || text.startsWith("cycle to ") || text.startsWith("transit to ") -> {
                    val mode = when { text.startsWith("walk") -> "walking"; text.startsWith("cycle") -> "bicycling"; else -> "transit" }
                    val destination = text.substringAfter("to ").trim()
                    require(destination.isNotBlank())
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + URLEncoder.encode(destination,"UTF-8") + "&travelmode=$mode")))
                    reply("Opening $mode directions to $destination")
                }
                text.startsWith("find on maps ") || text.startsWith("find nearby ") -> {
                    val query = if (text.startsWith("find nearby ")) text.removePrefix("find nearby ") + " near me" else text.removePrefix("find on maps ")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(query,"UTF-8"))))
                    reply("Searching Maps for $query")
                }
                text.startsWith("navigate to ") || text.startsWith("directions to ") -> {
                    val destination = text.substringAfter("to ")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + URLEncoder.encode(destination,"UTF-8") + "&travelmode=driving")))
                    reply("Opening driving directions to $destination in Maps")
                }
                text == "read notifications" -> reply(ShouneNotificationService.current?.readSummary() ?: "Enable notification access in Permissions")
                text.isBlank() -> { awaitingConfirmation = true; reply("I’m listening. What do you need?") }
                text == "local models" || text == "open local models" -> startActivity(Intent(this, ModelsActivity::class.java))
                text == "cancel answer" -> { LocalBrain.cancel(); reply("Answer cancelled.") }
                text == "permissions" || text == "open permissions" -> startActivity(Intent(this, PermissionsActivity::class.java))
                text in listOf("go home", "home", "go back", "back", "recent apps", "show notifications", "open notifications", "quick settings", "scroll down", "scroll up") -> {
                    val access = PhoneAccessibilityService.current
                    reply(if (access == null) "Enable Shoune accessibility in Permissions" else if (access.control(text)) "Done" else "That control is unavailable on this screen")
                }
                text == "how many notifications" || text == "notification count" -> {
                    val access = ShouneNotificationService.current
                    reply(if (access == null) "Enable notification access in Permissions" else "You have ${access.count()} notifications")
                }
                text.startsWith("media ") -> {
                    val access = ShouneNotificationService.current
                    reply(if (access?.media(text.removePrefix("media ")) == true) "Done" else "Start media once and enable notification access first")
                }
                text.matches(Regex("play .+ on (spotify|youtube)")) -> {
                    val appName = text.substringAfterLast(" on ")
                    val query = text.removePrefix("play ").substringBeforeLast(" on ").trim()
                    val encoded = URLEncoder.encode(query, "UTF-8")
                    val uri = if (appName == "spotify") Uri.parse("spotify:search:$encoded")
                        else Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    reply("Opening $query on $appName")
                }
                text.startsWith("open instagram profile ") -> {
                    val handle = text.removePrefix("open instagram profile ").trim().removePrefix("@").filter { it.isLetterOrDigit() || it == '_' || it == '.' }
                    require(handle.isNotBlank()) { "Say an Instagram profile name" }
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$handle/")))
                    reply("Opening Instagram profile $handle")
                }
                text.startsWith("open whatsapp chat with ") -> {
                    val number = SpokenNumbers.phone(text.removePrefix("open whatsapp chat with "))
                    require(number.matches(Regex("\\+?[0-9]{7,15}"))) { "Say a WhatsApp phone number one digit at a time" }
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${number.removePrefix("+")}")))
                    reply("Opening the WhatsApp chat. Shoune will not send without a separate confirmed action.")
                }
                text.startsWith("tap ") -> {
                    val access = PhoneAccessibilityService.current
                    reply(if (access?.clickText(text.removePrefix("tap ")) == true) "Done" else "I need one exact visible label to tap")
                }
                text.startsWith("type ") -> {
                    val access = PhoneAccessibilityService.current
                    reply(if (access?.typeIntoFocused(text.removePrefix("type ")) == true) "Text entered. Say tap followed by the exact button label when ready." else "Select a non-password text field first")
                }
                text == "stop listening" || text == "stop" -> { stop(); reply("Paused.") }
                text.startsWith("open ") -> {
                    val name = text.removePrefix("open ").trim()
                    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val matches = packageManager.queryIntentActivities(query, 0).filter {
                        AppNames.key(it.loadLabel(packageManager).toString()) == AppNames.key(name)
                    }
                    if (matches.size != 1) {
                        reply("Use the exact app name. Found " + matches.size + " matches for " + name); return
                    }
                    val app = matches.single().activityInfo
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(app.packageName, app.name))
                    reply("Opened " + name)
                }
                text == "volume up" || text == "volume down" -> {
                    val audio = getSystemService(AUDIO_SERVICE) as AudioManager
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        if (text.endsWith("up")) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI)
                    reply("Media volume adjusted.")
                }
                text == "flashlight on" || text == "flashlight off" -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        requestPermission(Manifest.permission.CAMERA)
                        reply("Grant camera permission, then repeat the command."); return
                    }
                    val camera = getSystemService(CAMERA_SERVICE) as CameraManager
                    val id = camera.cameraIdList.firstOrNull {
                        camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    if (id == null) reply("No flashlight found.")
                    else { camera.setTorchMode(id, text.endsWith("on")); reply(text) }
                }
                text.matches(Regex("set timer for .+ minutes?")) -> {
                    val duration = text.removePrefix("set timer for ").replace(Regex(" minutes?$"), "")
                    val minutes = SpokenNumbers.minutes(duration)
                    if (minutes !in 1..1440) { reply("Choose between 1 and 1440 minutes."); return }
                    startActivity(Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, false))
                }
                text.startsWith("call ") -> {
                    var number = SpokenNumbers.phone(text.removePrefix("call "))
                    if (number.isEmpty() && checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        val name = text.removePrefix("call ").trim()
                        val phones = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        val names = android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        val numbers = android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                        val matches = mutableSetOf<String>()
                        contentResolver.query(phones, arrayOf(numbers), "$names = ? COLLATE NOCASE", arrayOf(name), null)?.use { cursor ->
                            while (cursor.moveToNext()) matches.add(cursor.getString(0).replace(Regex("[ ()-]"), ""))
                        }
                        if (matches.size > 1) { reply("That contact has multiple numbers. Please say the phone number."); return }
                        number = matches.singleOrNull().orEmpty()
                    }
                    if (!number.matches(Regex("\\+?[0-9]{3,15}"))) {
                        reply("Say call followed by a phone number, one digit at a time."); return
                    }
                    if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermission(Manifest.permission.CALL_PHONE)
                        reply("Allow phone access, then ask me again."); return
                    }
                    pendingCall = number
                    callDeadline = android.os.SystemClock.elapsedRealtime() + 30000
                    awaitingConfirmation = true
                    reply("Call " + number.toCharArray().joinToString(" ") + "? Say confirm call, or cancel.")
                }
                text == "what time is it" || text == "time" ->
                    reply(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()))
                else -> {
                    if (LocalBrain.selected(this) == null) {
                        reply("Choose a model on the Local Models page to enable offline AI answers.")
                    } else {
                        reply("Thinking on your phone…")
                        LocalBrain.answer(this, text) { result ->
                            if (!closed && isUnlocked()) {
                                reply(result.getOrElse { it.message ?: "The model could not answer. Try a smaller model." })
                            }
                        }
                    }
                }
            }
        } catch (error: SecurityException) {
            reply("Allow this action in Shoune Permissions, then try again.")
        } catch (error: IllegalStateException) {
            reply(error.message ?: "This action is unavailable right now.")
        } catch (error: Exception) {
            reply("Android could not complete this action. Check permissions and installed apps.")
        }
    }

}
