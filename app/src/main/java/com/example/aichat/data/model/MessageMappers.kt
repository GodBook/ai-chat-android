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
    thinkingContent = thinkingContent,
    thinkingDurationMs = thinkingDurationMs,
    webSearchResults = decodeWebSearchResults(webSearchResults),
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
    thinkingContent = thinkingContent,
    thinkingDurationMs = thinkingDurationMs,
    webSearchResults = encodeWebSearchResults(webSearchResults),
)

internal fun ChatConversationEntity.toDomain(): ChatConversation = ChatConversation(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    groupName = groupName,
)

internal fun ChatConversation.toEntity(): ChatConversationEntity = ChatConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt,
    groupName = groupName,
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

internal fun encodeWebSearchResults(results: List<com.example.aichat.data.network.WebSearchResult>?): String? {
    if (results.isNullOrEmpty()) return null
    return runCatching {
        imagePathJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.example.aichat.data.network.WebSearchResult.serializer()), results)
    }.getOrNull()
}

internal fun decodeWebSearchResults(value: String?): List<com.example.aichat.data.network.WebSearchResult>? {
    if (value.isNullOrBlank()) return null
    return runCatching {
        imagePathJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.example.aichat.data.network.WebSearchResult.serializer()), value)
    }.getOrNull()
}
