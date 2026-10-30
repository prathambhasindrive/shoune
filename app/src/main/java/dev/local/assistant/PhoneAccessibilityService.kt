package dev.local.assistant

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Bundle

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
    /** Exact visible-label targeting avoids coordinate guesses copied from another phone. */
    fun clickText(label: String): Boolean {
        val target = label.trim()
        if (target.isBlank() || target.length > 80) return false
        val matches = rootInActiveWindow?.findAccessibilityNodeInfosByText(target).orEmpty().filter {
            it.text?.toString()?.equals(target, true) == true || it.contentDescription?.toString()?.equals(target, true) == true
        }
        if (matches.size != 1) return false
        var node: AccessibilityNodeInfo? = matches.single()
        repeat(4) {
            if (node?.isClickable == true) return node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            node = node?.parent
        }
        return false
    }

    fun typeIntoFocused(value: String): Boolean {
        if (value.isBlank() || value.length > 500) return false
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        if (!node.isEditable || node.isPassword) return false
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        })
    }
    private fun scroll(node: AccessibilityNodeInfo?, forward: Boolean): Boolean {
        if (node == null) return false
        if (node.isScrollable && node.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        for (index in 0 until node.childCount) if (scroll(node.getChild(index), forward)) return true
        return false
    }
    companion object { @Volatile var current: PhoneAccessibilityService? = null; private set }
}
