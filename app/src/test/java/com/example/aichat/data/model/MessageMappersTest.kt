package com.example.aichat.data.model

import com.example.aichat.data.local.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageMappersTest {
    @Test
    fun `image paths and thinking fields survive Room json mapping`() {
        val original = ChatMessage(
            id = "message-1",
            role = MessageRole.ASSISTANT,
            text = "看图回答",
            imagePaths = listOf("/data/user/0/app/chat-images/a.jpg", "/tmp/b.png"),
            status = MessageStatus.SENT,
            requestId = "request-1",
            createdAt = 123L,
            thinkingContent = "我正在深度思考...",
            thinkingDurationMs = 4500L,
        )

        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun `malformed image json falls back to an empty list`() {
        val row = ChatMessageEntity(
            id = "message-1",
            role = MessageRole.ASSISTANT.name,
            text = "ok",
            imagePaths = "not-json",
            status = MessageStatus.SENT.name,
            createdAt = 123L,
        )

        assertEquals(emptyList<String>(), row.toDomain().imagePaths)
    }
}
