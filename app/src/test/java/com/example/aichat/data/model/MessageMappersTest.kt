package com.example.aichat.data.model

import com.example.aichat.data.local.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageMappersTest {
    @Test
    fun `image paths survive Room json mapping`() {
        val original = ChatMessage(
            id = "message-1",
            role = MessageRole.USER,
            text = "看图",
            imagePaths = listOf("/data/user/0/app/chat-images/a.jpg", "/tmp/b.png"),
            status = MessageStatus.SENT,
            requestId = "request-1",
            createdAt = 123L,
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
