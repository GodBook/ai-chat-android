package com.example.aichat.background

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resumeWithException

/** User-selected root capture; no shared screenshot file and no fallback to another backend. */
internal object RootScreenCapture {
    suspend fun capture(): Bitmap = try {
        withTimeout(20_000L) {
            suspendCancellableCoroutine { continuation ->
                val processRef = AtomicReference<Process?>()
                val worker = Executors.newSingleThreadExecutor()
                continuation.invokeOnCancellation {
                    processRef.get()?.destroyForcibly()
                    worker.shutdownNow()
                }
                worker.execute {
                    var process: Process? = null
                    try {
                        if (!continuation.isActive) return@execute
                        // Fixed command only. Do not interpolate prompts, paths, or other user input.
                        process = ProcessBuilder("su", "-c", "/system/bin/screencap -p")
                            .redirectError(ProcessBuilder.Redirect.to(File("/dev/null")))
                            .start()
                        processRef.set(process)
                        if (!continuation.isActive) return@execute
                        process.outputStream.close()
                        val png = process.inputStream.use { input ->
                            val bytes = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                check(bytes.size() + count <= 32 * 1024 * 1024) { "Root 截图数据过大" }
                                bytes.write(buffer, 0, count)
                            }
                            bytes.toByteArray()
                        }
                        check(process.waitFor() == 0) { "Root 截图失败，请在 Root 管理器中授权本应用" }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
                        check(bounds.outWidth > 0 && bounds.outHeight > 0 &&
                            bounds.outWidth.toLong() * bounds.outHeight <= 32_000_000L) {
                            "Root 未返回有效的屏幕图像"
                        }
                        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
                            ?: error("无法解码 Root 截图")
                        continuation.resume(bitmap) { _, value, _ -> value.recycle() }
                    } catch (failure: Throwable) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(
                                failure.message ?: "Root 不可用，请检查设备授权", failure,
                            ))
                        }
                    } finally {
                        process?.destroyForcibly()
                        runCatching { process?.inputStream?.close() }
                        runCatching { process?.errorStream?.close() }
                        runCatching { process?.outputStream?.close() }
                        worker.shutdown()
                    }
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        error("Root 截图超时，请先在 Root 管理器中授权后重试")
    }
}
