package com.example.aichat.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

data class ImageStorageStats(
    val fileCount: Int,
    val totalSizeBytes: Long,
)

interface ImageStore {
    fun mimeType(path: String): String
}

/** Copies selected media into app-private storage so the URI remains usable later. */
class ImageFileStore(private val context: Context) : ImageStore {
    /** Decode directly from the selected URI; temporary images never create a private file. */
    suspend fun importInMemory(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = 1600.0 / maxOf(1600, info.size.width, info.size.height)
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            java.io.ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "图片处理失败" }
                "data:image/jpeg;base64," + android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
            }
        } finally { bitmap.recycle() }
    }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, IMAGE_DIRECTORY).apply { mkdirs() }
        val extension = extensionFor(uri)
        val destination = File(directory, "${UUID.randomUUID()}$extension")
        val resolver = context.contentResolver
        try {
            resolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("无法读取所选图片")
            destination.absolutePath
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.parentFile?.canonicalFile == File(context.filesDir, IMAGE_DIRECTORY).canonicalFile) {
            file.delete()
        }
    }

    /** Persists a screenshot in the same private directory used by chat attachments. */
    suspend fun saveScreenshot(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, IMAGE_DIRECTORY).apply { mkdirs() }
        val destination = File(directory, "${UUID.randomUUID()}.jpg")
        try {
            destination.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    "无法保存屏幕截图"
                }
            }
            destination.absolutePath
        } catch (failure: Throwable) {
            destination.delete()
            throw failure
        }
    }

    fun getImageDirectory(): File =
        File(context.filesDir, IMAGE_DIRECTORY).apply { mkdirs() }

    suspend fun getImageStorageStats(): ImageStorageStats = withContext(Dispatchers.IO) {
        val dir = getImageDirectory()
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val totalSize = files.sumOf { it.length() }
        ImageStorageStats(files.size, totalSize)
    }

    suspend fun cleanupOrphanImages(referencedPaths: Set<String>): Int = withContext(Dispatchers.IO) {
        val dir = getImageDirectory()
        val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
        val canonicalReferenced = referencedPaths.mapNotNull { path ->
            runCatching { File(path).canonicalPath }.getOrNull()
        }.toSet()
        var deletedCount = 0
        for (file in files) {
            val canonical = runCatching { file.canonicalPath }.getOrNull() ?: continue
            if (canonical !in canonicalReferenced) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        deletedCount
    }

    override fun mimeType(path: String): String =
        context.contentResolver.getType(Uri.fromFile(File(path))) ?: when (File(path).extension.lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "bmp" -> "image/bmp"
            else -> "image/jpeg"
        }

    private fun extensionFor(uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return when (mime.lowercase()) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "image/heic" -> ".heic"
            "image/heif" -> ".heif"
            "image/bmp" -> ".bmp"
            else -> ".jpg"
        }
    }

    private companion object {
        const val IMAGE_DIRECTORY = "chat-images"
    }
}
