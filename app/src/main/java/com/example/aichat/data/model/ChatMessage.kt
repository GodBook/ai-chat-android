package com.example.aichat.data.model

const val DEFAULT_SCREENSHOT_PROMPT =
    "回答这张图片里的题目，先告诉我答案，然后再给出简短的解析。如果没有题目，就只回复没有识别到题目"

const val MAX_SCREENSHOT_PROMPT_LENGTH = 2_000

/** The side of the conversation that produced a message. */
enum class MessageRole {
    USER,
    ASSISTANT,
}

/** Persistence and UI state of a chat message. */
enum class MessageStatus {
    SENDING,
    STREAMING,
    SENT,
    FAILED,
    INTERRUPTED,
}

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val imagePaths: List<String> = emptyList(),
    val status: MessageStatus = MessageStatus.SENT,
    val requestId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    /** The chat this message belongs to. Legacy rows are assigned the default id. */
    val conversationId: String = DEFAULT_CONVERSATION_ID,
)

data class ProviderConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val apiKey: String? = null,
    val visionEnabled: Boolean = true,
    /** Enables the background volume-down screenshot question flow. */
    val backgroundCaptureEnabled: Boolean = false,
    /** Instruction sent with screenshots captured by the background question flow. */
    val screenshotPrompt: String = DEFAULT_SCREENSHOT_PROMPT,
)

/** A message in the provider request, before it is encoded as JSON. */
data class ChatRequestMessage(
    val role: MessageRole,
    val text: String,
    val imagePaths: List<String> = emptyList(),
)
