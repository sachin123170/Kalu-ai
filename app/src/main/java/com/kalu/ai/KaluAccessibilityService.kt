package com.kalu.ai

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional screen control. The user must enable this manually in Android Settings, and also
 * switch "Screen control" ON inside the app. It never types text, never reads password fields,
 * and never sends screen content anywhere.
 */
class KaluAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        KaluAccess.service = this
        KaluAccess.serviceConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        KaluAccess.service = null
        KaluAccess.serviceConnected.value = false
        KaluAccess.disarm(applicationContext)
        return super.onUnbind(intent)
    }

    fun execute(cmd: Command): String {
        val needsOtherApp = cmd !is Command.Home && cmd !is Command.Recents
        if (needsOtherApp && rootInActiveWindow?.packageName?.toString() == packageName) {
            return "KALU hi screen par khula tha, isliye kuch nahi kiya. Dobara bolo aur 4 second mein dusre app par chale jao."
        }
        return when (cmd) {
            is Command.Back -> if (performGlobalAction(GLOBAL_ACTION_BACK)) "Back kar diya. ✅" else "Back nahi ho paya."
            is Command.Home -> if (performGlobalAction(GLOBAL_ACTION_HOME)) "Home par aa gaya. ✅" else "Home nahi ho paya."
            is Command.Recents -> if (performGlobalAction(GLOBAL_ACTION_RECENTS)) "Recent apps khol diye. ✅" else "Recents nahi khule."
            is Command.ScrollDown -> if (scroll(true)) "Neeche scroll kar diya. ✅" else "Scroll nahi ho paya."
            is Command.ScrollUp -> if (scroll(false)) "Upar scroll kar diya. ✅" else "Scroll nahi ho paya."
            is Command.ClickText ->
                if (click(cmd.text)) "'${cmd.text}' dabaa diya. ✅" else "Screen par '${cmd.text}' nahi mila ya dab nahi paya."
            is Command.ReadScreen -> readScreen()
            else -> "Ye screen control ka command nahi hai."
        }
    }

    private fun scroll(forward: Boolean): Boolean {
        val node = rootInActiveWindow?.let { findScrollable(it) }
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        if (node != null && node.performAction(action)) return true
        return swipe(forward)
    }

    private fun findScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            findScrollable(c)?.let { return it }
        }
        return null
    }

    private fun swipe(forward: Boolean): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val p = Path()
        if (forward) {
            p.moveTo(w / 2, h * 0.7f); p.lineTo(w / 2, h * 0.3f)
        } else {
            p.moveTo(w / 2, h * 0.3f); p.lineTo(w / 2, h * 0.7f)
        }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 300))
            .build()
        return dispatchGesture(g, null, null)
    }

    private fun click(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = root.findAccessibilityNodeInfosByText(text).firstOrNull { it.isVisibleToUser && !it.isPassword }
            ?: return false
        var n: AccessibilityNodeInfo? = target
        while (n != null) {
            if (n.isClickable && n.isEnabled) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        val r = Rect()
        target.getBoundsInScreen(r)
        return tap(r.centerX().toFloat(), r.centerY().toFloat())
    }

    private fun tap(x: Float, y: Float): Boolean {
        val p = Path()
        p.moveTo(x, y)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60))
            .build()
        return dispatchGesture(g, null, null)
    }

    private fun readScreen(): String {
        val root = rootInActiveWindow ?: return "Screen padh nahi paya."
        val out = mutableListOf<String>()
        collect(root, out)
        return if (out.isEmpty()) "Screen par padhne layak text nahi mila."
        else "Screen par ye dikh raha hai:\n" + out.joinToString("\n") { "• $it" }
    }

    private fun collect(n: AccessibilityNodeInfo, out: MutableList<String>) {
        if (out.size >= 40 || !n.isVisibleToUser) return
        if (!n.isPassword) {
            val t = n.text?.toString()?.trim().orEmpty()
                .ifEmpty { n.contentDescription?.toString()?.trim().orEmpty() }
            if (t.isNotEmpty() && !out.contains(t.take(120))) out.add(t.take(120))
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { collect(it, out) }
    }
}
