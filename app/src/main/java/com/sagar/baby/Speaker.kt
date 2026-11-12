package com.sagar.baby

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Baby ki awaaz.
 * Jis jawab ki aapki recording (voice pack) hai, woh recording bajti hai; baaki phone ki TTS awaaz me.
 */
class Speaker(private val context: Context) : TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var failed = false
    private var queued: Pair<String, (() -> Unit)?>? = null
    private var player: MediaPlayer? = null
    private var playerDone: (() -> Unit)? = null
    private val prefs = context.getSharedPreferences("baby", Context.MODE_PRIVATE)

    @Volatile var isSpeaking = false
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true
            queued?.second?.let { main.post(it) }
            queued = null
            return
        }
        // Hinglish (Roman) ke liye Indian English awaaz sabse saaf bolti hai
        val r = tts.setLanguage(Locale.forLanguageTag("en-IN"))
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.forLanguageTag("hi-IN"))
        }
        applyVoiceSettings()
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

    fun applyVoiceSettings() {
        if (!ready && failed) return
        try {
            val name = prefs.getString("ttsVoice", null)
            if (name != null) tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it }
            tts.setPitch(prefs.getFloat("ttsPitch", 1.0f))
            tts.setSpeechRate(prefs.getFloat("ttsRate", 1.0f))
        } catch (_: Exception) { }
    }

    /** Hindi / Indian English awaazein (settings me chunne ke liye). */
    fun indianVoices(): List<Voice> = try {
        tts.voices.orEmpty()
            .filter { (it.locale.language == "en" && it.locale.country == "IN") || it.locale.language == "hi" }
            .filter { !it.isNetworkConnectionRequired || it.name.contains("local") }
            .sortedBy { it.name }
    } catch (_: Exception) {
        emptyList()
    }

    private fun finish(id: String?, invoke: Boolean) {
        isSpeaking = false
        val cb = id?.let { callbacks.remove(it) } ?: return
        if (invoke) main.post(cb)
    }

    /** [clip] ki recording ho to woh bajao, warna [text] TTS se bolo. */
    fun say(text: String, clip: String?, onDone: (() -> Unit)? = null) {
        val file = clip?.let { VoicePack.file(context, it) }
        val asset = if (file == null) clip?.let { VoicePack.asset(context, it) } else null
        if (file != null || asset != null) {
            stop()
            if (playClip(file, asset, onDone)) return
        }
        speak(text, onDone)
    }

    private fun playClip(file: File?, asset: String?, onDone: (() -> Unit)?): Boolean = try {
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        if (file != null) {
            mp.setDataSource(file.absolutePath)
        } else {
            context.assets.openFd(asset!!).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        }
        playerDone = onDone
        mp.setOnCompletionListener { endClip(true) }
        mp.setOnErrorListener { _, _, _ -> endClip(true); true }
        mp.prepare()
        isSpeaking = true
        player = mp
        mp.start()
        true
    } catch (e: Exception) {
        isSpeaking = false
        false
    }

    private fun endClip(invoke: Boolean) {
        val cb = playerDone
        playerDone = null
        try { player?.release() } catch (_: Exception) { }
        player = null
        isSpeaking = false
        if (invoke && cb != null) main.post(cb)
    }

    /** Sirf TTS se bolo. */
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
        if (player != null) endClip(false)
        isSpeaking = false
    }
}
