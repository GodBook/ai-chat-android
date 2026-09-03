package com.example.aichat.data.repository

import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.DEFAULT_CONVERSATION_TITLE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface ChatRepository {
    /** All saved chats, ordered by most recently changed. */
    val conversations: Flow<List<ChatConversation>>
        get() = emptyFlow()

    fun observeConversations(): Flow<List<ChatConversation>> = conversations

    /**
     * Legacy alias for callers that only support the original single-chat
     * screen. New code should call [observeMessages] with an id.
     */
    val messages: Flow<List<ChatMessage>>

    /** All messages across chats, used for list previews and migration-safe recovery. */
    fun observeAllMessages(): Flow<List<ChatMessage>> = messages

    fun observeMessages(): Flow<List<ChatMessage>> = messages

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = messages

    suspend fun getConversation(conversationId: String): ChatConversation? = null

    suspend fun createConversation(title: String = DEFAULT_CONVERSATION_TITLE): ChatConversation =
        throw UnsupportedOperationException("多会话功能未由此仓储实现")

    suspend fun renameConversation(conversationId: String, title: String): ChatConversation? = null

    suspend fun deleteConversation(conversationId: String): Boolean = false

    /** Inserts a user message and streams an assistant response. Returns the assistant id. */
    suspend fun sendMessage(text: String, imagePaths: List<String> = emptyList()): String

    /** Inserts and streams a message in the selected chat. */
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        imagePaths: List<String> = emptyList(),
    ): String = sendMessage(text, imagePaths)

    /** Re-runs the user request associated with a failed or interrupted assistant message. */
    suspend fun retryMessage(messageId: String): String?

    suspend fun retryMessage(conversationId: String, messageId: String): String? = retryMessage(messageId)

    /** Stops only the currently running model request. Partial text is kept. */
    fun stopGeneration()

    suspend fun clearConversation()

    suspend fun clearConversation(conversationId: String) {
        clearConversation()
    }

    /** Marks requests that cannot survive process death as interrupted. */
    suspend fun recoverInterruptedMessages()

    suspend fun recoverInterruptedMessages(conversationId: String) {
        recoverInterruptedMessages()
    }
}
