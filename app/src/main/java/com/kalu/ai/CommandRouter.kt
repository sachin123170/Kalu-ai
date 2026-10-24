package com.kalu.ai

import java.util.Calendar
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
    object ReadMessages : Command()
    data class ReplyTo(val name: String, val body: String) : Command()
    data class Remember(val text: String) : Command()
    object ShowMemory : Command()
    data class Forget(val text: String) : Command()
    data class AddEvent(val title: String, val startMillis: Long) : Command()
    data class CallContact(val name: String) : Command()
    data class Navigate(val place: String) : Command()
    data class Media(val action: String) : Command()
    object Briefing : Command()
    object SummarizeScreen : Command()
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
    private val showMemorySet = setOf("meri yaadein", "mujhe kya yaad hai", "tumhe kya yaad hai", "kya yaad hai", "what do you remember")
    private val briefingSet = setOf("briefing", "subah ka briefing", "aaj ka briefing", "aaj ka plan", "daily briefing")
    private val mediaPause = setOf("pause", "pause karo", "gaana roko", "gaana pause karo", "music pause", "music roko")
    private val mediaPlay = setOf("play karo", "gaana chalu karo", "music chalu karo")
    private val mediaNext = setOf("next gaana", "agla gaana", "next song")
    private val mediaPrev = setOf("previous gaana", "pichla gaana", "previous song")

    private val eventStop = setOf(
        "calendar", "mein", "me", "add", "karo", "kar", "do", "daal", "daalo", "dal", "event", "bana", "banao",
        "kal", "aaj", "parso", "baje", "bje", "pm", "am", "subah", "shaam", "raat", "dopahar", "ko", "ka", "ki", "ke",
        "set", "jod", "for", "at", "on", "tomorrow", "today", "save", "a", "the", "to"
    )

    private fun eventTitle(t: String): String {
        val timeLike = Regex("\\d{1,2}([:.]\\d{2})?(am|pm)?")
        val kept = t.split(Regex("\\s+")).filter { it.isNotEmpty() && it !in eventStop && !it.matches(timeLike) }
        val title = kept.joinToString(" ").trim()
        return if (title.isEmpty()) "KALU event" else title.replaceFirstChar { it.uppercase() }
    }

    private fun eventTime(t: String, h: Int, m: Int): Long {
        val c = Calendar.getInstance()
        val off = when {
            t.contains("parso") -> 2
            Regex("\\b(kal|tomorrow)\\b").containsMatchIn(t) -> 1
            else -> 0
        }
        c.add(Calendar.DAY_OF_YEAR, off)
        c.set(Calendar.HOUR_OF_DAY, h)
        c.set(Calendar.MINUTE, m)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (off == 0 && c.timeInMillis < System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    private val readMsgSet = setOf(
        "naye messages", "naye messages padho", "messages padho", "message padho", "koi message aaya",
        "kisi ka message aaya", "notifications padho", "whatsapp messages padho"
    )
    private val sendSet = setOf("send karo", "send kar do", "send", "bhej do", "bhejo", "send message")
    private val showNotes = setOf("mere notes", "notes dikhao", "show notes", "notes", "my notes", "notes dikha do")

    private val bodyLead = Regex("^ki\\s+(?:ye\\s+)?", RegexOption.IGNORE_CASE)

    /** "ki main late hoon" becomes "main late hoon". Keeps the original if nothing is left. */
    private fun cleanBody(b: String): String {
        val c = bodyLead.replaceFirst(b.trim(), "").trim()
        return if (c.isEmpty()) b.trim() else c
    }

    private fun words(s: String) = s.trim().split(Regex("\\s+")).size

    private val channelWa = setOf("whatsapp", "watsapp", "whatsap", "wa")
    private val sendVerbs = setOf("bhejo", "bhej", "karo", "kar", "likho", "bolo", "bol", "send", "bhijwao")
    private val fillers = setOf("pe", "par", "me", "mein", "se", "ke", "liye", "ki", "ye", "is", "ek", "do", "aur")
    private val stopWords: Set<String> = channelWa + setOf("sms", "message", "msg") + sendVerbs + fillers

    /**
     * Natural sentences like "watsapp pe sms karo paras ko ki mai nahi arha" or
     * "paras ko message bhejo ki main late hoon". Needs: a channel word, a send verb, "NAME ko", and a body.
     */
    private fun flexibleMessage(s: String): Command? {
        val tokens = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val lower = tokens.map { it.lowercase(Locale.ROOT).trim(':', '-', ',') }
        val koIdx = lower.indexOf("ko")
        if (koIdx <= 0) return null
        val hasWa = lower.any { it in channelWa }
        val hasSms = lower.any { it == "sms" }
        val hasMsg = lower.any { it == "message" || it == "msg" }
        if (!hasWa && !hasSms && !hasMsg) return null
        if (lower.none { it in sendVerbs }) return null

        val nameTokens = ArrayList<String>()
        var i = koIdx - 1
        while (i >= 0 && nameTokens.size < 2 && lower[i] !in stopWords) {
            nameTokens.add(0, tokens[i])
            i--
        }
        if (nameTokens.isEmpty()) return null

        var j = koIdx + 1
        while (j < tokens.size && lower[j] in stopWords) {
            val hadColon = tokens[j].endsWith(":")
            j++
            if (hadColon) break
        }
        val body = tokens.drop(j).joinToString(" ").trimStart(':', '-', ' ').trim()
        if (body.isEmpty()) return null
        val name = nameTokens.joinToString(" ")
        return if (hasWa || !hasSms) Command.SendWhatsApp(name, body) else Command.SendSms(name, body)
    }

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
            in readMsgSet -> return Command.ReadMessages
            in showMemorySet -> return Command.ShowMemory
            in briefingSet -> return Command.Briefing
            in mediaPause -> return Command.Media("pause")
            in mediaPlay -> return Command.Media("play")
            in mediaNext -> return Command.Media("next")
            in mediaPrev -> return Command.Media("prev")
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
            .find(s)?.let { return Command.SendWhatsApp(it.groupValues[1].trim(), cleanBody(it.groupValues[2])) }
        Regex("^(?:whatsapp|watsapp)\\s+(?:to\\s+)?(\\S+)\\s*[:\\-]\\s*(.+)$", I)
            .find(s)?.let { return Command.SendWhatsApp(it.groupValues[1].trim(), cleanBody(it.groupValues[2])) }
        Regex("^(.+?)\\s+ko\\s+sms\\s+(?:bhejo|karo|bhej do|kar do|likho)\\s*[:\\-]?\\s*(.+)$", I)
            .find(s)?.let { return Command.SendSms(it.groupValues[1].trim(), cleanBody(it.groupValues[2])) }
        Regex("^sms\\s+(?:to\\s+)?(\\S+)\\s*[:\\-]\\s*(.+)$", I)
            .find(s)?.let { return Command.SendSms(it.groupValues[1].trim(), cleanBody(it.groupValues[2])) }
        Regex("^(.+?)\\s+ko\\s+(?:message|msg|text)\\s+(?:bhejo|karo|bhej do|kar do|likho)\\s*[:\\-]?\\s*(.+)$", I)
            .find(s)?.let { return Command.SendWhatsApp(it.groupValues[1].trim(), cleanBody(it.groupValues[2])) }

        flexibleMessage(s)?.let { return it }

        // Type into the focused text box (screen control)
        Regex("^(?:type|likho|write|isme likho|yaha likho)\\s*[:\\-]\\s*(.+)$", I).find(s)?.let {
            return Command.TypeText(it.groupValues[1].trim())
        }
        Regex("^(?:type karo|type kar do|likh do|likho)\\s+(.+)$", I).find(s)?.let {
            return Command.TypeText(it.groupValues[1].trim())
        }

        // Memory
        Regex("^(?:yaad rakho|yaad rakh|yaad rakhna|yaad kar lo|remember(?: that)?)\\s*[:\\-]?\\s*(.+)$", I).find(s)?.let {
            return Command.Remember(it.groupValues[1].trim())
        }
        Regex("^(?:bhool jao|bhul jao|forget)\\s*[:\\-]?\\s*(.+)$", I).find(s)?.let {
            return Command.Forget(it.groupValues[1].trim())
        }

        // Calendar event (opens the Calendar app pre-filled; you press Save)
        if (t.contains("calendar") && listOf("add", "daal", "bana", "jod", "set", "event", "save").any { t.contains(it) }) {
            parseTime(t)?.let { hm -> return Command.AddEvent(eventTitle(t), eventTime(t, hm.first, hm.second)) }
        }

        // Call (opens the dialer; you press the call button)
        Regex("^(.+?)\\s+ko\\s+(?:call|phone|fone)\\s+(?:karo|lagao|kar do|laga do)$", I).find(s)?.let {
            return Command.CallContact(it.groupValues[1].trim())
        }
        if (words(s) <= 4) {
            Regex("^(?:call|phone)\\s+(.+)$", I).find(s)?.let { return Command.CallContact(it.groupValues[1].trim()) }
        }

        // Maps
        Regex("^(?:navigate to|navigate|directions to|route to)\\s+(.+)$", I).find(s)?.let {
            return Command.Navigate(it.groupValues[1].trim())
        }
        Regex("^(.+?)\\s+(?:ka raasta|ka rasta|ka route|tak ka raasta|jane ka raasta)(?:\\s+(?:batao|dikhao|bata do|dikha do))?$", I)
            .find(s)?.let { return Command.Navigate(it.groupValues[1].trim()) }

        // Summarize what is on screen (needs screen control; confirmed first)
        if (words(s) <= 8 && Regex("(screen|page).*(summary|summarize|samjhao|saaransh)|(summary|summarize).*(screen|page)", I).containsMatchIn(s)) {
            return Command.SummarizeScreen
        }

        // Reply to a message notification (confirmed first)
        Regex("^(.+?)\\s+ko\\s+reply\\s+(?:karo|bhejo|do|kar do)\\s*[:\\-]?\\s*(.+)$", I).find(s)?.let {
            return Command.ReplyTo(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        Regex("^reply\\s+(?:to\\s+)?(\\S+)\\s*[:\\-]\\s*(.+)$", I).find(s)?.let {
            return Command.ReplyTo(it.groupValues[1].trim(), it.groupValues[2].trim())
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
