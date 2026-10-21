package com.kalu.ai

import java.util.Locale

sealed class Command {
    data class OpenApp(val name: String) : Command()
    data class FindApps(val query: String) : Command()
    data class YouTube(val query: String) : Command()
    data class SetAlarm(val hour: Int, val minute: Int) : Command()
    data class SetTimer(val seconds: Int) : Command()
    data class SaveNote(val text: String) : Command()
    object ShowNotes : Command()
    object Back : Command()
    object Home : Command()
    object Recents : Command()
    object ScrollDown : Command()
    object ScrollUp : Command()
    object ReadScreen : Command()
    data class ClickText(val text: String) : Command()
    data class TypeText(val text: String) : Command()
    object SendMessage : Command()
    data class SendSms(val name: String, val body: String) : Command()
    data class SendWhatsApp(val name: String, val body: String) : Command()
}

/** Local, offline parser for Hindi/Hinglish/English commands. Anything unmatched goes to Gemini. */
object CommandRouter {
    private val I = RegexOption.IGNORE_CASE

    private val back = setOf("back", "go back", "wapas jao", "peeche jao", "back jao", "back karo")
    private val home = setOf("home", "go home", "home jao", "home screen")
    private val recents = setOf("recents", "recent apps", "recent apps kholo")
    private val down = setOf("scroll down", "neeche scroll karo", "niche scroll karo", "scroll karo", "neeche jao")
    private val up = setOf("scroll up", "upar scroll karo", "upar jao")
    private val read = setOf("read screen", "screen padho", "screen par kya hai", "screen read karo")
    private val sendSet = setOf("send karo", "send kar do", "send", "bhej do", "bhejo", "send message")
    private val showNotes = setOf("mere notes", "notes dikhao", "show notes", "notes", "my notes", "notes dikha do")

    private fun words(s: String) = s.trim().split(Regex("\\s+")).size

