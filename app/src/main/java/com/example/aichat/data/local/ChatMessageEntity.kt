package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/** A Room row. Lists are encoded as a JSON array to keep the schema stable. */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["requestId"]),
        Index(value = ["conversationId"]),
    ],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val role: String,
    val text: String,
    val imagePaths: String = "[]",
    val status: String,
    val requestId: String? = null,
    val createdAt: Long,
    val errorMessage: String? = null,
    @ColumnInfo(defaultValue = "'default'")
    val conversationId: String = "default",
)
