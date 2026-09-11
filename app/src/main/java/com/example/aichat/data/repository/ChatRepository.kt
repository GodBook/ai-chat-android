package com.example.aichat.data.repository

import com.example.aichat.data.local.ChatConversationEntity
import com.example.aichat.data.local.ChatMessageEntity
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.DEFAULT_CONVERSATION_ID
import com.example.aichat.data.model.DEFAULT_CONVERSATION_TITLE
import com.example.aichat.data.model.MessageStatus
import com.example.aichat.data.model.ProviderConfig
import com.example.aichat.data.network.ProbeResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

data class AppStorageStats(
    val conversationCount: Int,
    val messageCount: Int,
    val imageCount: Int,
    val imageSizeBytes: Long,
)

interface ChatRepository {
    val searchingConversation: Flow<String?>
        get() = kotlinx.coroutines.flow.flowOf(null)

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

    /** Newest message per conversation, used by the conversation list. */
    fun observeConversationPreviews(): Flow<List<ChatMessage>> = observeAllMessages()

    /** Whether any conversation currently has an assistant response in flight. */
    fun observeAnyWorking(): Flow<Boolean> = observeAllMessages().map { rows ->
        rows.any { it.status == MessageStatus.SENDING || it.status == MessageStatus.STREAMING }
    }

    fun observeMessages(): Flow<List<ChatMessage>> = messages

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = messages

    suspend fun getConversation(conversationId: String): ChatConversation? = null

    suspend fun createConversation(
        title: String = DEFAULT_CONVERSATION_TITLE,
        groupName: String? = null,
    ): ChatConversation =
        throw UnsupportedOperationException("多会话功能未由此仓储实现")

    suspend fun renameConversation(conversationId: String, title: String): ChatConversation? = null

    suspend fun setConversationIcon(conversationId: String, icon: String?): Boolean = false

    suspend fun setConversationPinned(conversationId: String, isPinned: Boolean): ChatConversation? = null

    suspend fun updateConversationGroup(conversationId: String, groupName: String?): ChatConversation? = null

    suspend fun updateConversationsGroup(conversationIds: Collection<String>, groupName: String?): Int = 0

    suspend fun renameGroup(oldGroupName: String, newGroupName: String): Int = 0

    suspend fun deleteConversation(conversationId: String): Boolean = false
 
    suspend fun deleteConversations(conversationIds: Collection<String>): Int = 0

    /** Inserts a user message and streams an assistant response. Returns the assistant id. */
    suspend fun sendMessage(
        text: String,
        imagePaths: List<String> = emptyList(),
        webSearch: Boolean = false,
    ): String = sendMessage(DEFAULT_CONVERSATION_ID, text, imagePaths, webSearch)

    /** Inserts and streams a message in the selected chat. */
    suspend fun sendMessage(
        conversationId: String,
        text: String,
        imagePaths: List<String> = emptyList(),
        webSearch: Boolean = false,
    ): String

    /** Re-runs the user request associated with a failed or interrupted assistant message. */
    suspend fun retryMessage(messageId: String): String?

    suspend fun retryMessage(conversationId: String, messageId: String): String? = retryMessage(messageId)

    /** Regenerates an assistant message using preceding context. */
    suspend fun regenerateMessage(conversationId: String, messageId: String): String? = null

    /** Deletes a single message and cleans up any referenced images. */
    suspend fun deleteMessage(messageId: String): Boolean = false

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

    suspend fun getAllConversations(): List<ChatConversation> = emptyList()

    suspend fun getAllMessages(): List<ChatMessage> = emptyList()

    suspend fun getStorageStats(): AppStorageStats = AppStorageStats(0, 0, 0, 0L)

    suspend fun cleanupOrphanImages(): Int = 0

    suspend fun restoreBackupData(
        conversations: List<ChatConversationEntity>,
        messages: List<ChatMessageEntity>,
    ) {}

    suspend fun probeModelConnection(config: ProviderConfig): ProbeResult =
        ProbeResult(false, 0L, "未实现")
}
