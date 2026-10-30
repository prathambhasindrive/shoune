package dev.local.assistant

import android.service.notification.NotificationListenerService
import android.content.ComponentName
import android.media.session.MediaSessionManager

/** Counts active notifications only when asked; no notification text is stored. */
class ShouneNotificationService : NotificationListenerService() {
    override fun onListenerConnected() { current = this }
    override fun onListenerDisconnected() { current = null }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    fun count(): Int = activeNotifications?.count { it.packageName != packageName } ?: 0
    fun media(action: String): Boolean {
        val controller = getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(this, ShouneNotificationService::class.java)).firstOrNull() ?: return false
        when (action) {
            "play" -> controller.transportControls.play()
            "pause" -> controller.transportControls.pause()
            "next" -> controller.transportControls.skipToNext()
            "previous" -> controller.transportControls.skipToPrevious()
            else -> return false
        }
        return true
    }
    companion object { @Volatile var current: ShouneNotificationService? = null; private set }
}
