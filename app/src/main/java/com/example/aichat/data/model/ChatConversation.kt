package com.example.aichat.data.model

/**
 * Stable id used by the first version of the app before multiple chats were
 * introduced. Keeping it constant lets the Room migration attach all legacy
 * messages to one conversation without changing their ids.
 */
const val DEFAULT_CONVERSATION_ID: String = "default"

const val DEFAULT_CONVERSATION_TITLE: String = "默认聊天"
const val DEFAULT_GROUP_NAME: String = "未分组"

data class ChatConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val groupName: String? = null,
    val isPinned: Boolean = false,
    val icon: String? = null,
    val personaId: String? = null,
    val providerProfileId: String? = null,
    val contextWindowLimit: Int = 8,
)
