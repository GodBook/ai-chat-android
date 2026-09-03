package com.example.aichat.background

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Keeps a MediaProjection grant alive and captures one frame on demand. */
class ScreenCaptureManager(private val context: Context) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var activeReader: ImageReader? = null
    private var activeDisplay: VirtualDisplay? = null
    private var pendingCapture: CancellableContinuation<Bitmap>? = null
    private var captureWidth = 0
    private var captureHeight = 0

    val hasProjection: Boolean
        get() = projection != null

    suspend fun setProjection(resultCode: Int, permissionData: Intent) {
        withContext(Dispatchers.Main.immediate) {
            releaseProjection()
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                as? android.media.projection.MediaProjectionManager
                ?: error("系统不支持屏幕捕获")
            val granted = manager.getMediaProjection(resultCode, permissionData)
                ?: error("屏幕捕获授权已失效，请重新授权")
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    // A stale callback from a previous grant must not clear a newer grant.
                    if (projection === granted) {
                        releaseCaptureResources()
                        projection = null
                    }
                }
            }
            granted.registerCallback(callback, mainHandler)
            projectionCallback = callback
            projection = granted
        }
    }

    /** Captures the next available frame from the active projection session. */
    suspend fun capture(): Bitmap = withContext(Dispatchers.Main.immediate) {
        val activeProjection = projection ?: error("尚未获得屏幕捕获权限，请先在设置中授权")
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay
            .getRealMetrics(metrics)
        val fallback = context.resources.displayMetrics
        val width = metrics.widthPixels.takeIf { it > 0 } ?: fallback.widthPixels
        val height = metrics.heightPixels.takeIf { it > 0 } ?: fallback.heightPixels
        val density = metrics.densityDpi.takeIf { it > 0 } ?: fallback.densityDpi
        require(width > 0 && height > 0) { "无法读取屏幕尺寸" }

        // Recreate the reader when the device rotates or its display size changes.
        if (activeReader != null && activeDisplay != null &&
            (captureWidth != width || captureHeight != height)
        ) {
            releaseCaptureResources()
        }

        suspendCancellableCoroutine { continuation ->
            check(pendingCapture == null) { "截图请求正在处理中" }
            pendingCapture = continuation
            continuation.invokeOnCancellation {
                mainHandler.post {
                    if (pendingCapture === continuation) pendingCapture = null
                }
            }
            try {
                ensureVirtualDisplay(activeProjection, width, height, density)
            } catch (failure: Throwable) {
                if (pendingCapture === continuation) pendingCapture = null
                continuation.resumeWithException(failure)
            }
        }
    }

    override fun close() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseProjection()
        } else {
            mainHandler.post(::releaseProjection)
        }
    }

    private fun imageToBitmap(image: Image, width: Int, height: Int): Bitmap {
        val plane = image.planes.firstOrNull() ?: error("截图没有像素数据")
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        require(pixelStride > 0 && rowStride >= pixelStride * width) { "截图像素格式不受支持" }
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        val buffer: ByteBuffer = plane.buffer
        bitmap.copyPixelsFromBuffer(buffer)
        if (paddedWidth == width) return bitmap
        val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
        bitmap.recycle()
        return cropped
    }

    private fun ensureVirtualDisplay(
        activeProjection: MediaProjection,
        width: Int,
        height: Int,
        density: Int,
    ) {
        if (activeReader != null && activeDisplay != null) return
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        captureWidth = width
        captureHeight = height
        reader.setOnImageAvailableListener({ imageReader ->
            val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val continuation = pendingCapture
            if (continuation == null) {
                image.close()
                return@setOnImageAvailableListener
            }
            pendingCapture = null
            try {
                val bitmap = imageToBitmap(image, captureWidth, captureHeight)
                if (continuation.isActive) continuation.resume(bitmap) else bitmap.recycle()
            } catch (failure: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(failure)
            } finally {
                image.close()
            }
        }, mainHandler)
        val display = runCatching {
            activeProjection.createVirtualDisplay(
                "AiChatScreenshot",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                mainHandler,
            )
        }.getOrElse { failure ->
            reader.close()
            throw failure
        }
        activeReader = reader
        activeDisplay = display
    }

    private fun releaseCaptureResources() {
        pendingCapture?.let { continuation ->
            pendingCapture = null
            if (continuation.isActive) {
                continuation.resumeWithException(CancellationException("屏幕捕获已停止"))
            }
        }
        activeReader?.setOnImageAvailableListener(null, null)
        activeReader?.close()
        activeReader = null
        activeDisplay?.release()
        activeDisplay = null
        captureWidth = 0
        captureHeight = 0
    }

    private fun releaseProjection() {
        releaseCaptureResources()
        val current = projection
        val callback = projectionCallback
        if (current != null && callback != null) {
            runCatching { current.unregisterCallback(callback) }
        }
        projectionCallback = null
        projection = null
        current?.stop()
    }
}
