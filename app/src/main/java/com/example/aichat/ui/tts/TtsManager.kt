package com.example.aichat.ui.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsPlaybackState(
    val isPlaying: Boolean = false,
    val voiceOnly: Boolean = false,
    val isPaused: Boolean = false,
    val currentMessageId: String? = null,
    val currentSentenceIndex: Int = 0,
    val totalSentences: Int = 0,
    val currentSentenceText: String = "",
    val speechRate: Float = 1.0f,
    val isReady: Boolean = false,
    val errorMessage: String? = null,
)

val TtsPlaybackState.messageId: String?
    get() = currentMessageId

class TtsManager private constructor(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var engineGeneration = 0L
    private var utteranceSequence = 0L
    private var activeUtteranceId: String? = null
    private val initTimeout = Runnable {
        if (!_playbackState.value.isReady) {
            fail("语音引擎启动超时，请检查系统的文字转语音设置", resetEngine = true)
        }
    }

    private val _playbackState = MutableStateFlow(TtsPlaybackState())
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private var currentSentences: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var activeMessageId: String? = null
    private var pendingSpeak: Boolean = false

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        val generation = ++engineGeneration
        mainHandler.postDelayed(initTimeout, 15_000L)
        tts = TextToSpeech(appContext) { status ->
            // Always defer: an engine may report failure before its constructor returns.
            mainHandler.post {
                if (generation == engineGeneration) onInit(status)
            }
        }
    }

    override fun onInit(status: Int) {
        mainHandler.removeCallbacks(initTimeout)
        if (status != TextToSpeech.SUCCESS) {
            fail("无法启动语音引擎，请在系统文字转语音设置中安装或启用中文语音引擎", resetEngine = true)
            return
        }
        val ttsEngine = tts ?: return
        runCatching {
            val language = ttsEngine.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (language < TextToSpeech.LANG_AVAILABLE) {
                fail("语音引擎缺少中文语音，请在系统文字转语音设置中下载中文语音包", resetEngine = true)
                return
            }
            ttsEngine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            ttsEngine.setSpeechRate(_playbackState.value.speechRate)
            setupProgressListener()
            _playbackState.value = _playbackState.value.copy(isReady = true, errorMessage = null)
            if (pendingSpeak && currentSentences.isNotEmpty()) {
                pendingSpeak = false
                speakSentence(currentIndex)
            }
        }.onFailure {
            fail("语音引擎配置失败，请检查系统文字转语音设置", resetEngine = true)
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId != activeUtteranceId || utteranceId == null) return@post
                    _playbackState.value = _playbackState.value.copy(isPlaying = true, isPaused = false)
                }
            }

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId != activeUtteranceId || utteranceId == null) return@post
                    val nextIdx = currentIndex + 1
                    if (nextIdx < currentSentences.size) {
                        currentIndex = nextIdx
                        speakSentence(currentIndex)
                    } else {
                        stop()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onError(utteranceId, TextToSpeech.ERROR)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    if (utteranceId != activeUtteranceId || utteranceId == null) return@post
                    fail("语音播放失败（$errorCode），请检查中文语音包和系统语音引擎", resetEngine = true)
                }
            }
        })
    }

    fun speak(messageId: String, rawText: String, voiceOnly: Boolean = false) {
        stop()
        _playbackState.value = _playbackState.value.copy(voiceOnly = voiceOnly)

        val purified = TtsTextPurifier.purify(rawText)
        val sentences = TtsTextPurifier.splitIntoSentences(purified)
        if (sentences.isEmpty()) {
            fail("回答中没有可朗读的文字")
            return
        }

        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            fail("媒体音量为零，请先在系统音量面板调高媒体音量")
            return
        }
        if (tts == null) initializeEngine()

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
            errorMessage = null,
        )

        if (_playbackState.value.isReady) {
            speakSentence(0)
        } else {
            pendingSpeak = true
        }
    }

    private fun speakSentence(index: Int) {
        val ttsEngine = tts ?: return
        if (index !in currentSentences.indices) return

        val text = currentSentences[index]
        _playbackState.value = _playbackState.value.copy(
            currentSentenceIndex = index,
            currentSentenceText = text,
        )

        val utteranceId = "tts_utt_${++utteranceSequence}"
        activeUtteranceId = utteranceId
        val result = runCatching {
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result == TextToSpeech.ERROR) {
            fail("语音播放启动失败，请检查系统文字转语音设置后重试", resetEngine = true)
        }
    }

    fun hideScreenshotPlayback() {
        if (_playbackState.value.currentMessageId?.startsWith("screenshot_") == true) {
            _playbackState.value = _playbackState.value.copy(voiceOnly = true)
        }
    }

    fun pause() {
        if (_playbackState.value.isPlaying) {
            pendingSpeak = false
            activeUtteranceId = null
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
            if (_playbackState.value.isReady) speakSentence(currentIndex) else pendingSpeak = true
        }
    }

    fun stop() {
        pendingSpeak = false
        activeUtteranceId = null
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

    private fun fail(message: String, resetEngine: Boolean = false) {
        val allowVisualFeedback = !_playbackState.value.voiceOnly
        val notify = activeMessageId != null || _playbackState.value.isPlaying
        stop()
        if (resetEngine) {
            ++engineGeneration
            mainHandler.removeCallbacks(initTimeout)
            tts?.shutdown()
            tts = null
        }
        _playbackState.value = _playbackState.value.copy(
            isReady = !resetEngine && _playbackState.value.isReady,
            errorMessage = message,
        )
        // Initialization runs at app startup; only show errors for an actual playback request.
        if (allowVisualFeedback && (notify || !resetEngine)) Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
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
        ++engineGeneration
        mainHandler.removeCallbacks(initTimeout)
        tts?.shutdown()
        tts = null
        _playbackState.value = _playbackState.value.copy(isReady = false)
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
