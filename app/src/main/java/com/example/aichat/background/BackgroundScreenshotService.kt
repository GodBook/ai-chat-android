package com.example.aichat.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.aichat.AiChatApplication
import com.example.aichat.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Foreground worker that captures a frame and sends it through the normal chat repository. */
class BackgroundScreenshotService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private lateinit var app: AiChatApplication
    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var overlayManager: ScreenshotOverlayManager
    private lateinit var questionProcessor: ScreenshotQuestionProcessor
    private var projectionJob: Job? = null
    private var captureJob: Job? = null

    internal val hasProjection: Boolean
        get() = ::captureManager.isInitialized && captureManager.hasProjection

    override fun onCreate() {
        super.onCreate()
        app = application as AiChatApplication
        captureManager = ScreenCaptureManager(this)
        overlayManager = ScreenshotOverlayManager(this)
        questionProcessor = ScreenshotQuestionProcessor(app)
        createNotificationChannel()
        BackgroundScreenshotManager.attach(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BackgroundScreenshotManager.ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            BackgroundScreenshotManager.ACTION_START,
            BackgroundScreenshotManager.ACTION_CAPTURE_NOW,
            null -> Unit
            else -> return START_STICKY
        }
        val projectionData = intent?.parcelableIntentExtra(
            BackgroundScreenshotManager.EXTRA_PROJECTION_DATA,
        )
        val hasNewProjectionGrant = intent?.getIntExtra(
            BackgroundScreenshotManager.EXTRA_PROJECTION_RESULT_CODE,
            Int.MIN_VALUE,
        ) != Int.MIN_VALUE && projectionData != null
        try {
            // Every start path uses startForegroundService(), so promote it immediately before
            // doing any projection or configuration work.
            startForegroundCompat()
        } catch (_: SecurityException) {
            notifyStatus("系统拒绝启动屏幕捕获服务，请重新授权屏幕捕获")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == BackgroundScreenshotManager.ACTION_CAPTURE_NOW &&
            !captureManager.hasProjection && !hasNewProjectionGrant &&
            projectionJob?.isActive != true
        ) {
            notifyStatus("后台进程已重启，请回到设置重新授权屏幕捕获")
            // A MediaProjection grant is tied to the current process. Keep the switch enabled so
            // the user can renew the grant from Settings instead of silently turning it off.
            return START_STICKY
        }
        applyProjectionGrant(intent)
        if (intent?.action == BackgroundScreenshotManager.ACTION_CAPTURE_NOW) requestCapture()
        // Keep the worker alive while the user-controlled switch is enabled. The projection grant
        // may still need to be renewed after a process death.
        return START_STICKY
    }

    override fun onDestroy() {
        captureJob?.cancel()
        projectionJob?.cancel()
        overlayManager.dismiss()
        captureManager.close()
        BackgroundScreenshotManager.detach(this)
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Called by the accessibility service when this worker is already alive. */
    internal fun captureFromTrigger() {
        requestCapture()
    }

    private fun applyProjectionGrant(intent: Intent?) {
        val resultCode = intent?.getIntExtra(
            BackgroundScreenshotManager.EXTRA_PROJECTION_RESULT_CODE,
            Int.MIN_VALUE,
        ) ?: Int.MIN_VALUE
        val projectionData = intent?.parcelableIntentExtra(BackgroundScreenshotManager.EXTRA_PROJECTION_DATA)
        if (resultCode == Int.MIN_VALUE || projectionData == null) return
        projectionJob?.cancel()
        projectionJob = serviceScope.launch {
            runCatching { captureManager.setProjection(resultCode, projectionData) }
                .onFailure { notifyStatus("屏幕捕获授权无效，请重新授权") }
        }
    }

    private fun requestCapture() {
        if (captureJob?.isActive == true) return
        captureJob = serviceScope.launch {
            projectionJob?.join()
            val config = runCatching { app.container.configStore.read() }.getOrElse {
                notifyStatus("无法读取后台截图设置")
                return@launch
            }
            if (!config.backgroundCaptureEnabled) return@launch
            try {
                val bitmap = captureManager.capture()
                val answer = questionProcessor.process(bitmap, config.screenshotPrompt)
                if (!overlayManager.show(
                        answer = answer,
                        backgroundColor = config.overlayBackgroundColor,
                        glassEnabled = config.overlayGlassEnabled,
                    )
                ) {
                    notifyStatus(answer)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                notifyStatus(failure.message ?: "截屏问答失败")
            } finally {
                captureJob = null
            }
        }
    }

    private fun startForegroundCompat() {
        val notification = buildNotification("后台截图问答已开启")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun notifyStatus(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(ANSWER_NOTIFICATION_ID, buildNotification(message.take(180))) }
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AI 截屏问答")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(text == "后台截图问答已开启")
            .setAutoCancel(text != "后台截图问答已开启")
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "后台截图问答",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "后台截图服务状态和 AI 回答" },
        )
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "background_screenshot"
        const val FOREGROUND_NOTIFICATION_ID = 1201
        const val ANSWER_NOTIFICATION_ID = 1202
    }
}

@Suppress("DEPRECATION")
private fun Intent.parcelableIntentExtra(key: String): Intent? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        getParcelableExtra(key)
    }
