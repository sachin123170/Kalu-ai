package com.kalu.ai

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Safety layer for outgoing messages (M2): OTP/password block, rate limit, duplicate guard,
 * and a global emergency stop. It only decides allow or block; it never sends anything itself.
 */
object ToolRouter {
    private var store: SharedPreferences? = null

    /** Emergency-stop flag. It is saved, so it survives Android killing the app. */
    @Volatile
    var paused: Boolean = false
        set(value) {
            field = value
            store?.edit()?.putBoolean("paused", value)?.apply()
        }

    fun init(ctx: Context) {
        if (store == null) {
            store = ctx.applicationContext.getSharedPreferences("kalu_safety", Context.MODE_PRIVATE)
            paused = store?.getBoolean("paused", false) ?: false
        }
    }

    private const val WINDOW_MS = 10 * 60_000L
    private const val MAX_PER_WINDOW = 5
    private const val DUP_MS = 60_000L

    private val sent = mutableListOf<Pair<Long, String>>()

    private val secretWords = Regex(
        "\\b(otp|password|passcode|pin|cvv|cvc|verification code|one[- ]time)\\b",
        RegexOption.IGNORE_CASE
    )
    private val shortDigits = Regex("\\b\\d{4,8}\\b")
    private val cardLike = Regex("\\b(?:\\d[ -]?){13,19}\\b")

    private fun key(kind: String, name: String, body: String) =
        kind.lowercase(Locale.ROOT) + "|" + name.trim().lowercase(Locale.ROOT) + "|" + body.trim().lowercase(Locale.ROOT)

    /** Returns null if the message may go ahead, otherwise a Hinglish reason it was blocked. */
    @Synchronized
    fun checkMessage(kind: String, name: String, body: String): String? {
        if (paused) {
            return "KALU abhi emergency stop mein hai. Pehle 'kalu chalu karo' bolo."
        }
        if ((secretWords.containsMatchIn(body) && shortDigits.containsMatchIn(body)) || cardLike.containsMatchIn(body)) {
            ActivityLog.add("BLOCK ${kind.uppercase(Locale.ROOT)} -> $name: OTP/password/card jaisa content")
            return "Is message mein OTP, password ya card number jaisa kuch lag raha hai. Safety ke liye main ye nahi bhejunga."
        }
        val now = System.currentTimeMillis()
        sent.removeAll { now - it.first > WINDOW_MS }
        val k = key(kind, name, body)
        if (sent.any { it.second == k && now - it.first < DUP_MS }) {
            ActivityLog.add("BLOCK ${kind.uppercase(Locale.ROOT)} -> $name: same message dobara")
            return "Ye bilkul wahi message abhi 1 minute ke andar bheja tha. Dobara nahi bhej raha. 1 minute baad try karo."
        }
        if (sent.size >= MAX_PER_WINDOW) {
            ActivityLog.add("BLOCK ${kind.uppercase(Locale.ROOT)} -> $name: rate limit")
            return "10 minute mein $MAX_PER_WINDOW messages ho chuke hain. Loop se bachne ke liye ruk gaya hoon, thodi der baad bolo."
        }
        return null
    }

    /** Call when the user has confirmed and the send really starts. The message body is never logged. */
    @Synchronized
    fun recordSend(kind: String, name: String, body: String) {
        sent.add(System.currentTimeMillis() to key(kind, name, body))
        ActivityLog.add("${kind.uppercase(Locale.ROOT)} bheja -> $name (${body.length} akshar)")
    }
}

/** Short on-device log of what KALU did. Message text is not stored. */
object ActivityLog {
    private const val MAX = 100
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp == null) sp = ctx.applicationContext.getSharedPreferences("kalu_activity", Context.MODE_PRIVATE)
    }

    @Synchronized
    fun add(text: String) {
        val p = sp ?: return
        val stamp = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
        val line = "$stamp  " + text.replace('\n', ' ').take(140)
        val old = (p.getString("lines", "") ?: "").split("\n").filter { it.isNotBlank() }
        p.edit().putString("lines", (listOf(line) + old).take(MAX).joinToString("\n")).apply()
    }

    fun recent(n: Int = 10): String {
        val p = sp ?: return "Log abhi available nahi hai."
        val lines = (p.getString("lines", "") ?: "").split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return "Abhi koi activity nahi hai."
        return "Pichli activity:\n" + lines.take(n).joinToString("\n") { "• $it" }
    }
}
