package com.kalu.ai

import android.Manifest
import android.content.Context
import android.content.Intent
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
        val autoSendAfterMs: Long = 0L
    ) : MsgPlan()
}

object Messaging {
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

    private fun pick(ctx: Context, name: String): Pair<ContactHit?, String?> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) {
            return null to "Contacts permission chahiye. KALU ke Permissions guide mein allow karo."
        }
        val hits = findContacts(ctx, name)
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
                if (sendSms(ctx, hit.number, body)) "SMS bhej diya: ${hit.name} ko. (Delivery ki gaarantee nahi, SMS app mein dekh lena.)"
                else "SMS nahi bhej paya."
            }
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
            autoSendAfterMs = 4000L
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
    private fun sendSms(ctx: Context, number: String, body: String): Boolean = try {
        val sm: SmsManager = (if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else null)
            ?: SmsManager.getDefault()
        val parts = sm.divideMessage(body)
        if (parts.size > 1) sm.sendMultipartTextMessage(number, null, parts, null, null)
        else sm.sendTextMessage(number, null, body, null, null)
        true
    } catch (e: Exception) {
        false
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
