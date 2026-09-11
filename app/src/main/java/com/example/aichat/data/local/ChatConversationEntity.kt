package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_conversations",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["isPinned", "updatedAt"]),
        Index(value = ["sortOrder"]),
    ],
)
data class ChatConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val groupName: String? = null,
    val isPinned: Boolean = false,
    val icon: String? = null,
    val personaId: String? = null,
    val providerProfileId: String? = null,
    val contextWindowLimit: Int = 8,
    val sortOrder: Int = 0,
)
