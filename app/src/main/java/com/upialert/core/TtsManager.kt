package com.upialert.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingQueue = ArrayDeque<String>()

    private var onSpeechLimitListener: (() -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                // Set Audio Attributes for Alarm Stream (Bypasses Silent Mode)
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)

                // Default to English, but standard call will set it
                tts?.language = Locale("en", "IN")
                
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}

                    override fun onDone(utteranceId: String?) {
                        onSpeechLimitListener?.invoke()
                    }

                    override fun onError(utteranceId: String?) {
                        onSpeechLimitListener?.invoke()
                    }
                })
                
                processQueue()
            } else {
                Log.e("TtsManager", "Initialization failed")
            }
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        this.onSpeechLimitListener = onComplete
        if (isInitialized) {
            val params = android.os.Bundle()
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_ALARM)
            tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "UPI_ALERT_ID")
        } else {
            pendingQueue.add(text)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }

    fun setLanguage(locale: Locale) {
        val available = tts?.isLanguageAvailable(locale)
        if (available == TextToSpeech.LANG_MISSING_DATA || available == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.e("TtsManager", "Language not supported or missing data: ${locale.displayName}")
        }
        tts?.language = locale
    }

    fun setRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        tts?.setPitch(pitch)
    }

    private fun processQueue() {
        while (pendingQueue.isNotEmpty()) {
            val text = pendingQueue.removeFirst()
            speak(text, onSpeechLimitListener)
        }
    }
}
