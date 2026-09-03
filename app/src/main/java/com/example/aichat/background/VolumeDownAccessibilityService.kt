package com.example.aichat.background

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.example.aichat.AiChatApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Receives global volume-down key events after the user enables this accessibility service. */
class VolumeDownAccessibilityService : AccessibilityService() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    @Volatile private var enabled = false
    private var configJob: Job? = null
    private var lastTriggerAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        } ?: AccessibilityServiceInfo().apply {
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        val app = application as AiChatApplication
        configJob = serviceScope.launch {
            app.container.configStore.config.collectLatest { config ->
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
                sendBroadcast(
                    android.content.Intent(this, ScreenshotTriggerReceiver::class.java)
                        .setAction(BackgroundScreenshotManager.ACTION_CAPTURE_NOW),
                )
            }
        }
        // Do not consume the key: the normal system volume behavior remains available.
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        configJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TRIGGER_DEBOUNCE_MS = 700L
    }
}
