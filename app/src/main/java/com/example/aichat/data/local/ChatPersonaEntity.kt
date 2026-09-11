package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.aichat.data.model.ChatPersona

@Entity(
    tableName = "chat_personas",
    indices = [
        Index(value = ["category"]),
        Index(value = ["createdAt"]),
    ],
)
data class ChatPersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatar: String,
    val description: String,
    val systemPrompt: String,
    val temperature: Float = 1.0f,
    val preferredModel: String? = null,
    val category: String = "general",
    val isBuiltIn: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

