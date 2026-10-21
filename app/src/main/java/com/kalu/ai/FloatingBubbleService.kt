package com.kalu.ai

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Floating mic bubble drawn over other apps (needs "Display over other apps").
 * The mic only listens after YOU tap the bubble, the bubble changes colour while listening,
 * and the notification has a Stop button. Nothing is recorded in the background.
 */
class FloatingBubbleService : Service() {

    companion object {
        val running = MutableStateFlow(false)
        private const val CHANNEL = "kalu_bubble"
        private const val NOTIF_ID = 1002
        private const val ACTION_STOP = "com.kalu.ai.STOP_BUBBLE"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, FloatingBubbleService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, FloatingBubbleService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var notes: NotesStore
    private lateinit var voice: VoiceController
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private var bubble: BubbleView? = null
    private var label: TextView? = null
    private var voiceState = VoiceState.IDLE
    private var awaiting: (() -> Unit)? = null
    private var feel = Feel.NEUTRAL
    private var awaitingAt = 0L

    private val sensitive = listOf(
        "send", "bhej", "delete", "hatao", "remove", "pay", "buy", "purchase", "order", "checkout",
        "confirm", "transfer", "submit", "post", "install", "uninstall", "sign out", "logout", "call", "reset", "format"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(applicationContext)
        notes = NotesStore(applicationContext)
        voice = VoiceController(
            applicationContext,
            onText = { handleSpoken(it) },
            onState = {
                voiceState = it
                bubble?.state = it
                bubble?.invalidate()
            },
            onNotice = { showLabel(it) },
            onContinuousChanged = {}
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (bubble != null) return START_NOT_STICKY
        try {
            goForeground()
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        createBubble()
        running.value = true
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "KALU bubble", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingBubbleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("KALU bubble ON")
            .setContentText("Bubble par tap karke bolo. Band karne ke liye Stop dabao.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(0, "Stop", stopPi).build())
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun createBubble() {
        val view = BubbleView(this)
        val size = dp(64)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(12)
        lp.y = dp(220)

        view.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x; startY = lp.y
                        touchX = e.rawX; touchY = e.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - touchX
                        val dy = e.rawY - touchY
                        if (abs(dx) > 10 || abs(dy) > 10) moved = true
                        if (moved) {
                            lp.x = startX + dx.toInt()
                            lp.y = startY + dy.toInt()
                            wm.updateViewLayout(v, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) onTap()
                }
                return true
            }
        })
        wm.addView(view, lp)
        bubble = view
    }

    private fun lang() = if (prefs.language == "english") "en-IN" else "hi-IN"

    private fun onTap() {
        if (voiceState == VoiceState.LISTENING) {
            voice.stop()
            return
        }
        val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!ok) {
            showLabel("Mic permission nahi hai. KALU app kholke allow karo.")
            return
        }
        showLabel("Sun raha hoon...")
        voice.listenOnce(lang())
    }

    // ---------- showing text ----------

    private fun showLabel(msg: String) {
        handler.removeCallbacksAndMessages(null)
        var tv = label
        if (tv == null) {
            tv = TextView(this).apply {
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                maxWidth = (resources.displayMetrics.widthPixels * 0.85f).toInt()
                background = GradientDrawable().apply {
                    setColor(0xEE1A2640.toInt())
                    cornerRadius = dp(18).toFloat()
                }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.y = dp(90)
            wm.addView(tv, lp)
            label = tv
        }
        tv.text = msg
        val delay = (4000L + msg.length * 60L).coerceAtMost(20000L)
        handler.postDelayed({ hideLabel() }, delay)
    }

    private fun hideLabel() {
        label?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        label = null
    }

    private fun reply(msg: String) {
        showLabel(msg)
        voice.speak(msg)
    }

    // ---------- understanding what you said ----------

    private fun handleSpoken(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        showLabel("🗣 $text")
        feel = FeelDetector.detect(text)
        voice.setTone(feel.rate, feel.pitch)
        val pendingAction = awaiting
        if (pendingAction != null) {
            awaiting = null
            if (System.currentTimeMillis() - awaitingAt < 60000 && isYes(text)) pendingAction()
            else reply("Theek hai, cancel kar diya.")
            return
        }
        val cmd = CommandRouter.parse(text)
        if (cmd != null && runCommand(cmd)) return
        askGemini(text)
    }

    /** Returns false if the text should go to Gemini instead. */
    private fun runCommand(cmd: Command): Boolean {
        when (cmd) {
            is Command.OpenApp -> {
                val hit = AppLauncher.find(this, cmd.name).firstOrNull() ?: return false
                if (AppLauncher.launch(this, hit)) reply("${hit.label} khol raha hoon.")
                else reply("${hit.label} khul nahi paya.")
            }
            is Command.FindApps -> {
                val r = AppLauncher.find(this, cmd.query).take(5)
                reply(if (r.isEmpty()) "Koi app nahi mila." else "Mile: " + r.joinToString(", ") { it.label })
            }
            is Command.YouTube -> {
                if (YouTubeHelper.search(this, cmd.query)) reply("YouTube par ${cmd.query} search khol diya.")
                else reply("YouTube nahi khul paya.")
            }
            is Command.SetAlarm -> {
                if (AlarmHelper.setAlarm(this, cmd.hour, cmd.minute, "KALU reminder"))
                    reply("Clock app khol diya. ${String.format(Locale.US, "%02d:%02d", cmd.hour, cmd.minute)} ka alarm wahan confirm karo.")
                else reply("Alarm app nahi khul paya.")
            }
            is Command.SetTimer -> {
                if (AlarmHelper.setTimer(this, cmd.seconds, "KALU timer")) reply("Timer taiyaar hai, Clock app mein confirm karo.")
                else reply("Timer nahi lag paya.")
            }
            is Command.SaveNote -> {
                val list = notes.load().toMutableList()
                list.add(0, Note(System.currentTimeMillis(), cmd.text, System.currentTimeMillis()))
                notes.save(list)
                reply("Note save kar diya.")
            }
            is Command.ShowNotes -> {
                val list = notes.load().take(5)
                reply(if (list.isEmpty()) "Abhi koi note nahi hai." else "Tumhare notes: " + list.joinToString(". ") { it.text.take(60) })
            }
            is Command.SendSms -> planMessage(Messaging.sms(this, cmd.name, cmd.body))
            is Command.SendWhatsApp -> planMessage(Messaging.whatsapp(this, cmd.name, cmd.body))
            else -> screen(cmd)
        }
        return true
    }

    private fun screen(cmd: Command) {
        val svc = KaluAccess.service
        if (svc == null) {
            reply("Screen control ke liye Accessibility service ON honi chahiye.")
            return
        }
        if (!KaluAccess.armed.value) {
            reply("Screen control OFF hai. KALU app mein switch ON karo.")
            return
        }
        if (cmd is Command.SendMessage) {
            ask("Ye message bhej dun?") { reply(svc.execute(cmd)) }
            return
        }
        if (cmd is Command.ClickText && sensitive.any { cmd.text.lowercase(Locale.ROOT).contains(it) }) {
            ask("'${cmd.text}' dabaun? Ye sensitive ho sakta hai.") { reply(svc.execute(cmd)) }
            return
        }
        reply(svc.execute(cmd))
    }

    private fun ask(prompt: String, action: () -> Unit) {
        awaiting = action
        awaitingAt = System.currentTimeMillis()
        reply("$prompt Bubble tap karke 'haan' ya 'nahi' bolo.")
    }

    private fun isYes(text: String): Boolean {
        val w = text.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
        if (w.any { it in setOf("nahi", "nahin", "no", "mat", "cancel", "ruko") }) return false
        return w.any { it in setOf("haan", "han", "ha", "haa", "yes", "ok", "okay", "bhejo", "bhej", "karo", "kar") }
    }

    private fun planMessage(plan: MsgPlan) {
        when (plan) {
            is MsgPlan.Error -> reply(plan.message)
            is MsgPlan.Confirm -> ask(plan.prompt) {
                reply(plan.run())
                val svc = KaluAccess.service
                if (plan.autoSendAfterMs > 0 && svc != null && KaluAccess.armed.value) {
                    scope.launch {
                        delay(plan.autoSendAfterMs)
                        reply(svc.execute(Command.SendMessage))
                    }
                }
            }
        }
    }

    private fun askGemini(text: String) {
        OfflineBrain.timeOrDate(text)?.let { reply(it); return }
        val key = prefs.secure.getApiKey()
        if (key.isNullOrBlank()) {
            reply(OfflineBrain.tryReply(text) ?: "Gemini key set nahi hai. KALU app ki Settings mein key daalo.")
            return
        }
        showLabel("Soch raha hoon...")
        val system = Persona.system(prefs, Mode.CHAT, feel) +
            "\nYou are answering through a voice bubble: reply in 1 to 3 short spoken sentences, no markdown, no lists, no code blocks."
        scope.launch {
            when (val res = GeminiClient.generate(key, prefs.model, system, listOf("user" to text))) {
                is GeminiResult.Ok -> reply(res.text)
                is GeminiResult.Err -> reply(res.message)
            }
        }
    }

    override fun onDestroy() {
        running.value = false
        voice.destroy()
        handler.removeCallbacksAndMessages(null)
        hideLabel()
        bubble?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        bubble = null
        scope.cancel()
        super.onDestroy()
    }
}

private class BubbleView(ctx: Context) : View(ctx) {
    var state = VoiceState.IDLE
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        val base = when (state) {
            VoiceState.LISTENING -> 0xFF39D98A.toInt()
            VoiceState.SPEAKING -> 0xFFFFB84D.toInt()
            else -> 0xFF4DA3FF.toInt()
        }
        glow.shader = RadialGradient(
            cx, cy, r,
            intArrayOf((base and 0x00FFFFFF) or 0x88000000.toInt(), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r, glow)
        core.shader = RadialGradient(
            cx, cy, r * 0.7f,
            intArrayOf(Color.WHITE, base, 0xFF123A66.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, r * 0.7f, core)
        txt.textSize = r * 0.7f
        c.drawText("🎤", cx, cy + txt.textSize * 0.35f, txt)
    }
}
