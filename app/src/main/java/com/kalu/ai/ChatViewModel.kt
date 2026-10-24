package com.kalu.ai

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    class Pending(val message: String, val action: () -> Unit)

    private val ctx: Context = app.applicationContext
    private val chatStore = ChatStore(ctx)
    private val notesStore = NotesStore(ctx)
    val prefs = Prefs(ctx)

    val messages = mutableStateListOf<ChatMessage>()
    val notes = mutableStateListOf<Note>()

    var typing by mutableStateOf(false)
        private set
    var mode by mutableStateOf(Mode.CHAT)
    var voiceState by mutableStateOf(VoiceState.IDLE)
        private set
    var continuous by mutableStateOf(false)
        private set
    var pending by mutableStateOf<Pending?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
    var feel by mutableStateOf(Feel.NEUTRAL)
        private set
    var attachedImage by mutableStateOf<ByteArray?>(null)

    private var nextId = 1L

    private val voice = VoiceController(
        ctx,
        onText = { onVoiceText(it) },
        onState = { voiceState = it },
        onNotice = { notice = it },
        onContinuousChanged = { continuous = it }
    )

    private val resultListener: (String) -> Unit = { msg ->
        botSay(msg, speak = !FloatingBubbleService.running.value)
    }

    init {
        OfflineBrain.name = prefs.assistantName
        ToolRouter.init(ctx)
        ActivityLog.init(ctx)
        Messaging.addResultListener(resultListener)
        if (prefs.saveHistory) messages.addAll(chatStore.load())
        notes.addAll(notesStore.load())
        nextId = (messages.maxOfOrNull { it.id } ?: 0L) + 1
        if (messages.isEmpty()) addMsg(OfflineBrain.greeting(), false)
    }

    // ---------- messages ----------

    private fun addMsg(text: String, fromUser: Boolean) {
        messages.add(ChatMessage(nextId++, fromUser, text, System.currentTimeMillis()))
        persist()
    }

    private fun persist() {
        if (!prefs.saveHistory) return
        val snapshot = messages.toList()
        viewModelScope.launch(Dispatchers.IO) { chatStore.save(snapshot) }
    }

    private fun botSay(text: String, speak: Boolean = true) {
        addMsg(text, false)
        if (speak && (prefs.speakReplies || voice.continuous)) voice.speak(text) else voice.resume()
    }

    private val yesWords = setOf(
        "haan", "han", "ha", "haa", "yes", "ok", "okay", "theek hai", "thik hai", "kar do", "bhej do",
        "bhejo", "send", "send karo", "send kar do", "confirm", "pakka", "haan bhej do", "haan kar do", "haan bhai"
    )
    private val noWords = setOf(
        "nahi", "nahin", "na", "no", "cancel", "ruko", "ruk jao", "mat bhejo", "rehne do", "reh ne do", "stop", "band karo"
    )

    private val stopPhrases = setOf(
        "emergency stop", "emergency", "sab band karo", "sab kuch band karo", "kalu ruko", "kalu stop", "sab ruko"
    )
    private val resumePhrases = setOf(
        "kalu chalu karo", "kalu resume", "resume kalu", "emergency stop hatao", "emergency hatao"
    )
    private val logPhrases = setOf("activity log", "log dikhao", "activity dikhao", "kalu ne kya kiya")

    private fun emergencyStop() {
        ToolRouter.paused = true
        pending = null
        KaluAccess.disarm(ctx)
        ActivityLog.add("EMERGENCY STOP")
        botSay("Emergency stop ON. Saare actions band. Wapas chalu karne ke liye 'kalu chalu karo' bolo.", speak = false)
    }

    private fun langCode() = if (prefs.language == "english") "en-IN" else "hi-IN"

    // ---------- voice ----------

    fun micTapped() {
        if (voiceState == VoiceState.LISTENING) voice.stop()
        else if (!typing) voice.listenOnce(langCode())
    }

    fun toggleContinuous() {
        if (continuous) voice.stop() else voice.startContinuous(langCode())
    }

    fun stopVoice() = voice.stop()

    fun onAppBackground() = voice.stop()

    private fun onVoiceText(raw: String) {
        var text = raw.trim()
        if (prefs.wakeEnabled && voice.continuous) {
            val w = prefs.wakePhrase.lowercase(Locale.getDefault()).trim()
            val idx = text.lowercase(Locale.getDefault()).indexOf(w)
            if (w.isNotEmpty()) {
                if (idx < 0) {
                    voice.resume(); return
                }
                text = text.substring(minOf(idx + w.length, text.length)).trim(' ', ',', '.')
                if (text.isEmpty()) {
                    voice.resume(); return
                }
            }
        }
        send(text)
    }

    // ---------- sending ----------

    fun send(raw: String) {
        val img = attachedImage
        val text = raw.trim().ifEmpty { if (img != null) "Is photo mein kya hai? Hinglish mein batao." else "" }
        if (text.isEmpty()) return
        if (typing) {
            voice.resume(); return
        }
        addMsg(if (img != null) "📷 $text" else text, true)
        if (pending != null) {
            val ans = text.lowercase(Locale.ROOT).trim(' ', '.', '!', '?', ',')
            if (ans in yesWords) {
                confirmPending(); return
            }
            pending = null
            if (ans in noWords) {
                botSay("Theek hai, cancel kar diya."); return
            }
        }
        val low = text.lowercase(Locale.ROOT).trim(' ', '.', '!', '?', ',')
        if (low in stopPhrases) {
            emergencyStop(); return
        }
        if (low in resumePhrases) {
            ToolRouter.paused = false
            ActivityLog.add("Emergency stop hata diya")
            botSay("Theek hai, KALU wapas chalu hai.")
            return
        }
        if (low in logPhrases) {
            botSay(ActivityLog.recent(), speak = false); return
        }
        if (ToolRouter.paused) {
            botSay("KALU abhi emergency stop mein hai. 'kalu chalu karo' bolo.", speak = false); return
        }
        feel = FeelDetector.detect(text)
        voice.setTone(feel.rate, feel.pitch)
        if (img != null) {
            attachedImage = null
            askBrain(text, image = img)
            return
        }
        val cmd = CommandRouter.parse(text)
        if (cmd != null && handleCommand(cmd)) return
        askBrain(text)
    }

    /** Returns false if the command should fall through to Gemini. */
    private fun handleCommand(cmd: Command): Boolean {
        when (cmd) {
            is Command.OpenApp -> {
                val hit = AppLauncher.find(ctx, cmd.name).firstOrNull() ?: return false
                if (AppLauncher.launch(ctx, hit)) botSay("${hit.label} khol raha hoon. 🚀")
                else botSay("${hit.label} ko main khol nahi paya. Is app ka launch intent supported nahi hai.")
            }
            is Command.FindApps -> {
                val r = AppLauncher.find(ctx, cmd.query).take(6)
                botSay(if (r.isEmpty()) "Koi app nahi mila: ${cmd.query}" else "Mile hue apps:\n" + r.joinToString("\n") { "• ${it.label}" })
            }
            is Command.YouTube -> {
                if (YouTubeHelper.search(ctx, cmd.query))
                    botSay("YouTube par \"${cmd.query}\" search khol diya. Result par tap karke play karo (ads/premium ko main bypass nahi karta). 🎵")
                else botSay("YouTube nahi khul paya.")
            }
            is Command.SetAlarm -> {
                val hhmm = String.format(Locale.US, "%02d:%02d", cmd.hour, cmd.minute)
                if (AlarmHelper.setAlarm(ctx, cmd.hour, cmd.minute, "KALU reminder"))
                    botSay("Clock app khol diya, $hhmm ka alarm wahan confirm kar lo. ⏰")
                else botSay("Is phone par alarm app nahi khul paya.")
            }
            is Command.SetTimer -> {
                if (AlarmHelper.setTimer(ctx, cmd.seconds, "KALU timer"))
                    botSay("Clock app mein ${cmd.seconds} second ka timer taiyaar hai, wahan confirm karo. ⏳")
                else botSay("Timer nahi lag paya.")
            }
            is Command.SaveNote -> {
                addNote(cmd.text)
                botSay("Note save kar diya: \"${cmd.text.take(80)}\" 📝")
            }
            is Command.ShowNotes -> {
                botSay(
                    if (notes.isEmpty()) "Abhi koi note nahi hai. 'note: kuch bhi' bolke add karo."
                    else "Tumhare notes:\n" + notes.take(10).joinToString("\n") { "• ${it.text.take(100)}" },
                    speak = false
                )
            }
            is Command.Remember -> botSay(MoreActions.remember(ctx, cmd.text))
            is Command.ShowMemory -> botSay(MoreActions.showMemory(ctx), speak = false)
            is Command.Forget -> botSay(MoreActions.forget(ctx, cmd.text))
            is Command.AddEvent -> botSay(MoreActions.addEvent(ctx, cmd.title, cmd.startMillis))
            is Command.CallContact -> botSay(MoreActions.call(ctx, cmd.name))
            is Command.Navigate -> botSay(MoreActions.navigate(ctx, cmd.place))
            is Command.Media -> botSay(MoreActions.media(ctx, cmd.action), speak = false)
            is Command.Briefing -> botSay(MoreActions.briefing(ctx))
            is Command.ReadMessages -> botSay(NotifHelper.readLatest(ctx))
            is Command.ReplyTo -> planMessage(NotifHelper.reply(ctx, cmd.name, cmd.body))
            is Command.SummarizeScreen -> summarizeScreen()
            is Command.SendSms -> guarded("sms", cmd.name, cmd.body) { Messaging.sms(ctx, cmd.name, cmd.body) }
            is Command.SendWhatsApp -> guarded("whatsapp", cmd.name, cmd.body) { Messaging.whatsapp(ctx, cmd.name, cmd.body) }
            else -> screenCommand(cmd)
        }
        return true
    }

    /** Runs the safety checks, then plans the message. The send is recorded only after the user confirms. */
    private fun guarded(kind: String, name: String, body: String, make: () -> MsgPlan) {
        ToolRouter.checkMessage(kind, name, body)?.let {
            botSay(it)
            return
        }
        val plan = make()
        if (plan is MsgPlan.Confirm) {
            planMessage(plan.copy(run = {
                ToolRouter.recordSend(kind, name, body)
                plan.run()
            }))
        } else {
            planMessage(plan)
        }
    }

    private fun planMessage(plan: MsgPlan) {
        when (plan) {
            is MsgPlan.Error -> botSay(plan.message)
            is MsgPlan.Confirm -> {
                val go = {
                    botSay(plan.run())
                    if (plan.autoSendAfterMs > 0) {
                        val svc = KaluAccess.service
                        if (svc != null && KaluAccess.armed.value) {
                            viewModelScope.launch {
                                delay(plan.autoSendAfterMs)
                                botSay(svc.execute(Command.SendMessage))
                            }
                        } else {
                            botSay("Chat khul gayi aur message likha hua hai. Send tum dabao, ya Screen control ON karke 'send karo' bolo.", speak = false)
                        }
                    }
                }
                if (prefs.skipConfirm && prefs.isTrusted(plan.contact)) go()
                else {
                    pending = Pending(plan.prompt) { go() }
                    botSay(plan.prompt + "\n\nHaan bolo ya button dabao, tab bhejunga. Nahi bhejna to 'cancel' bolo.")
                }
            }
        }
    }

    private fun summarizeScreen() {
        if (KaluAccess.service == null || !KaluAccess.armed.value) {
            botSay("Screen summary ke liye Accessibility service ON aur Screen control ON chahiye.")
            return
        }
        pending = Pending("Screen ka text Gemini (Google) ko bhejun summary ke liye? Password wale boxes nahi padhe jate.") {
            botSay("Theek hai! 4 second mein padhunga. Jis app ka summary chahiye uspar chale jao.", speak = false)
            viewModelScope.launch {
                delay(4000)
                val text = KaluAccess.service?.screenText()
                if (text.isNullOrBlank()) {
                    botSay("Screen se text nahi mila (ya KALU hi screen par tha).")
                } else {
                    addMsg("📄 Screen ka summary", true)
                    askBrain("Neeche ek screen ka text hai. Uska chhota summary Hinglish mein do.", extraContext = text)
                }
            }
        }
    }

    private fun askBrain(text: String, extraContext: String? = null, image: ByteArray? = null) {
        if (image == null && extraContext == null) {
            OfflineBrain.timeOrDate(text)?.let { botSay(it); return }
        }
        val key = prefs.secure.getApiKey()
        if (key.isNullOrBlank()) {
            botSay(
                (OfflineBrain.tryReply(text) ?: OfflineBrain.fallback()) +
                    "\n\n🔑 Gemini API key set nahi hai. Settings mein apni key daalo, phir har sawaal ka jawab de paunga.",
                speak = false
            )
            return
        }
        typing = true
        val turns = buildTurns().toMutableList()
        if (extraContext != null && turns.isNotEmpty() && turns.last().first == "user") {
            turns[turns.size - 1] = "user" to (turns.last().second + "\n\n" + extraContext)
        }
        val model = prefs.model
        val system = Persona.system(prefs, mode, feel, MoreActions.factsForPrompt(ctx))
        viewModelScope.launch {
            val res = GeminiClient.generate(key, model, system, turns, image, prefs.webSearch && image == null)
            typing = false
            when (res) {
                is GeminiResult.Ok -> botSay(res.text)
                is GeminiResult.Err -> {
                    val offline = if (res.kind == ErrKind.NETWORK) OfflineBrain.tryReply(text) else null
                    botSay("⚠️ ${res.message}" + (offline?.let { "\n\n(Offline jawab) $it" } ?: ""), speak = false)
                }
            }
        }
    }

    private fun buildTurns(): List<Pair<String, String>> {
        val n = prefs.memoryTurns
        val src = messages.filter { !it.text.startsWith("⚠️") }
        val recent = if (n == 0) src.takeLast(1) else src.takeLast(n * 2 + 1)
        val out = mutableListOf<Pair<String, String>>()
        for (m in recent) {
            val role = if (m.fromUser) "user" else "model"
            if (out.isNotEmpty() && out.last().first == role) {
                out[out.size - 1] = role to (out.last().second + "\n" + m.text)
            } else out.add(role to m.text)
        }
        while (out.isNotEmpty() && out.first().first != "user") out.removeAt(0)
        return out
    }

    // ---------- screen control (AccessibilityService) ----------

    private val sensitive = listOf(
        "send", "bhej", "delete", "hatao", "remove", "pay", "buy", "purchase", "order", "checkout",
        "confirm", "transfer", "submit", "post", "install", "uninstall", "sign out", "logout", "call", "reset", "format"
    )

    private fun screenCommand(cmd: Command) {
        if (KaluAccess.service == null) {
            botSay("Screen control ke liye Accessibility service ON honi chahiye. Settings, phir Permissions guide dekho.")
            return
        }
        if (!KaluAccess.armed.value) {
            botSay("Screen control abhi OFF hai. Upar wala 'Screen control' switch ON karo, phir command bolo.")
            return
        }
        if (cmd is Command.SendMessage) {
            pending = Pending("Ye message bhej dun? (Send button dabaunga)") { runDelayed(cmd) }
            return
        }
        if (cmd is Command.ClickText && sensitive.any { cmd.text.lowercase(Locale.ROOT).contains(it) }) {
            pending = Pending("'${cmd.text}' dabana sensitive ho sakta hai (send/pay/delete jaisa). Pakka karna hai?") { runDelayed(cmd) }
            return
        }
        runDelayed(cmd)
    }

    private fun runDelayed(cmd: Command) {
        botSay("Theek hai! 4 second mein karunga. Jis app par kaam karna hai uspar chale jao. Rokne ke liye Stop dabao.", speak = false)
        viewModelScope.launch {
            delay(4000)
            val svc = KaluAccess.service
            if (!KaluAccess.armed.value || svc == null) {
                addMsg("Screen action roka gaya. 🛑", false)
                return@launch
            }
            botSay(svc.execute(cmd))
        }
    }

    fun setArmed(on: Boolean) {
        if (on) KaluAccess.arm(ctx) else KaluAccess.disarm(ctx)
    }

    fun stopControl() {
        KaluAccess.disarm(ctx)
        notice = "Screen control band kar diya."
    }

    fun confirmPending() {
        val p = pending
        pending = null
        p?.action?.invoke()
    }

    fun dismissPending() {
        pending = null
        addMsg("Theek hai, ye action cancel kar diya.", false)
    }

    // ---------- notes / privacy ----------

    private fun addNote(text: String) {
        notes.add(0, Note(System.currentTimeMillis(), text, System.currentTimeMillis()))
        notesStore.save(notes.toList())
    }

    /** Notes can also be added by the floating bubble service, so reload when the app comes back. */
    fun reloadNotes() {
        notes.clear()
        notes.addAll(notesStore.load())
    }

    fun addNoteFromUi(text: String) {
        if (text.isNotBlank()) addNote(text.trim())
    }

    fun deleteNote(id: Long) {
        notes.removeAll { it.id == id }
        notesStore.save(notes.toList())
    }

    fun clearNotes() {
        notes.clear()
        notesStore.clear()
    }

    fun clearFacts() {
        MoreActions.clearFacts(ctx)
        notice = "Yaadein clear ho gayi."
    }

    fun clearHistory() {
        messages.clear()
        viewModelScope.launch(Dispatchers.IO) { chatStore.clear() }
        addMsg(OfflineBrain.greeting(), false)
    }

    fun clearAllData() {
        clearHistory()
        clearNotes()
        MoreActions.clearFacts(ctx)
        prefs.secure.putApiKey("")
        prefs.secure.putSecret("picovoice", "")
        prefs.resetAll()
        notice = "Saara data delete ho gaya."
    }

    override fun onCleared() {
        Messaging.removeResultListener(resultListener)
        voice.destroy()
        super.onCleared()
    }
}
