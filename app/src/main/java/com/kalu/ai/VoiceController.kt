package com.kalu.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

enum class VoiceState { IDLE, LISTENING, SPEAKING }

/**
 * Speech-to-text + text-to-speech. Listening only ever happens after the user taps the mic or
 * turns on Continuous mode, the UI always shows the state, and everything stops when the app is hidden.
 */
class VoiceController(
    private val ctx: Context,
    private val onText: (String) -> Unit,
    private val onState: (VoiceState) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onContinuousChanged: (Boolean) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lang = "hi-IN"
    private var listening = false
    private var errorStreak = 0

    var continuous = false
        private set

    init {
        tts = TextToSpeech(ctx) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                val r = t.setLanguage(Locale("hi", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    t.setLanguage(Locale.getDefault())
                }
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        main.post { onState(VoiceState.SPEAKING) }
                    }

                    override fun onDone(utteranceId: String?) {
                        main.post { afterSpeak() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        main.post { afterSpeak() }
                    }
                })
                ttsReady = true
            }
        }
    }

    fun listenOnce(language: String) {
        lang = language
        startRecognizer()
    }

    fun startContinuous(language: String) {
        lang = language
        errorStreak = 0
        setContinuous(true)
        startRecognizer()
    }

    /** In continuous mode, start listening again shortly. No-op otherwise. */
    fun resume() {
        if (!continuous) return
        main.postDelayed({ if (continuous && !listening) startRecognizer() }, 400)
    }

    fun stop() {
        setContinuous(false)
        listening = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        onState(VoiceState.IDLE)
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
    }

    /** Speech speed and pitch for the next replies (mood-based). */
    fun setTone(rate: Float, pitch: Float) {
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
    }

    fun speak(raw: String) {
        val clean = sanitize(raw)
        val t = tts
        if (!ttsReady || t == null || clean.isBlank()) {
            afterSpeak()
            return
        }
        if (listening) {
            recognizer?.destroy()
            recognizer = null
            listening = false
        }
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "kalu")
    }

    private fun afterSpeak() {
        onState(VoiceState.IDLE)
        resume()
    }

    private fun sanitize(s: String): String =
        s.replace(Regex("```[\\s\\S]*?```"), " code screen par likha hai. ")
            .replace(Regex("[*#`_>•]"), "")
            .take(1500)

    private fun setContinuous(v: Boolean) {
        continuous = v
        onContinuousChanged(v)
    }

    private fun startRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onNotice("Is phone par speech recognition service nahi mili. Google app / Speech Services install karo.")
            setContinuous(false)
            return
        }
        tts?.stop()
        recognizer?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                listening = false
                errorStreak = 0
                onState(VoiceState.IDLE)
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) resume() else onText(text)
            }

            override fun onError(error: Int) {
                listening = false
                onState(VoiceState.IDLE)
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (continuous) resume() else onNotice("Kuch sunai nahi diya, dobara bolo.")
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {}
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> resume()
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        onNotice("Mic permission chahiye. Permissions guide dekho.")
                        setContinuous(false)
                    }
                    else -> {
                        errorStreak++
                        onNotice(
                            if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
                                "Voice recognition ke liye internet chahiye."
                            else "Voice error (code $error)."
                        )
                        if (errorStreak >= 4) setContinuous(false) else resume()
                    }
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        listening = true
        onState(VoiceState.LISTENING)
        r.startListening(i)
    }
}
