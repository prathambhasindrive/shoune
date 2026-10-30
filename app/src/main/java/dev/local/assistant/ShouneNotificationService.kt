package dev.local.assistant

import android.service.notification.NotificationListenerService

/** Counts active notifications only when asked; no notification text is stored. */
class ShouneNotificationService : NotificationListenerService() {
    override fun onListenerConnected() { current = this }
    override fun onListenerDisconnected() { current = null }
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    fun count(): Int = activeNotifications?.count { it.packageName != packageName } ?: 0
    companion object { @Volatile var current: ShouneNotificationService? = null; private set }
}
