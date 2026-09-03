package com.example.aichat.data.model

import com.example.aichat.data.local.ChatMessageEntity
import com.example.aichat.data.local.ChatConversationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

private val imagePathJson = Json { ignoreUnknownKeys = true }

internal fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = role.toMessageRole(),
    text = text,
    imagePaths = decodeImagePaths(imagePaths),
    status = status.toMessageStatus(),
    requestId = requestId,
    createdAt = createdAt,
    errorMessage = errorMessage,
)

internal fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    text = text,
    imagePaths = encodeImagePaths(imagePaths),
    status = status.name,
    requestId = requestId,
    createdAt = createdAt,
    errorMessage = errorMessage,
)

internal fun ChatConversationEntity.toDomain(): ChatConversation = ChatConversation(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ChatConversation.toEntity(): ChatConversationEntity = ChatConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun String.toMessageRole(): MessageRole = runCatching {
    MessageRole.valueOf(this)
}.getOrDefault(MessageRole.USER)

internal fun String.toMessageStatus(): MessageStatus = runCatching {
    MessageStatus.valueOf(this)
}.getOrDefault(MessageStatus.SENT)

internal fun encodeImagePaths(paths: List<String>): String =
    JsonArray(paths.map { path -> JsonPrimitive(path) }).toString()

internal fun decodeImagePaths(value: String): List<String> = runCatching {
    imagePathJson.decodeFromString<JsonArray>(value).mapNotNull { it as? JsonPrimitive }.map { it.content }
}.getOrDefault(emptyList())
