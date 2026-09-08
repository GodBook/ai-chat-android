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
import com.example.aichat.data.model.ScreenshotTrigger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/**
 * Receives global volume key events after the user enables this accessibility
 * service and starts the background screenshot flow for the configured shortcut.
 */
class VolumeDownAccessibilityService : AccessibilityService() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private lateinit var app: AiChatApplication
    private lateinit var overlayManager: ScreenshotOverlayManager
    private lateinit var questionProcessor: ScreenshotQuestionProcessor
    @Volatile private var enabled = false
    @Volatile private var latestConfig = ProviderConfig()
    @Volatile private var trigger: ScreenshotTrigger = ScreenshotTrigger.VOLUME_DOWN
    private var configJob: Job? = null
    /** Down-press timestamp per volume key slot: 0 = VOLUME_DOWN, 1 = VOLUME_UP. */
    private val volumeKeyPressedAt = LongArray(2)
    /** Blocks a second pair trigger while the user still holds both keys. */
    private var pairTriggerConsumed = false
    @Volatile private var captureJob: Job? = null
    @Volatile private var screenshotPending = false
    private var lastTriggerAt = 0L
    private val captureLock = Any()
    private val screenshotExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ai-chat-screenshot").apply { isDaemon = true }
    }

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
                trigger = config.screenshotTrigger
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!enabled) {
            resetVolumeKeyState()
            return false
        }
        val slot = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> SLOT_VOLUME_DOWN
            KeyEvent.KEYCODE_VOLUME_UP -> SLOT_VOLUME_UP
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_UP) {
            onVolumeKeyReleased(slot)
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val now = android.os.SystemClock.elapsedRealtime()
        // Repeat events refresh the hold timestamp so a long press still counts as held.
        volumeKeyPressedAt[slot] = now
        if (event.repeatCount != 0) return false
        onVolumeKeyPressed(slot, now)
        // Do not consume the key: the normal system volume behavior remains available.
        return false
    }

    private fun onVolumeKeyPressed(slot: Int, now: Long) {
        val matched = when (trigger) {
            ScreenshotTrigger.VOLUME_DOWN -> slot == SLOT_VOLUME_DOWN
            ScreenshotTrigger.VOLUME_UP_DOWN -> !pairTriggerConsumed && isHeld(1 - slot, now)
        }
        if (!matched) return
        if (trigger == ScreenshotTrigger.VOLUME_UP_DOWN) pairTriggerConsumed = true
        if (now - lastTriggerAt < TRIGGER_DEBOUNCE_MS) return
        lastTriggerAt = now
        captureFromTrigger()
    }

    private fun onVolumeKeyReleased(slot: Int) {
        volumeKeyPressedAt[slot] = 0L
        pairTriggerConsumed = false
    }

    private fun resetVolumeKeyState() {
        volumeKeyPressedAt.fill(0L)
        pairTriggerConsumed = false
    }

    /** Treats a key as held while its last down or repeat event is recent enough. */
    private fun isHeld(slot: Int, now: Long): Boolean {
        val pressedAt = volumeKeyPressedAt[slot]
        return pressedAt != 0L && now - pressedAt <= PAIR_WINDOW_MS
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Handles both the volume key and the settings screen's manual test action. */
    internal fun captureFromTrigger(): Boolean {
        if (!enabled) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            synchronized(captureLock) {
                if (screenshotPending || captureJob != null) return false
                // Keep the reservation until processScreenshot has installed its Job. This
                // closes the callback-to-processing window where a second key press could start
                // another system screenshot while the first bitmap was still being scheduled.
                screenshotPending = true
            }
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
        overlayManager.dismiss()
        try {
            @Suppress("DEPRECATION")
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        if (!serviceJob.isActive) {
                            screenshot.hardwareBuffer.close()
                            releaseCaptureReservation()
                            return
                        }
                        val bitmap = runCatching { screenshot.toSoftwareBitmap() }
                            .getOrElse {
                                releaseCaptureReservation()
                                serviceScope.launch {
                                    showFeedback(it.message ?: "无法读取屏幕截图")
                                }
                                return
                            }
                        processScreenshot(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        releaseCaptureReservation()
                        serviceScope.launch {
                            showFeedback(screenshotErrorMessage(errorCode))
                        }
                    }
                },
            )
        } catch (failure: Throwable) {
            releaseCaptureReservation()
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
            val config = try {
                app.container.configStore.read()
            } catch (cancelled: CancellationException) {
                bitmap.recycle()
                coroutineContext[Job]?.let(::clearCaptureJob)
                throw cancelled
            } catch (_: Throwable) {
                bitmap.recycle()
                if (serviceJob.isActive) showFeedback("无法读取后台截图设置")
                coroutineContext[Job]?.let(::clearCaptureJob)
                return@launch
            }
            if (!config.backgroundCaptureEnabled) {
                bitmap.recycle()
                coroutineContext[Job]?.let(::clearCaptureJob)
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
                coroutineContext[Job]?.let(::clearCaptureJob)
            }
        }
        synchronized(captureLock) {
            captureJob = job
            screenshotPending = false
        }
        if (!job.start()) {
            // A service destroyed between launch and start still owns this bitmap.
            bitmap.recycle()
            clearCaptureJob(job)
        }
    }

    private fun releaseCaptureReservation() {
        synchronized(captureLock) {
            screenshotPending = false
        }
    }

    private fun clearCaptureJob(job: Job) {
        synchronized(captureLock) {
            if (captureJob === job) captureJob = null
        }
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
        resetVolumeKeyState()
        val runningJob = synchronized(captureLock) {
            screenshotPending = false
            captureJob.also { captureJob = null }
        }
        runningJob?.cancel()
        configJob?.cancel()
        screenshotExecutor.shutdownNow()
        overlayManager.dismiss()
        BackgroundScreenshotManager.detach(this)
        serviceJob.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TRIGGER_DEBOUNCE_MS = 700L
        /** How long a held volume key still counts as part of an up + down combination. */
        const val PAIR_WINDOW_MS = 2_000L
        const val SLOT_VOLUME_DOWN = 0
        const val SLOT_VOLUME_UP = 1
    }
}
