package dev.local.assistant

import android.app.Activity
import android.os.Bundle
import android.widget.*

object Features {
    const val spoken = "I can control the flashlight and media volume, adjust brightness with permission, open apps, confirm calls, read notifications, and open system controls. I can search the browser, open Maps routes, draft Google Keep notes, and answer offline questions with a selected local model. Say stop conversation to return to wake listening, or stop listening to power me off. More examples are in Settings, Features."
    val sections = listOf(
        "Conversation" to "Say Hey Shoune or Okay Shoune. Follow-up commands stay active until stop conversation. Stop listening powers off the microphone service. Background listening uses battery.",
        "Device controls" to "Flashlight on/off; volume up/down; set volume to 50 percent; mute media; brightness up/down; set brightness to 50 percent; automatic brightness. Brightness requires Modify system settings permission.",
        "System panels" to "WiFi, Bluetooth, hotspot, internet/mobile data, location, airplane mode and battery saver open Android controls. These are not silent direct toggles. Availability varies by phone.",
        "Drawer & navigation" to "Quick settings, show notifications, close drawer, home, back, recent apps, scroll up/down. Accessibility must be enabled. Tap followed by an exact visible label selects a control.",
        "Calls & notifications" to "Call a number or exact contact name; say confirm call or cancel. Read notifications or ask notification count. Grant each permission in Settings.",
        "Apps & browser" to "Open an app by its exact name. Search for a topic. Open YouTube in Brave and play a title opens results; choose a result. Open ChatGPT in Chrome opens the page; submission is manual. Play/pause/next/previous control an active media session.",
        "Maps & notes" to "Directions to a destination, walk to, cycle to, transit to, find nearby, or find on maps open Maps routes/results. Live traffic summaries and full navigation automation are not implemented. Create note opens a Google Keep draft to save.",
        "Personalization & local AI" to "Commander name, offline Android reply voices, optional voice enrollment, Pulse/Sway wake animations, local GGUF model import and selection, and the latest 100 commands in history. Voice matching is not secure authentication.",
        "While locked" to "Listening, flashlight, time and active media transport work within Android limits. Private commands and app screens require unlocking. Third-party services may need internet even though Shoune voice recognition works offline."
    )
}
class FeaturesActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,48,32,32) }
        root.addView(TextView(this).apply { text = "Features & commands"; textSize = 28f })
        Features.sections.forEach { (title, detail) ->
            root.addView(TextView(this).apply { text = title; textSize = 20f; setPadding(0,28,0,8) })
            root.addView(TextView(this).apply { text = detail; textSize = 15f })
        }
        root.addView(Button(this).apply { text = "Back to Settings"; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
        ScreenStyle.apply(this)
    }
}
