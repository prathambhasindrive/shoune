package dev.local.assistant

import android.app.Activity
import android.os.Bundle
import android.widget.*
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded private history; stores commands, never microphone audio. */
object CommandHistory {
    @Synchronized fun add(context: Context, command: String) {
        val prefs = context.getSharedPreferences("history", 0)
        val old = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrDefault(JSONArray())
        val items = JSONArray().put(JSONObject().put("time", System.currentTimeMillis()).put("command", command.take(500)))
        for (i in 0 until minOf(old.length(), 99)) items.put(old.get(i))
        prefs.edit().putString("items", items.toString()).apply()
    }
}
class HistoryActivity : Activity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE); render() }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,48,32,32) }
        root.addView(TextView(this).apply { text = "Command history"; textSize = 26f })
        root.addView(Button(this).apply { text = "Clear history"; setOnClickListener { getSharedPreferences("history",0).edit().clear().apply(); render() } })
        val items = runCatching { JSONArray(getSharedPreferences("history",0).getString("items","[]")) }.getOrDefault(JSONArray())
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            root.addView(TextView(this).apply { text = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.getLong("time"))) + "\n" + item.getString("command"); textSize = 17f; setPadding(0,16,0,16) })
        }
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
