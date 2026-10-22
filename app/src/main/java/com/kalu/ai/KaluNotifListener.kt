package com.kalu.ai

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

data class IncomingMsg(
    val pkg: String,
    val app: String,
    val sender: String,
    val text: String,
    val time: Long,
    val action: Notification.Action?
)

/**
 * Optional. Only active if you turn it on in KALU Settings AND grant Notification access in Android.
 * It watches only a few messaging apps, skips anything that looks like an OTP or password, keeps the
 * last few messages in memory only (never saved, never sent to Gemini), and is used only when you ask.
 */
class KaluNotifListener : NotificationListenerService() {
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prefs.notifAccess) return
        val app = NotifHelper.apps[sbn.packageName] ?: return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = n.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (sender.isEmpty() || text.isEmpty() || NotifHelper.looksSensitive(text)) return
        val action = n.actions?.firstOrNull { a -> a.remoteInputs?.isNotEmpty() == true }
        NotifHelper.add(IncomingMsg(sbn.packageName, app, sender, text.take(200), sbn.postTime, action))
    }

    override fun onListenerDisconnected() {
        NotifHelper.clear()
    }
}

object NotifHelper {
    val apps = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.google.android.apps.messaging" to "Messages",
        "com.samsung.android.messaging" to "Messages",
        "com.instagram.android" to "Instagram"
    )

    private val items = ArrayList<IncomingMsg>()

    @Synchronized
    fun add(m: IncomingMsg) {
        items.removeAll { it.pkg == m.pkg && it.sender == m.sender && it.text == m.text }
        items.add(m)
        while (items.size > 30) items.removeAt(0)
    }

    @Synchronized
    fun clear() {
        items.clear()
    }

    @Synchronized
    private fun snapshot(): List<IncomingMsg> = items.toList()

    fun looksSensitive(t: String): Boolean =
        Regex("(otp|verification code|one[- ]?time|password|passcode|cvv|\\bpin\\b|do not share)", RegexOption.IGNORE_CASE)
            .containsMatchIn(t)

    fun isListenerEnabled(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun readLatest(ctx: Context): String {
        if (!Prefs(ctx).notifAccess) return "Messages padhne ki setting OFF hai. KALU Settings mein 'Messages padhne do' ON karo."
        if (!isListenerEnabled(ctx)) return "Android mein Notification access ON nahi hai. Permissions guide mein 'Notification access' allow karo."
        val list = snapshot().takeLast(5).reversed()
        if (list.isEmpty()) return "Abhi koi naya message nahi hai (sirf wahi jo KALU ke ON hone ke baad aaye)."
        return "Naye messages:\n" + list.joinToString("\n") { "• ${it.sender} (${it.app}): ${it.text.take(100)}" }
    }

    fun reply(ctx: Context, name: String, body: String): MsgPlan {
        if (!Prefs(ctx).notifAccess || !isListenerEnabled(ctx)) {
            return MsgPlan.Error("Reply ke liye Notification access ON chahiye (KALU Settings aur Permissions guide dekho).")
        }
        val m = snapshot().lastOrNull { it.action != null && it.sender.contains(name, ignoreCase = true) }
            ?: return MsgPlan.Error("'$name' ka koi taaza message nahi mila jisko reply kar sakun.")
        val action = m.action ?: return MsgPlan.Error("Is message par reply ka option nahi hai.")
        return MsgPlan.Confirm(
            "${m.sender} ko ${m.app} par reply bhejun?\n\n\"$body\"",
            {
                try {
                    val inputs = action.remoteInputs
                    val intent = Intent()
                    val results = Bundle()
                    for (ri in inputs) results.putCharSequence(ri.resultKey, body)
                    RemoteInput.addResultsToIntent(inputs, intent, results)
                    action.actionIntent.send(ctx, 0, intent)
                    "Reply bhej diya: ${m.sender} ko. ✅"
                } catch (e: Exception) {
                    "Reply nahi bhej paya (notification hat gayi ho sakti hai)."
                }
            }
        )
    }
}
