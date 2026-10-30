package dev.local.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PhoneAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { current = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { if (current === this) current = null; super.onDestroy() }
    fun control(command: String): Boolean = when (command) {
        "go home", "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "go back", "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "recent apps" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "show notifications", "open notifications" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        "quick settings" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        "scroll down", "scroll up" -> scroll(rootInActiveWindow, command == "scroll down")
        else -> false
    }
    private fun scroll(node: AccessibilityNodeInfo?, forward: Boolean): Boolean {
        if (node == null) return false
        if (node.isScrollable && node.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        for (index in 0 until node.childCount) if (scroll(node.getChild(index), forward)) return true
        return false
    }
    companion object { @Volatile var current: PhoneAccessibilityService? = null; private set }
}
