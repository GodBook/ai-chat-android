package com.example.aichat.background

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Entry points used by the activity and settings screen to control the background flow. */
object BackgroundScreenshotManager {
    const val ACTION_START = "com.example.aichat.action.START_SCREENSHOT_SERVICE"
    const val ACTION_CAPTURE_NOW = "com.example.aichat.action.CAPTURE_SCREENSHOT_NOW"
    const val ACTION_STOP = "com.example.aichat.action.STOP_SCREENSHOT_SERVICE"

    const val EXTRA_PROJECTION_RESULT_CODE = "com.example.aichat.extra.PROJECTION_RESULT_CODE"
    const val EXTRA_PROJECTION_DATA = "com.example.aichat.extra.PROJECTION_DATA"

    /** The activity must launch this intent and pass its result to [start]. */
    fun projectionPermissionIntent(context: Context): Intent =
        (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .createScreenCaptureIntent()

    /** Convenience overload for an Activity that is about to request projection consent. */
    fun projectionPermissionIntent(activity: Activity): Intent = projectionPermissionIntent(activity as Context)

    /** Starts the foreground service and gives it a fresh MediaProjection grant. */
    fun start(context: Context, resultCode: Int, projectionData: Intent) {
        val intent = serviceIntent(context, ACTION_START).apply {
            putExtra(EXTRA_PROJECTION_RESULT_CODE, resultCode)
            putExtra(EXTRA_PROJECTION_DATA, projectionData)
        }
        ContextCompat.startForegroundService(context.applicationContext, intent)
    }

    /** Starts the service without changing an existing in-memory projection grant. */
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context.applicationContext,
            serviceIntent(context, ACTION_START),
        )
    }

    /** Requests a capture from another in-app component, such as a notification action. */
    fun captureNow(context: Context) {
        ContextCompat.startForegroundService(
            context.applicationContext,
            serviceIntent(context, ACTION_CAPTURE_NOW),
        )
    }

    fun stop(context: Context) {
        context.applicationContext.stopService(serviceIntent(context, ACTION_STOP))
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun openOverlaySettings(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    /** Accessibility services are user-enabled in system settings; apps cannot enable them. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, VolumeDownAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').asSequence().mapNotNull { value ->
            ComponentName.unflattenFromString(value)
        }.any { it == expected }
    }

    internal fun serviceIntent(context: Context, action: String): Intent =
        Intent(context.applicationContext, BackgroundScreenshotService::class.java).setAction(action)
}
