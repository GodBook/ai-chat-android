package com.example.aichat.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** In-app bridge from the accessibility key listener to the foreground worker. */
class ScreenshotTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != BackgroundScreenshotManager.ACTION_CAPTURE_NOW) return
        BackgroundScreenshotManager.captureNow(context)
    }
}
