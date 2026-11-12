package com.sagar.baby

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Mic se sunna (Google speech recognition, Hindi + Hinglish). */
class VoiceListener(private val context: Context, private val cb: Callback) {

    interface Callback {
        fun onListeningStarted()
        fun onPartial(text: String)
        fun onResults(results: List<String>)
        fun onRms(db: Float)
        fun onListenError(code: Int)
    }

    private var recognizer: SpeechRecognizer? = null
    var isListening = false
        private set

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private fun create() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { cb.onListeningStarted() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { cb.onRms(rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (!isListening) return
                    isListening = false
                    cb.onListenError(error)
                }
                override fun onResults(results: Bundle?) {
                    if (!isListening) return
                    isListening = false
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (list.isEmpty()) cb.onListenError(SpeechRecognizer.ERROR_NO_MATCH) else cb.onResults(list)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { if (it.isNotBlank()) cb.onPartial(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun start(language: String = "hi-IN") {
        if (recognizer == null) create()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        isListening = true
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            isListening = false
            cb.onListenError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun stop() {
        isListening = false
        try { recognizer?.cancel() } catch (_: Exception) {}
    }

    fun reset() {
        destroy()
        create()
    }

    fun destroy() {
        isListening = false
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
    }
}
