package com.example.aichat.background

import android.graphics.Bitmap
import com.example.aichat.AiChatApplication
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_PROMPT
import com.example.aichat.data.model.MessageStatus
import com.example.aichat.data.network.ChatClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Saves a captured bitmap, sends it in a new chat, and returns the completed answer. */
class ScreenshotQuestionProcessor(private val app: AiChatApplication) {
    suspend fun process(bitmap: Bitmap, prompt: String): String {
        var imagePath: String? = null
        var requestWasPersisted = false
        var sendStarted = false
        try {
            val savedImagePath = try {
                app.container.imageFileStore.saveScreenshot(bitmap)
            } finally {
                bitmap.recycle()
            }
            imagePath = savedImagePath
            val conversation = app.container.chatRepository.createConversation("截屏问答")
            val question = prompt.trim().ifEmpty { DEFAULT_SCREENSHOT_PROMPT }
            sendStarted = true
            val assistantId = try {
                app.container.chatRepository.sendMessage(
                    conversation.id,
                    question,
                    listOf(savedImagePath),
                )
            } catch (failure: ChatClientException) {
                requestWasPersisted = failure.requestWasPersisted
                throw failure
            }
            requestWasPersisted = true
            val message = app.container.chatRepository.observeMessages(conversation.id).first { messages ->
                messages.any {
                    it.id == assistantId && it.status in setOf(
                        MessageStatus.SENT,
                        MessageStatus.FAILED,
                        MessageStatus.INTERRUPTED,
                    )
                }
            }.firstOrNull { it.id == assistantId }
            if (message != null && message.status != MessageStatus.SENT) {
                throw IllegalStateException(message.errorMessage ?: "AI 截屏问答失败")
            }
            return message?.text?.trim().orEmpty().ifEmpty { "AI 没有返回可显示的内容" }
        } catch (cancelled: CancellationException) {
            if (!sendStarted && !requestWasPersisted) {
                imagePath?.let { runCatching { app.container.imageFileStore.delete(it) } }
            }
            throw cancelled
        } catch (failure: Throwable) {
            if (!requestWasPersisted) {
                imagePath?.let { runCatching { app.container.imageFileStore.delete(it) } }
            }
            throw failure
        }
    }
}
