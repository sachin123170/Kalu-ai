package com.kalu.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Basic replies that work with no internet and no API key. */
object OfflineBrain {
    private val jokes = listOf(
        "Teacher: 5 ke baad kya aata hai? Student: Sir, 6... aur exam ka darr. 😄",
        "Phone ki battery aur meri motivation, dono hamesha 1% pe rehti hain. 🔋",
        "Main AI hoon, mujhe neend nahi aati. Par tumhe aani chahiye, bhai! 😴"
    )

    @Volatile
    var name = "KALU"

    fun greeting() =
        "Namaste bhai! Main $name hoon, tumhara AI dost. Main insaan nahi, ek app hoon, " +
            "par poori koshish karunga ki tumhari madad kar sakoon. Kuch bhi poochho ya bolo: " +
            "\"open WhatsApp\", \"youtube par arijit ke gaane chalao\", \"7:30 baje alarm\"."

    fun fallback() =
        "Abhi main offline basic mode mein hoon bhai: time, date, joke jaisi chhoti cheezein kar sakta hoon."

    private fun words(t: String) =
        t.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()

    /** Only for short "what's the time/date" style questions. Gemini cannot know the device clock. */
    fun timeOrDate(input: String): String? {
        val t = input.lowercase(Locale.getDefault())
        val w = words(t)
        if (w.size > 5) return null
        return when {
            w.any { it in setOf("time", "samay", "baje") } -> {
                "Abhi samay hai " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()) + "."
            }
            w.any { it in setOf("date", "tarikh") } -> {
                "Aaj ki date hai " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
            }
            else -> null
        }
    }

    fun tryReply(input: String): String? {
        val t = input.lowercase(Locale.getDefault()).trim()
        val w = words(t)
        fun has(vararg x: String) = x.any { it in w }
        return when {
            has("hi", "hii", "hello", "hey", "namaste") || t.contains("kya haal") ->
                "Hey bhai! Kaise ho? Bolo, kya karna hai?"
            has("joke", "chutkula") -> jokes.random()
            has("thanks", "thank", "shukriya", "dhanyavad") -> "Arre bhai, mention not! 🙌"
            t.contains("who are you") || t.contains("kaun ho") || t.contains("tumhara naam") ->
                "Main $name hoon, tumhara friendly AI dost. Insaan nahi, ek app hoon."
            else -> timeOrDate(t)
        }
    }
}
