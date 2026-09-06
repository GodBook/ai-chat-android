package com.example.aichat.ui

import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage

internal fun filterConversations(
    conversations: List<ChatConversation>,
    previews: Map<String, ChatMessage>,
    query: String,
): List<ChatConversation> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return conversations
    return conversations.filter { conversation ->
        conversation.title.contains(normalizedQuery, ignoreCase = true) ||
            previews[conversation.id]?.text?.contains(normalizedQuery, ignoreCase = true) == true
    }
}
