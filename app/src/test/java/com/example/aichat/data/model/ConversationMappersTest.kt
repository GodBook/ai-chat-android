package com.example.aichat.data.model

import com.example.aichat.data.local.ChatConversationEntity
import com.example.aichat.data.local.ChatMessageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationMappersTest {
    @Test
    fun `conversation and message keep their conversation id through Room mapping`() {
        val conversation = ChatConversation(
            id = "conversation-1",
            title = "工作问题",
            createdAt = 10L,
            updatedAt = 20L,
        )
        val message = ChatMessage(
            id = "message-1",
            conversationId = conversation.id,
            role = MessageRole.ASSISTANT,
            text = "好的",
            status = MessageStatus.SENT,
            createdAt = 30L,
        )

        assertEquals(conversation, conversation.toEntity().toDomain())
        assertEquals(message, message.toEntity().toDomain())
    }

    @Test
    fun `legacy entity without explicit conversation id maps to default chat`() {
        val row = ChatMessageEntity(
            id = "legacy-message",
            role = MessageRole.USER.name,
            text = "旧消息",
            status = MessageStatus.SENT.name,
            createdAt = 1L,
        )

        assertEquals(DEFAULT_CONVERSATION_ID, row.toDomain().conversationId)
    }

    @Test
    fun `conversation timestamps and title are preserved`() {
        val row = ChatConversationEntity(
            id = "conversation-2",
            title = "随手记",
            createdAt = 100L,
            updatedAt = 200L,
        )

        assertEquals(row, row.toDomain().toEntity())
    }
}
