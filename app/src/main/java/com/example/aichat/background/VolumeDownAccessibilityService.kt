package com.example.aichat.background

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.aichat.AiChatApplication
import com.example.aichat.data.model.ProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Receives global volume-down key events after the user enables this accessibility service. */
class VolumeDownAccessibilityService : AccessibilityService() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private lateinit var app: AiChatApplication
    private lateinit var overlayManager: ScreenshotOverlayManager
    private lateinit var questionProcessor: ScreenshotQuestionProcessor
    @Volatile private var enabled = false
    @Volatile private var latestConfig = ProviderConfig()
    private var configJob: Job? = null
    private var captureJob: Job? = null
    private var screenshotPending = false
    private var lastTriggerAt = 0L

    override fun onCreate() {
        super.onCreate()
        app = application as AiChatApplication
        overlayManager = ScreenshotOverlayManager(this)
        questionProcessor = ScreenshotQuestionProcessor(app)
        BackgroundScreenshotManager.attach(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        configJob?.cancel()
        configJob = serviceScope.launch {
            app.container.configStore.config.collectLatest { config ->
                latestConfig = config
                enabled = config.backgroundCaptureEnabled
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled || event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastTriggerAt >= TRIGGER_DEBOUNCE_MS) {
                lastTriggerAt = now
                captureFromTrigger()
            }
        }
        // Do not consume the key: the normal system volume behavior remains available.
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Handles both the volume key and the settings screen's manual test action. */
    internal fun captureFromTrigger(): Boolean {
        if (!enabled || screenshotPending || captureJob?.isActive == true) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestAccessibilityScreenshot()
            true
        } else {
            runCatching {
                BackgroundScreenshotManager.captureWithProjection(this)
            }.onFailure {
                showFeedback(it.message ?: "无法启动屏幕捕获")
            }.let { result ->
                if (result.isFailure) screenshotPending = false
                result.isSuccess
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestAccessibilityScreenshot() {
        screenshotPending = true
        overlayManager.dismiss()
        try {
            @Suppress("DEPRECATION")
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        screenshotPending = false
                        if (!serviceJob.isActive) {
                            screenshot.hardwareBuffer.close()
                            return
                        }
                        val bitmap = runCatching { screenshot.toSoftwareBitmap() }
                            .getOrElse {
                                showFeedback(it.message ?: "无法读取屏幕截图")
                                return
                            }
                        processScreenshot(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        screenshotPending = false
                        showFeedback(screenshotErrorMessage(errorCode))
                    }
                },
            )
        } catch (failure: Throwable) {
            screenshotPending = false
            showFeedback(failure.message ?: "系统截图失败")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap {
        val buffer = hardwareBuffer
        try {
            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                ?: error("系统返回了不支持的截图格式")
            return try {
                hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    ?: error("无法转换屏幕截图")
            } finally {
                hardwareBitmap.recycle()
            }
        } finally {
            buffer.close()
        }
    }

    private fun processScreenshot(bitmap: Bitmap) {
        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            val config = runCatching { app.container.configStore.read() }.getOrElse {
                bitmap.recycle()
                showFeedback("无法读取后台截图设置")
                captureJob = null
                return@launch
            }
            if (!config.backgroundCaptureEnabled) {
                bitmap.recycle()
                captureJob = null
                return@launch
            }
            try {
                val answer = questionProcessor.process(
                    bitmap = bitmap,
                    prompt = config.screenshotPrompt,
                    shortAnswerModeEnabled = config.shortAnswerModeEnabled,
                )
                if (config.shortAnswerModeEnabled) {
                    // Compact mode intentionally has no text bubble. Only a recognized answer
                    // gets a one-second indicator at the top edge of the screen.
                    extractShortAnswerIndicator(answer)?.let(overlayManager::showShortAnswer)
                } else {
                    showFeedback(answer, config)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                showFeedback(failure.message ?: "截屏问答失败", config)
            } finally {
                captureJob = null
            }
        }
        captureJob = job
        job.start()
    }

    private fun showFeedback(message: String, config: ProviderConfig? = null) {
        val appearance = config ?: latestConfig
        val shown = overlayManager.show(
            answer = message,
            backgroundColor = appearance.overlayBackgroundColor,
            glassEnabled = appearance.overlayGlassEnabled,
        )
        if (!shown) {
            Toast.makeText(this, message.take(180), Toast.LENGTH_LONG).show()
        }
    }

    private fun screenshotErrorMessage(errorCode: Int): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (errorCode) {
                ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "截图操作过于频繁，请稍后再试"
                ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "无法识别当前屏幕"
                ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "音量监听服务没有截图权限，请重新开启"
                ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "当前界面禁止系统截图"
                else -> "系统截图失败（错误码 $errorCode）"
            }
        } else {
            "系统截图失败"
        }

    override fun onDestroy() {
        enabled = false
        screenshotPending = false
        captureJob?.cancel()
        configJob?.cancel()
        overlayManager.dismiss()
        BackgroundScreenshotManager.detach(this)
        serviceJob.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TRIGGER_DEBOUNCE_MS = 700L
    }
}
