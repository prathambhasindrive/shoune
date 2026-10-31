package dev.local.assistant.commands

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import dev.local.assistant.PhoneAccessibilityService

/** Public Android APIs only. Restricted radio controls open their system settings. */
object DeviceControls {
    fun execute(context: Context, text: String, launch: (Intent) -> Unit): String? {
        val panel = when {
            Regex(".*\\b(wifi|wi fi)\\b.*").matches(text) -> Settings.ACTION_WIFI_SETTINGS
            text.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            text.contains("hotspot") -> Settings.ACTION_WIRELESS_SETTINGS
            text.contains("airplane mode") || text.contains("air plane mode") -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            text.contains("power saving") || text.contains("battery saver") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            text == "location" || text.matches(Regex("(?:turn |switch )?(?:location (?:on|off)|(?:on|off) location)")) -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            text in listOf("internet", "mobile data", "internet on", "internet off", "mobile data on", "mobile data off") -> if (android.os.Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_DATA_ROAMING_SETTINGS
            else -> null
        }
        if (panel != null && text.matches(Regex("(?:(?:open|turn|switch|enable|disable) )?(?:(?:on|off) )?(?:wifi|wi fi|bluetooth|hotspot|airplane mode|air plane mode|power saving mode|power saving|battery saver|location|internet|mobile data)(?: settings| on| off)?"))) {
            launch(Intent(panel))
            return "Opening system controls. Choose the switch there; Android requires your input for this setting."
        }
        if (text in listOf("close drawer", "close notifications", "close quick settings"))
            return if (PhoneAccessibilityService.current?.control("back") == true) "Closed" else "Enable accessibility in Settings"
        val volume = Regex("(?:set )?volume (?:to )?(.+?)(?: percent)?").matchEntire(text)
        if (volume != null || text == "mute media") {
            if (text in listOf("volume up", "volume down")) return null
            val percent = volume?.groupValues?.get(1)?.let { dev.local.assistant.speech.SpokenNumbers.minutes(it) } ?: 0
            require(percent in 0..100) { "Choose volume from zero to one hundred percent" }
            val audio = context.getSystemService(AudioManager::class.java)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * percent / 100, AudioManager.FLAG_SHOW_UI)
            return "Media volume $percent percent"
        }
        val brightness = if (text in listOf("brightness up", "brightness down")) null else Regex("(?:set )?brightness (?:to )?(.+?)(?: percent)?").matchEntire(text)
        if (brightness != null || text in listOf("brightness up", "brightness down", "automatic brightness")) {
            if (!Settings.System.canWrite(context)) {
                launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}")))
                return "Allow modify system settings, then repeat the brightness command"
            }
            if (text == "automatic brightness") {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                return "Automatic brightness enabled"
            }
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) * 100 / 255
            val percent = brightness?.groupValues?.get(1)?.let { dev.local.assistant.speech.SpokenNumbers.minutes(it) } ?: (current + if (text.endsWith("up")) 10 else -10).coerceIn(1,100)
            require(percent in 1..100) { "Choose brightness from one to one hundred percent" }
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            check(Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, percent * 255 / 100))
            return "Brightness $percent percent"
        }
        return null
    }
}
