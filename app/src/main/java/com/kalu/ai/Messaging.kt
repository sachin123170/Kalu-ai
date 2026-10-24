package com.kalu.ai

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.util.Locale

data class ContactHit(val name: String, val number: String)

/** Result of preparing a message. Nothing is sent until the user confirms and run() is called. */
sealed class MsgPlan {
    data class Error(val message: String) : MsgPlan()
    data class Confirm(
        val prompt: String,
        val run: () -> String,
        /** > 0 means: after run(), try pressing the app's Send button after this many ms (needs screen control ON). */
        val autoSendAfterMs: Long = 0L,
        val contact: String = ""
    ) : MsgPlan()
}

object Messaging {
    private val resultListeners = java.util.concurrent.CopyOnWriteArrayList<(String) -> Unit>()

    /** Listeners get the REAL outcome of an SMS send, on the main thread. */
    fun addResultListener(l: (String) -> Unit) {
        resultListeners.addIfAbsent(l)
    }

    fun removeResultListener(l: (String) -> Unit) {
        resultListeners.remove(l)
    }

    private fun emitResult(msg: String) {
        ActivityLog.add(msg)
        for (l in resultListeners) l(msg)
    }

    private fun has(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun digits(s: String) = s.filter { it.isDigit() }

    fun findContacts(ctx: Context, query: String): List<ContactHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<ContactHit>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$q%"),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val hit = ContactHit(c.getString(0) ?: "", c.getString(1) ?: "")
                    val dup = out.any { it.name == hit.name && digits(it.number) == digits(hit.number) }
                    if (hit.number.isNotBlank() && !dup) out.add(hit)
                }
            }
        } catch (e: Exception) {
        }
        val ql = q.lowercase(Locale.ROOT)
        return out.sortedBy {
            val n = it.name.lowercase(Locale.ROOT)
            when {
                n == ql -> 0
                n.startsWith(ql) -> 1
                else -> 2
            }
        }
    }

    /** Tries the name as spoken, then its Latin transliteration, then shorter forms ("vinaya" becomes "vinay"). */
    private fun lookup(ctx: Context, name: String): List<ContactHit> {
        val first = findContacts(ctx, name)
        if (first.isNotEmpty()) return first
        var q = Translit.nameToLatin(name)
        var hits = if (q != name) findContacts(ctx, q) else emptyList()
        var tries = 0
        while (hits.isEmpty() && q.length > 3 && tries < 2) {
            q = q.dropLast(1)
            hits = findContacts(ctx, q)
            tries++
        }
        return hits
    }

    fun pick(ctx: Context, name: String): Pair<ContactHit?, String?> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) {
            return null to "Contacts permission chahiye. KALU ke Permissions guide mein allow karo."
        }
        val hits = lookup(ctx, name)
        if (hits.isEmpty()) return null to "'$name' naam ka contact nahi mila."
        val best = hits.first()
        val names = hits.map { it.name.lowercase(Locale.ROOT) }.distinct()
        val exact = best.name.equals(name.trim(), ignoreCase = true)
        if (names.size > 1 && !exact) {
            return null to "Kai contacts mile: " + names.take(4).joinToString(", ") + ". Poora naam bolo."
        }
        return best to null
    }

    fun sms(ctx: Context, name: String, body: String): MsgPlan {
        if (!has(ctx, Manifest.permission.SEND_SMS)) {
            return MsgPlan.Error("SMS permission chahiye. KALU ke Permissions guide mein allow karo.")
        }
        val (hit, err) = pick(ctx, name)
        if (hit == null) return MsgPlan.Error(err ?: "Contact nahi mila.")
        return MsgPlan.Confirm(
            "${hit.name} (${hit.number}) ko SMS bhejun?\n\n\"$body\"",
            {
                if (sendSms(ctx, hit.number, body, hit.name)) "SMS system ko de diya: ${hit.name} ko. Asli result abhi batata hoon..."
                else "SMS nahi bhej paya."
            },
            contact = hit.name
        )
    }

    fun whatsapp(ctx: Context, name: String, body: String): MsgPlan {
        val (hit, err) = pick(ctx, name)
        if (hit == null) return MsgPlan.Error(err ?: "Contact nahi mila.")
        return MsgPlan.Confirm(
            "${hit.name} (${hit.number}) ko WhatsApp par ye message bhejun?\n\n\"$body\"",
            {
                if (openWhatsApp(ctx, waNumber(hit.number), body)) "WhatsApp chat khol di, message likha hua hai."
                else "WhatsApp nahi khul paya."
            },
            autoSendAfterMs = 4000L,
            contact = hit.name
        )
    }

    /** International digits for wa.me. Assumes a 10-digit number without country code is Indian (+91). */
    fun waNumber(raw: String): String {
        val d = digits(raw)
        return when {
            raw.trim().startsWith("+") -> d
            d.startsWith("00") -> d.drop(2)
            d.length == 10 -> "91$d"
            d.length == 11 && d.startsWith("0") -> "91" + d.drop(1)
            else -> d
        }
    }

    @Suppress("DEPRECATION")
    private fun sendSms(ctx: Context, number: String, body: String, label: String): Boolean {
        val app = ctx.applicationContext
        var receiver: BroadcastReceiver? = null
        return try {
            val sm: SmsManager = (if (Build.VERSION.SDK_INT >= 31) app.getSystemService(SmsManager::class.java) else null)
                ?: SmsManager.getDefault()
            val parts = sm.divideMessage(body)
            val total = parts.size
            val action = "com.kalu.ai.SMS_SENT." + System.nanoTime()
            var done = 0
            var badCode = 0
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    done++
                    if (resultCode != Activity.RESULT_OK) badCode = resultCode
                    if (done >= total) {
                        try {
                            app.unregisterReceiver(this)
                        } catch (e: Exception) {
                        }
                        val msg = if (badCode == 0) {
                            "SMS network ko pahunch gaya: $label ko. \u2705 (Samne wale tak delivery ki gaarantee nahi.)"
                        } else {
                            val why = when (badCode) {
                                SmsManager.RESULT_ERROR_NO_SERVICE -> "network ya SIM service nahi hai"
                                SmsManager.RESULT_ERROR_RADIO_OFF -> "airplane mode ya radio band hai"
                                else -> "phone ya SIM ne SMS roka (code $badCode)"
                            }
                            "SMS FAIL ho gaya ($label ko): $why. \u274C"
                        }
                        emitResult(msg)
                    }
                }
            }
            receiver = r
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            else app.registerReceiver(r, IntentFilter(action))
            val pi = PendingIntent.getBroadcast(
                app, 0, Intent(action).setPackage(app.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (total > 1) sm.sendMultipartTextMessage(number, null, parts, ArrayList(List(total) { pi }), null)
            else sm.sendTextMessage(number, null, body, pi, null)
            true
        } catch (e: Exception) {
            receiver?.let {
                try {
                    app.unregisterReceiver(it)
                } catch (x: Exception) {
                }
            }
            false
        }
    }

    private fun openWhatsApp(ctx: Context, number: String, text: String): Boolean {
        val uri = Uri.parse("https://wa.me/$number?text=" + Uri.encode(text))
        for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b", null)) {
            val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pkg != null) i.setPackage(pkg)
            try {
                ctx.startActivity(i)
                return true
            } catch (e: Exception) {
            }
        }
        return false
    }
}
