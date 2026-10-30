package com.gi.koko.voiceassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object ChatStore {
    private const val PREFS = "gi_voice_prefs"
    private const val KEY = "chat_history_v1"

    fun load(context: Context): List<ChatLine> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            ChatLine(item.optBoolean("user"), item.optString("text"))
        }.takeLast(200)
    }.getOrDefault(emptyList())

    fun save(context: Context, messages: List<ChatLine>) {
        val array = JSONArray()
        messages.takeLast(200).forEach { line ->
            array.put(JSONObject().put("user", line.fromUser).put("text", line.text))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
