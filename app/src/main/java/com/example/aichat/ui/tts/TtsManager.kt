package com.example.aichat.ui.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsPlaybackState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentMessageId: String? = null,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentenceText: String = "",
    val speechRate: Float = 1.0f,
    val isReady: Boolean = false,
)

val TtsPlaybackState.messageId: String?
    get() = currentMessageId

class TtsManager private constructor(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    private val _playbackState = MutableStateFlow(TtsPlaybackState())
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private var currentSentences: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var activeMessageId: String? = null

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsEngine = tts ?: return
            val result = ttsEngine.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsEngine.setLanguage(Locale.getDefault())
            }
            ttsEngine.setSpeechRate(_playbackState.value.speechRate)
            setupProgressListener()
            _playbackState.value = _playbackState.value.copy(isReady = true)
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = true,
                    isPaused = false,
                )
            }

            override fun onDone(utteranceId: String?) {
                val nextIdx = currentIndex + 1
                if (nextIdx < currentSentences.size) {
                    currentIndex = nextIdx
                    speakSentence(currentIndex)
                } else {
                    stop()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                stop()
            }
        })
    }

    fun speak(messageId: String, rawText: String) {
        if (!(_playbackState.value.isReady)) {
            tts = TextToSpeech(appContext, this)
        }

        stop()

        val purified = TtsTextPurifier.purify(rawText)
        val sentences = TtsTextPurifier.splitIntoSentences(purified)
        if (sentences.isEmpty()) return

        activeMessageId = messageId
        currentSentences = sentences
        currentIndex = 0

        _playbackState.value = _playbackState.value.copy(
            isPlaying = true,
            isPaused = false,
            currentMessageId = messageId,
            currentSentenceIndex = 0,
            totalSentences = sentences.size,
            currentSentenceText = sentences.first(),
        )

        speakSentence(0)
    }

    private fun speakSentence(index: Int) {
        val ttsEngine = tts ?: return
        if (index !in currentSentences.indices) return

        val text = currentSentences[index]
        _playbackState.value = _playbackState.value.copy(
            currentSentenceIndex = index,
            currentSentenceText = text,
        )

        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utt_$index")
    }

    fun pause() {
        if (_playbackState.value.isPlaying) {
            tts?.stop()
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                isPaused = true,
            )
        }
    }

    fun resume() {
        if (_playbackState.value.isPaused) {
            _playbackState.value = _playbackState.value.copy(
                isPlaying = true,
                isPaused = false,
            )
            speakSentence(currentIndex)
        }
    }

    fun stop() {
        tts?.stop()
        activeMessageId = null
        currentSentences = emptyList()
        currentIndex = 0
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isPaused = false,
            currentMessageId = null,
            currentSentenceIndex = 0,
            totalSentences = 0,
            currentSentenceText = "",
        )
    }

    fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
        _playbackState.value = _playbackState.value.copy(speechRate = rate)
    }

    fun seekNext() {
        if (currentIndex + 1 < currentSentences.size) {
            currentIndex++
            speakSentence(currentIndex)
        }
    }

    fun seekPrev() {
        if (currentIndex > 0) {
            currentIndex--
            speakSentence(currentIndex)
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        @Volatile
        private var instance: TtsManager? = null

        fun getInstance(context: Context): TtsManager =
            instance ?: synchronized(this) {
                instance ?: TtsManager(context).also { instance = it }
            }
    }
}
