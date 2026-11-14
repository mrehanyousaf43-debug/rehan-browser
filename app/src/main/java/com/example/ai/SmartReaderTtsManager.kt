package com.example.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class TtsUiState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val speechRate: Float = 1.0f,
    val languageCode: String = "en"
)

class SmartReaderTtsManager(context: Context) {

    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state.asStateFlow()

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _state.update { it.copy(isSpeaking = true) }
                    }

                    override fun onDone(utteranceId: String?) {
                        _state.update { it.copy(isSpeaking = false) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.update { it.copy(isSpeaking = false) }
                    }
                })
                _state.update { it.copy(isReady = true) }
            }
        }
    }

    fun speak(text: String, languageCode: String = _state.value.languageCode) {
        val engine = tts ?: return
        val locale = when (languageCode) {
            "ur" -> Locale.Builder().setLanguage("ur").setRegion("PK").build()
            "hi" -> Locale.Builder().setLanguage("hi").setRegion("IN").build()
            else -> Locale.US
        }
        val availability = engine.isLanguageAvailable(locale)
        if (availability >= TextToSpeech.LANG_AVAILABLE) {
            engine.language = locale
        } else {
            engine.language = Locale.US
        }
        engine.setSpeechRate(_state.value.speechRate)
        val clipped = text.take(3900)
        engine.speak(clipped, TextToSpeech.QUEUE_FLUSH, null, "rehan_reader_${System.currentTimeMillis()}")
        _state.update { it.copy(isSpeaking = true, languageCode = languageCode) }
    }

    fun setSpeechRate(rate: Float) {
        _state.update { it.copy(speechRate = rate) }
        tts?.setSpeechRate(rate)
    }

    fun setLanguage(code: String) {
        _state.update { it.copy(languageCode = code) }
    }

    fun stop() {
        tts?.stop()
        _state.update { it.copy(isSpeaking = false) }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
