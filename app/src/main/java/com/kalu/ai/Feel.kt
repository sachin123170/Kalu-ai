package com.kalu.ai

import java.util.Locale

/**
 * How the user's message seems to feel, guessed from keywords (not certain, not saved anywhere).
 * Used only for the current reply: orb colour, voice speed/pitch, and a hint to Gemini.
 */
enum class Feel(val emoji: String, val rate: Float, val pitch: Float, val colorArgb: Long) {
    NEUTRAL("", 1.0f, 1.0f, 0xFF4DA3FF),
    HAPPY("😄", 1.05f, 1.1f, 0xFFFFC857),
    SAD("🫂", 0.85f, 0.9f, 0xFF9B8CFF),
    TIRED("😴", 0.88f, 0.92f, 0xFF4DD0C8),
    STRESSED("🌿", 0.9f, 0.95f, 0xFF6FE38A),
    ANGRY("🔥", 0.92f, 0.95f, 0xFFFF8A65)
}

object FeelDetector {
    private val rules: List<Pair<Feel, List<String>>> = listOf(
        Feel.SAD to listOf(
            "udaas", "dukhi", "dukh", "rona", "akela", "akeli", "sad", "lonely", "upset", "dilbroken",
            "dil toot", "bura lag", "रोना", "उदास", "दुखी", "अकेला", "अकेली"
        ),
        Feel.TIRED to listOf(
            "thak", "thaka", "thaki", "thakan", "tired", "exhausted", "neend", "थक", "नींद"
        ),
        Feel.STRESSED to listOf(
            "tension", "stress", "stressed", "pareshan", "pareshaan", "chinta", "ghabra", "ghabrahat", "anxious", "worried",
            "dar lag", "टेंशन", "परेशान", "चिंता", "घबरा"
        ),
        Feel.ANGRY to listOf(
            "gussa", "gusse", "angry", "irritated", "frustrated", "bakwas", "गुस्सा", "गुस्से"
        ),
        Feel.HAPPY to listOf(
            "khush", "mast", "maza", "mazaa", "awesome", "great", "yay", "party", "congrats", "badhai", "kamaal",
            "खुश", "मज़ा", "मजा", "बधाई"
        )
    )

    fun detect(text: String): Feel {
        val t = text.lowercase(Locale.ROOT)
        val tokens = t.split(Regex("[^\\p{L}\\p{N}\\u0900-\\u097F]+")).filter { it.isNotEmpty() }.toSet()
        for ((feel, keys) in rules) {
            for (k in keys) {
                val devanagari = k.any { it in '\u0900'..'\u097F' }
                val hit = if (devanagari || k.contains(' ')) t.contains(k) else k in tokens
                if (hit) return feel
            }
        }
        return Feel.NEUTRAL
    }
}
