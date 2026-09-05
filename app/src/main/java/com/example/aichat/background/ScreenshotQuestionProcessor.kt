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
    suspend fun process(
        bitmap: Bitmap,
        prompt: String,
        shortAnswerModeEnabled: Boolean = false,
    ): String {
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
            val question = prompt.trim().ifEmpty { DEFAULT_SCREENSHOT_PROMPT } +
                if (shortAnswerModeEnabled) {
                    "\n若图片是选择题或判断题，请在完整回答最后单独输出机器标记：选择题用 [简答:A]（仅填 A/B/C/D），判断题用 [简答:正确] 或 [简答:错误]。若不是这两类题目，不输出该标记。"
                } else {
                    ""
                }
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

/** Compact answer extracted from a screenshot answer, when it is unambiguous. */
sealed interface ShortAnswerIndicator {
    data class Choice(val option: Int) : ShortAnswerIndicator
    data class Judgment(val correct: Boolean) : ShortAnswerIndicator
}

fun extractShortAnswerIndicator(answer: String): ShortAnswerIndicator? {
    val text = answer
        .replace('\uFF21', 'A').replace('\uFF22', 'B').replace('\uFF23', 'C').replace('\uFF24', 'D')
        .replace("**", "")
        .trim()
    if (text.isEmpty()) return null
    val marked = Regex("(?i)\\[简答\\s*[:：]\\s*(正确|错误|对|错|是|否|true|false|[abcd])\\]").find(text)
    if (marked != null) {
        val value = marked.groupValues[1].lowercase()
        if (value in setOf("正确", "错误", "对", "错", "是", "否", "true", "false")) {
            return ShortAnswerIndicator.Judgment(value in setOf("正确", "对", "是", "true"))
        }
        return ShortAnswerIndicator.Choice(value[0] - 'a')
    }
    val judgment = Regex(
        "(?i)(?:正确答案|答案|结论|判断)\\s*(?:是|为|选)?\\s*[:：]?\\s*(正确|错误|对|错|是|否|true|false)",
    ).find(text)
        ?: Regex("(?i)(?:^|[\\n。.!！])\\s*(正确|错误|对|错|是|否|true|false)\\s*(?:[。.!！]|$)").find(text)
    if (judgment != null) {
        val value = judgment.groupValues.last().lowercase()
        return ShortAnswerIndicator.Judgment(value in setOf("正确", "对", "是", "true"))
    }
    val choice = Regex(
        "(?i)(?:正确选项|正确答案|答案|选择|选项|选)\\s*(?:是|为|选)?\\s*[:：]?\\s*(?:选项\\s*)?([abcd])(?:\\b|[)）。、：:.])",
    ).find(text)
        ?: Regex("(?i)(?:^|[\\n])\\s*[（(]?([abcd])(?:[)）.]|[：:、。](?=\\s|$)|$)").find(text)
    return choice?.groupValues?.last()?.lowercase()?.firstOrNull()?.let {
        ShortAnswerIndicator.Choice(it - 'a')
    }
}
