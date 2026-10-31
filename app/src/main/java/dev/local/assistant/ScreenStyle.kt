package dev.local.assistant

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Shared typography and surfaces for every settings panel. */
object ScreenStyle {
    fun apply(activity: Activity) {
        activity.window.statusBarColor = Color.rgb(16,27,23)
        activity.window.navigationBarColor = Color.rgb(16,27,23)
        fun style(view: View) {
            when (view) {
                is Button -> { view.isAllCaps = false; view.minHeight = 56; view.setTextColor(Color.rgb(233,217,174)); view.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30,48,40)) }
                is TextView -> { view.setTextColor(Color.rgb(231,240,232)); view.setLineSpacing(4f,1f) }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
        }
        activity.findViewById<View>(android.R.id.content).apply { setBackgroundColor(Color.rgb(16,27,23)); style(this) }
    }
}
