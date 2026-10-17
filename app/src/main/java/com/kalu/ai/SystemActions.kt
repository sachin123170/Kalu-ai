package com.kalu.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import java.util.Locale

data class AppInfo(val label: String, val pkg: String)

object AppLauncher {
    private val aliases = mapOf(
        "insta" to "instagram", "fb" to "facebook", "yt" to "youtube", "wa" to "whatsapp",
        "whats app" to "whatsapp", "whatsap" to "whatsapp", "setting" to "settings",
        "ghadi" to "clock", "kaimra" to "camera", "gallery" to "photos", "map" to "maps",
        "calculator" to "calculator", "insta gram" to "instagram", "tele gram" to "telegram"
    )

    @Suppress("DEPRECATION")
    fun installed(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
    }

    fun find(ctx: Context, query: String): List<AppInfo> {
        val q0 = query.lowercase(Locale.ROOT).trim()
        val q = aliases[q0] ?: q0
        if (q.isEmpty()) return emptyList()
        return installed(ctx)
            .map { app ->
                val l = app.label.lowercase(Locale.ROOT)
                val score = when {
                    l == q -> 3
                    l.startsWith(q) -> 2
                    l.contains(q) -> 1
                    else -> 0
                }
                app to score
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }.thenBy { it.first.label.length })
            .map { it.first }
    }

    fun launch(ctx: Context, app: AppInfo): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

object YouTubeHelper {
    /** Opens YouTube's own search results. The user taps a result; no ad/paywall bypass is attempted. */
    fun search(ctx: Context, query: String): Boolean {
        val app = Intent(Intent.ACTION_SEARCH)
            .setPackage("com.google.android.youtube")
            .putExtra("query", query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(app)
            return true
        } catch (e: Exception) {
        }
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(web)
            true
        } catch (e: Exception) {
            false
        }
    }
}

object AlarmHelper {
    /** Opens the Clock app's alarm screen pre-filled; the user confirms it there. */
    fun setAlarm(ctx: Context, hour: Int, minute: Int, label: String): Boolean {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setTimer(ctx: Context, seconds: Int, label: String): Boolean {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}
