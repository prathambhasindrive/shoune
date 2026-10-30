package dev.local.assistant

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*

class PermissionsActivity : Activity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state) }
    override fun onResume() { super.onResume(); render() }
    private fun allowed(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun open(intent: Intent) { try { startActivity(intent) } catch (e: Exception) { Toast.makeText(this, "This setting is unavailable on this phone", Toast.LENGTH_LONG).show() } }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,48,32,32); setBackgroundColor(Color.rgb(16,27,23)) }
        fun label(text: String, size: Float) { root.addView(TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.rgb(224,240,225)); setPadding(0,12,0,12) }) }
        fun row(title: String, detail: String, enabled: Boolean, action: () -> Unit) {
            label(title + if (enabled) " · Allowed" else " · Not enabled", 18f)
            label(detail, 13f)
            root.addView(Button(this).apply { text = if (enabled) "Manage" else "Enable"; isAllCaps = false; setOnClickListener { action() } })
        }
        label("Shoune permissions", 28f)
        label("You choose each permission. Android will ask you to approve access in its own screens.", 14f)
        root.addView(Button(this).apply { text = "Voice & commander name"; isAllCaps = false; setOnClickListener { open(Intent(this@PermissionsActivity, PersonalizationActivity::class.java)) } })
        fun runtime(title: String, detail: String, permission: String) = row(title, detail, allowed(permission)) {
            if (allowed(permission)) open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            else requestPermissions(arrayOf(permission), 20)
        }
        label("Spoken replies", 18f)
        label("Choose an offline English voice in Android Text-to-speech settings. Download voice data there if needed, then reopen Shoune.", 13f)
        root.addView(Button(this).apply { text = "Set up spoken replies"; isAllCaps = false; setOnClickListener { open(Intent("com.android.settings.TTS_SETTINGS")) } })
        runtime("Microphone", "Recognize your voice and listen for Shoune.", Manifest.permission.RECORD_AUDIO)
        runtime("Phone calls", "Place a call only after your spoken confirmation.", Manifest.permission.CALL_PHONE)
        runtime("Flashlight", "Camera permission allows flashlight control.", Manifest.permission.CAMERA)
        runtime("Contacts", "Find a contact when you say their exact name.", Manifest.permission.READ_CONTACTS)
        row("Installed apps", "Opening apps while Shoune is on screen needs no extra permission. Background app opening uses Accessibility and requires an unlocked phone.", true) {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        row("Listening notifications", "Show microphone status and the Stop listening action.", getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
            if (Build.VERSION.SDK_INT >= 33 && !allowed(Manifest.permission.POST_NOTIFICATIONS)) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
            else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        row("Accessibility controls", "Home, Back, recent apps, scroll, notification panel and quick settings. Shoune can inspect the active screen for scrolling.", PhoneAccessibilityService.current != null) {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        row("Notification access", "Count active notifications when you ask. Notification messages are not stored.", ShouneNotificationService.current != null) {
            open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        row("Background battery access", "Allow unrestricted battery use for longer listening. This increases battery use.", getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        label("Keep Shoune listening", 21f)
        label("Turn on Shoune on the home screen. A persistent notification shows when the microphone is active. On Realme, also allow background activity / auto-launch in the app battery settings if available. After reboot or Force stop, open Shoune to resume. Android may still end listening under memory or battery pressure.", 14f)
        root.addView(Button(this).apply { text = "Open app battery settings"; setOnClickListener { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } })
        root.addView(Button(this).apply { text = "Back to Shoune"; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) { super.onRequestPermissionsResult(code,permissions,results); render() }
}
