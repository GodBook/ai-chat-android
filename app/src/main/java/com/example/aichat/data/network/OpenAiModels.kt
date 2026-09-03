package com.example.aichat.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatCompletionChunk(
    val choices: List<ChunkChoice> = emptyList(),
    val error: ProviderErrorBody? = null,
)

@Serializable
internal data class ChunkChoice(
    val delta: ChunkDelta = ChunkDelta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class ChunkDelta(
    val content: String? = null,
)

@Serializable
internal data class ProviderErrorEnvelope(
    val error: ProviderErrorBody? = null,
)

@Serializable
internal data class ProviderErrorBody(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null,
)
