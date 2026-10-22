package com.kalu.ai

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.CalendarContract
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Things KALU was asked to remember ("yaad rakho: ..."). Stored privately on the phone. */
class FactsStore(private val context: Context) {
    private val file get() = File(context.filesDir, "memory.json")

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

/** Shared by the chat and the floating bubble. Every function returns what KALU should say. */
object MoreActions {

    // ---------- memory ----------

    fun remember(ctx: Context, text: String): String {
        val s = FactsStore(ctx)
        val l = s.load().toMutableList()
        val now = System.currentTimeMillis()
        l.add(0, Note(now, text, now))
        s.save(l.take(200))
        return "Yaad rakh liya: \"${text.take(80)}\" 🧠"
    }

    fun showMemory(ctx: Context): String {
        val l = FactsStore(ctx).load()
        return if (l.isEmpty()) "Abhi mujhe kuch yaad nahi. 'yaad rakho: ...' bolke sikhao."
        else "Mujhe ye yaad hai:\n" + l.take(15).joinToString("\n") { "• ${it.text.take(100)}" }
    }

    fun forget(ctx: Context, text: String): String {
        val s = FactsStore(ctx)
        val l = s.load()
        val keep = l.filter { !it.text.contains(text, ignoreCase = true) }
        s.save(keep)
        val n = l.size - keep.size
        return if (n > 0) "Bhool gaya ($n cheez)." else "Aisi koi yaad mili nahi."
    }

    fun clearFacts(ctx: Context) = FactsStore(ctx).clear()

    fun factsForPrompt(ctx: Context, max: Int = 25): String =
        FactsStore(ctx).load().take(max).joinToString("\n") { "- " + it.text.take(160) }

    // ---------- calendar / call / maps / media / briefing ----------

    /** Opens the Calendar app with the event filled in; the user presses Save there. */
    fun addEvent(ctx: Context, title: String, startMillis: Long): String {
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + 3_600_000L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            "Calendar khol diya, '$title' wahan Save karo. 📅"
        } catch (e: Exception) {
            "Calendar app nahi khul paya."
        }
    }

    /** Puts the number in the dialer; the user presses the call button. */
    fun call(ctx: Context, name: String): String {
        val (hit, err) = Messaging.pick(ctx, name)
        if (hit == null) return err ?: "Contact nahi mila."
        val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(hit.number)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            "${hit.name} ka number dialer mein daal diya. Call button tum dabao. 📞"
        } catch (e: Exception) {
            "Dialer nahi khul paya."
        }
    }

    fun navigate(ctx: Context, place: String): String {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
            .setPackage("com.google.android.apps.maps")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(nav)
            return "$place ka raasta khol raha hoon. 🗺️"
        } catch (e: Exception) {
        }
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(geo)
            "$place map par khol raha hoon. 🗺️"
        } catch (e: Exception) {
            "Maps nahi khul paya."
        }
    }

    fun media(ctx: Context, action: String): String {
        val code = when (action) {
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            else -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        return try {
            val am = ctx.getSystemService(AudioManager::class.java)
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            "Theek hai. 🎵"
        } catch (e: Exception) {
            "Music control nahi ho paya."
        }
    }

    fun briefing(ctx: Context): String {
        val now = Date()
        val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
        val sb = StringBuilder("Namaste! Aaj $date hai, abhi $time baj rahe hain.")
        val notes = NotesStore(ctx).load().take(3)
        if (notes.isNotEmpty()) sb.append(" Tumhare naye notes: ").append(notes.joinToString("; ") { it.text.take(60) }).append(".")
        val facts = FactsStore(ctx).load().take(3)
        if (facts.isNotEmpty()) sb.append(" Mujhe yaad hai: ").append(facts.joinToString("; ") { it.text.take(60) }).append(".")
        sb.append(" Kuch aur karna ho to bolo.")
        return sb.toString()
    }
}