    fun parse(input: String): Command? {
        val s = Translit.commandWords(input).trim().trimEnd('.', '!', '?', '।').trim()
        if (s.isEmpty()) return null
        val t = s.lowercase(Locale.ROOT)

        when (t) {
            in back -> return Command.Back
            in home -> return Command.Home
            in recents -> return Command.Recents
            in down -> return Command.ScrollDown
            in up -> return Command.ScrollUp
            in read -> return Command.ReadScreen
            in showNotes -> return Command.ShowNotes
            in sendSet -> return Command.SendMessage
        }

        // Notes
        Regex("^note(?:\\s+(?:likho|karo|save\\s+karo))?\\s*[:\\-]\\s*(.+)$", I).find(s)?.let {
            return Command.SaveNote(it.groupValues[1].trim())
        }
        Regex("^note\\s+(?:likho|karo)\\s+(.+)$", I).find(s)?.let {
            return Command.SaveNote(it.groupValues[1].trim())
        }

        // Messaging (WhatsApp / SMS). Always confirmed with the user before anything is sent.
        Regex("^(.+?)\\s+ko\\s+(?:whatsapp|watsapp|wa)\\s+(?:message\\s+|msg\\s+)?(?:bhejo|karo|bhej do|kar do|likho)\\s*[:\\-]?\\s*(.+)$", I)
            .find(s)?.let { return Command.SendWhatsApp(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        Regex("^(?:whatsapp|watsapp)\\s+(?:to\\s+)?(\\S+)\\s*[:\\-]\\s*(.+)$", I)
            .find(s)?.let { return Command.SendWhatsApp(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        Regex("^(.+?)\\s+ko\\s+sms\\s+(?:bhejo|karo|bhej do|kar do|likho)\\s*[:\\-]?\\s*(.+)$", I)
            .find(s)?.let { return Command.SendSms(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        Regex("^sms\\s+(?:to\\s+)?(\\S+)\\s*[:\\-]\\s*(.+)$", I)
            .find(s)?.let { return Command.SendSms(it.groupValues[1].trim(), it.groupValues[2].trim()) }
        Regex("^(.+?)\\s+ko\\s+(?:message|msg|text)\\s+(?:bhejo|karo|bhej do|kar do|likho)\\s*[:\\-]?\\s*(.+)$", I)
            .find(s)?.let { return Command.SendWhatsApp(it.groupValues[1].trim(), it.groupValues[2].trim()) }

        // Type into the focused text box (screen control)
        Regex("^(?:type|likho|write|isme likho|yaha likho)\\s*[:\\-]\\s*(.+)$", I).find(s)?.let {
            return Command.TypeText(it.groupValues[1].trim())
        }
        Regex("^(?:type karo|type kar do|likh do|likho)\\s+(.+)$", I).find(s)?.let {
            return Command.TypeText(it.groupValues[1].trim())
        }

        // Timer
        if (t.contains("timer")) {
            Regex("(\\d+)\\s*(seconds?|secs?|minutes?|mins?|ghanta|ghante|hours?|hrs?)").find(t)?.let {
                val n = it.groupValues[1].toIntOrNull() ?: return@let
                val u = it.groupValues[2]
                val secs = when {
                    u.startsWith("s") -> n
                    u.startsWith("m") -> n * 60
                    else -> n * 3600
                }
                if (secs in 1..86400) return Command.SetTimer(secs)
            }
        }

        // Alarm / reminder
        if (listOf("alarm", "reminder", "remind me", "yaad dila", "jagana", "jaga").any { t.contains(it) }) {
            parseTime(t)?.let { return Command.SetAlarm(it.first, it.second) }
        }

        // Find apps
        Regex("^(?:find|search|dhundo|dhoondo)\\s+app\\s+(.+)$", I).find(s)?.let {
            return Command.FindApps(it.groupValues[1].trim())
        }

        // YouTube (explicit)
        Regex("^(?:play|search|chalao|bajao|chala do|baja do|sunao)\\s+(.+?)\\s+(?:on|par|pe|in|me|mein)\\s+youtube$", I)
            .find(s)?.let { return Command.YouTube(it.groupValues[1].trim()) }
        Regex("^youtube\\s+(?:par|pe|me|mein|on)\\s+(.+)$", I).find(s)?.let {
            return Command.YouTube(it.groupValues[1].trim())
        }

        // Open app
        if (words(s) <= 6) {
            Regex("^(?:open|launch|start|kholo|khol do)\\s+(.+)$", I).find(s)?.let {
                return Command.OpenApp(it.groupValues[1].trim())
            }
            Regex("^(.+?)\\s+(?:kholo|khol do|khol|open karo|open kar do|open|chalu karo|start karo)$", I).find(s)?.let {
                return Command.OpenApp(it.groupValues[1].trim())
            }
        }

        // Screen click
        if (words(s) <= 6) {
            Regex("^(?:click on|tap on|click|tap|press|dabao)\\s+(.+)$", I).find(s)?.let {
                return Command.ClickText(it.groupValues[1].trim())
            }
            Regex("^(.+?)\\s+(?:dabao|click karo|tap karo|press karo)$", I).find(s)?.let {
                return Command.ClickText(it.groupValues[1].trim())
            }
        }

        // YouTube (implicit: "play X", "X bajao")
        if (words(s) <= 10) {
            Regex("^(?:play|bajao|chalao|baja do|chala do)\\s+(.+)$", I).find(s)?.let {
                return Command.YouTube(it.groupValues[1].trim())
            }
            Regex("^(.+?)\\s+(?:bajao|chalao|baja do|chala do|sunao|play karo|play kar do)$", I).find(s)?.let {
                return Command.YouTube(it.groupValues[1].trim())
            }
        }
        return null
    }

    private fun parseTime(t: String): Pair<Int, Int>? {
        for (m in Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm|baje|bje)?").findAll(t)) {
            val suffix = m.groupValues[3]
            val hasMin = m.groupValues[2].isNotEmpty()
            if (suffix.isEmpty() && !hasMin) continue
            var h = m.groupValues[1].toInt()
            val min = if (hasMin) m.groupValues[2].toInt() else 0
            val pm = suffix == "pm" || t.contains("shaam") || t.contains("raat") || t.contains("dopahar")
            val am = suffix == "am" || t.contains("subah")
            if (pm && h < 12) h += 12
            if (am && h == 12) h = 0
            if (t.contains("raat") && h == 12) h = 0
            if (h in 0..23 && min in 0..59) return h to min
        }
        return null
    }
}
