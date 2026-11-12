package com.sagar.baby

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Baby ki awaaz (Text-to-Speech, Hindi). */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var failed = false
    private var queued: Pair<String, (() -> Unit)?>? = null

    @Volatile var isSpeaking = false
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            queued?.second?.let { main.post(it) }
            queued = null
            return
        }
        val r = tts.setLanguage(Locale.forLanguageTag("hi-IN"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.forLanguageTag("en-IN"))
        }
        tts.setSpeechRate(1.05f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { isSpeaking = true }
            override fun onDone(utteranceId: String?) = finish(utteranceId, true)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId, true)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, true)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, false)
        })
        ready = true
        queued?.let { speak(it.first, it.second) }
        queued = null
    }

    private fun finish(id: String?, invoke: Boolean) {
        isSpeaking = false
        val cb = id?.let { callbacks.remove(it) } ?: return
        if (invoke) main.post(cb)
    }

    /** Bolo, aur khatam hone par [onDone] chalao. */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (failed) { onDone?.let { main.post(it) }; return }
        if (!ready) { queued = text to onDone; return }
        val id = UUID.randomUUID().toString()
        if (onDone != null) callbacks[id] = onDone
        isSpeaking = true
        val res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (res != TextToSpeech.SUCCESS) finish(id, true)
    }

    fun stop() {
        callbacks.clear()
        if (ready) tts.stop()
        isSpeaking = false
    }
}
