package com.kalu.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared state for screen control.
 * serviceConnected: Android has the AccessibilityService running (user enabled it in Settings).
 * armed: the user switched "Screen control" ON inside KALU. Nothing is performed unless this is true.
 */
object KaluAccess {
    val serviceConnected = MutableStateFlow(false)
    val armed = MutableStateFlow(false)

    @Volatile
    var service: KaluAccessibilityService? = null

    private const val CHANNEL = "kalu_control"
    private const val NOTIF_ID = 1001

    fun isEnabledInSettings(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val me = ctx.packageName + "/" + KaluAccessibilityService::class.java.name
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun arm(ctx: Context) {
        armed.value = true
        showNotification(ctx)
    }

    fun disarm(ctx: Context) {
        armed.value = false
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    @Suppress("DEPRECATION")
    private fun showNotification(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen control status", NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, StopReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("KALU screen control ON")
            .setContentText("KALU tumhare command par tap/scroll kar sakta hai. Rokne ke liye Stop dabao.")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(0, "Stop", stop).build())
            .build()
        try {
            nm.notify(NOTIF_ID, n)
        } catch (e: SecurityException) {
            // Notification permission not granted: the in-app banner still shows the status.
        }
    }
}

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        KaluAccess.disarm(context.applicationContext)
    }
}
