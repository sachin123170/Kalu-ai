package com.kalu.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Lets KALU be chosen as the phone's "digital assistant app" (Settings > Apps > Default apps).
 * The assist gesture (power-button long press / home swipe) then opens KALU and starts listening.
 */
class KaluVoiceService : VoiceInteractionService()

class KaluSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = KaluSession(this)
}

class KaluSession(ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val i = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("auto_listen", true)
        startAssistantActivity(i)
        hide()
    }
}
