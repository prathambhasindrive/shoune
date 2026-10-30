package dev.local.assistant.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.view.WindowManager

/** Wake recognition is not authentication. Sensitive actions require an unlocked device. */
object DeviceSecurity {
    fun unlocked(context: Context) = !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
    fun protect(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 31) activity.window.setHideOverlayWindows(true)
        activity.window.decorView.filterTouchesWhenObscured = true
    }
}
