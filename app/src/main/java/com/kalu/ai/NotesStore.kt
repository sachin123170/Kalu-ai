package com.kalu.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Note(val id: Long, val text: String, val time: Long)

class NotesStore(private val context: Context) {
    private val file get() = File(context.filesDir, "notes.json")

    fun load(): List<Note> = try {
        if (!file.exists()) emptyList() else {
            val a = JSONArray(file.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Note(o.getLong("id"), o.getString("text"), o.getLong("time"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Note>) {
        try {
            val a = JSONArray()
            list.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("time", it.time)) }
            file.writeText(a.toString())
        } catch (e: Exception) {
        }
    }

    fun clear() {
        file.delete()
    }
}
