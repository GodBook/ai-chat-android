package com.example.aichat.ui

import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus

/** Converts a conversation into a stable, readable plain-text export. */
internal object ChatExportFormatter {
    fun format(title: String, messages: List<ChatMessage>): String = buildString {
        appendLine(title.trim().ifEmpty { "聊天记录" })
        appendLine()
        messages.forEachIndexed { index, message ->
            appendLine(if (message.role == MessageRole.USER) "用户" else "AI")
            if (message.imagePaths.isNotEmpty()) {
                appendLine("[图片 ${message.imagePaths.size} 张]")
            }
            if (message.text.isNotBlank()) {
                appendLine(message.text.trim())
            }
            if (message.status != MessageStatus.SENT) {
                appendLine("状态：${message.status.exportLabel()}")
                message.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine("说明：$it") }
            }
            if (index != messages.lastIndex) appendLine()
        }
    }.trim()

    fun formatMultiple(conversations: List<Pair<String, List<ChatMessage>>>): String = buildString {
        appendLine("批量导出聊天记录（共 ${conversations.size} 个会话）")
        appendLine()
        conversations.forEachIndexed { index, (title, messages) ->
            appendLine("=".repeat(40))
            appendLine("会话 ${index + 1}：${title.trim().ifEmpty { "未命名聊天" }}")
            appendLine("=".repeat(40))
            appendLine()
            if (messages.isEmpty()) {
                appendLine("（暂无消息）")
            } else {
                messages.forEachIndexed { msgIndex, message ->
                    appendLine(if (message.role == MessageRole.USER) "用户" else "AI")
                    if (message.imagePaths.isNotEmpty()) {
                        appendLine("[图片 ${message.imagePaths.size} 张]")
                    }
                    if (message.text.isNotBlank()) {
                        appendLine(message.text.trim())
                    }
                    if (message.status != MessageStatus.SENT) {
                        appendLine("状态：${message.status.exportLabel()}")
                        message.errorMessage?.takeIf { it.isNotBlank() }?.let { appendLine("说明：$it") }
                    }
                    if (msgIndex != messages.lastIndex) appendLine()
                }
            }
            if (index != conversations.lastIndex) {
                appendLine()
                appendLine()
            }
        }
    }.trim()

    private fun MessageStatus.exportLabel(): String = when (this) {
        MessageStatus.SENDING -> "发送中"
        MessageStatus.STREAMING -> "生成中"
        MessageStatus.SENT -> "已完成"
        MessageStatus.FAILED -> "失败"
        MessageStatus.INTERRUPTED -> "已中断"
    }
}
