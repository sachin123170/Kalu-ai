package com.kalu.ai

import android.icu.text.Transliterator
import java.util.Locale

/**
 * Hindi speech recognition (hi-IN) usually returns Devanagari, e.g. "विनय को एसएमएस भेजो ...".
 * The command parser works on Latin text, so common command words are mapped to Latin here.
 * The message body and other free text stay in their original script.
 */
object Translit {
    private val words = mapOf(
        "को" to "ko", "भेजो" to "bhejo", "भेजिए" to "bhejo", "भेज" to "bhej", "दो" to "do",
        "करो" to "karo", "कर" to "kar", "लिखो" to "likho", "खोलो" to "kholo", "खोल" to "khol",
        "एसएमएस" to "sms", "एसएमस" to "sms", "एस" to "sms", "टेक्स्ट" to "text",
        "मैसेज" to "message", "मेसेज" to "message", "संदेश" to "message",
        "व्हाट्सएप" to "whatsapp", "व्हाट्सऐप" to "whatsapp", "वाट्सएप" to "whatsapp",
        "वॉट्सऐप" to "whatsapp", "व्हाट्सअप" to "whatsapp", "व्हाट्सप" to "whatsapp", "वाट्सऐप" to "whatsapp",
        "ओपन" to "open", "यूट्यूब" to "youtube", "यूट्यूब" to "youtube", "पर" to "par", "पे" to "pe",
        "बजाओ" to "bajao", "चलाओ" to "chalao", "सुनाओ" to "sunao", "अलार्म" to "alarm",
        "टाइमर" to "timer", "नोट" to "note", "बजे" to "baje",
        "इंस्टाग्राम" to "instagram", "फेसबुक" to "facebook", "टेलीग्राम" to "telegram",
        "क्रोम" to "chrome", "कैमरा" to "camera", "जीमेल" to "gmail", "मैप्स" to "maps",
        "सेटिंग्स" to "settings", "कैलकुलेटर" to "calculator", "गैलरी" to "gallery",
        "सेंड" to "send", "बैक" to "back", "होम" to "home", "स्क्रॉल" to "scroll"
    )

    private val tr: Transliterator? by lazy {
        try {
            Transliterator.getInstance("Devanagari-Latin; Latin-ASCII")
        } catch (e: Exception) {
            null
        }
    }

    private fun hasDevanagari(s: String) = s.any { it in '\u0900'..'\u097F' }

    /** Maps Devanagari command words and digits to Latin. Other text is left untouched. */
    fun commandWords(input: String): String {
        if (!hasDevanagari(input)) return input
        val digits = input.map { if (it in '\u0966'..'\u096F') ('0' + (it - '\u0966')) else it }.joinToString("")
        return digits.split(" ").joinToString(" ") { token ->
            val clean = token.trimEnd('।', ',', '.', '?', '!')
            val tail = token.substring(clean.length)
            (words[clean] ?: clean) + tail
        }
    }

    /** Devanagari name to Latin letters, for contact lookup ("विनय" becomes "vinaya"). */
    fun nameToLatin(name: String): String {
        if (!hasDevanagari(name)) return name
        return try {
            (tr?.transliterate(name) ?: name).lowercase(Locale.ROOT)
        } catch (e: Exception) {
            name
        }
    }
}
