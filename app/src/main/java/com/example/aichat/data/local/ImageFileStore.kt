package com.example.aichat.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

interface ImageStore {
    fun mimeType(path: String): String
}

/** Copies selected media into app-private storage so the URI remains usable later. */
class ImageFileStore(private val context: Context) : ImageStore {
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
