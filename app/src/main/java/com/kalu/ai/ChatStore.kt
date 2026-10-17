package com.kalu.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Stores chat history privately in the app's own storage (no cloud, no backup). */
class ChatStore(private val context: Context) {
    private val file: File get() = File(context.filesDir, "history.json")

    fun load(): List<ChatMessage> = try {
        if (!file.exists()) {
            emptyList()
        } else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                ChatMessage(
                    id = o.getLong("id"),
                    fromUser = o.getBoolean("fromUser"),
                    text = o.getString("text"),
                    time = o.getLong("time")
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("fromUser", it.fromUser)
                        .put("text", it.text)
                        .put("time", it.time)
                )
            }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // Saving history is best-effort; the chat keeps working without it.
        }
    }

    fun clear() {
        file.delete()
    }
}
