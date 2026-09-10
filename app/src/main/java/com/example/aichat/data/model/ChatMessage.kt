package com.example.aichat.data.model

const val DEFAULT_SCREENSHOT_PROMPT =
    "回答这张图片里的题目，先告诉我答案，然后再给出简短的解析。如果没有题目，就只回复没有识别到题目"

const val MAX_SCREENSHOT_PROMPT_LENGTH = 2_000

/** Default appearance for the floating screenshot answer. */
const val DEFAULT_OVERLAY_BACKGROUND_COLOR = "#CCF1FB"
const val DEFAULT_OVERLAY_GLASS_ENABLED = false

/** Default provider endpoint. DeepSeek is the out-of-the-box provider. */
const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"

/** Default model. Timed releases carry an `expires-on-` suffix and stop working afterwards. */
const val DEFAULT_MODEL = "deepseek-v4.1-flash-expires-on-0910"

/** Used automatically when [DEFAULT_MODEL] is gone, for example after it expires. */
const val FALLBACK_MODEL = "deepseek-v4-flash"

private val MODEL_FALLBACKS: Map<String, String> = mapOf(
    DEFAULT_MODEL to FALLBACK_MODEL,
)

/**
 * Models to try in order, primary first.
 *
 * Only known models get a fallback so a custom model is never silently swapped for another one.
 */
fun modelCandidatesFor(model: String): List<String> {
    val primary = model.trim()
    if (primary.isEmpty()) return listOf(DEFAULT_MODEL)
    val fallback = MODEL_FALLBACKS[primary] ?: return listOf(primary)
    return listOf(primary, fallback)
}

/** A one-tap model choice offered in the settings screen. */
data class ModelPreset(
    /** Short name shown on the chip. */
    val label: String,
    /** Model id sent to the provider. */
    val model: String,
    /** Endpoint to switch to, or null to keep whatever the user already configured. */
    val baseUrl: String? = null,
)

/** Presets keep DeepSeek first because it is the default provider. */
val MODEL_PRESETS: List<ModelPreset> = listOf(
    ModelPreset("V4.1 Flash 限时", DEFAULT_MODEL, DEFAULT_BASE_URL),
    ModelPreset("V4 Flash 备用", FALLBACK_MODEL, DEFAULT_BASE_URL),
    ModelPreset("DeepSeek Chat", "deepseek-chat", DEFAULT_BASE_URL),
    ModelPreset("DeepSeek Reasoner", "deepseek-reasoner", DEFAULT_BASE_URL),
    ModelPreset("GPT-4o mini", "gpt-4o-mini", "https://api.openai.com/v1"),
    ModelPreset("GPT-4.1 mini", "gpt-4.1-mini", "https://api.openai.com/v1"),
)

private val PRESET_ENDPOINTS: Set<String> =
    MODEL_PRESETS.mapNotNull { it.baseUrl?.trim()?.removeSuffix("/") }.toSet() +
        DEFAULT_BASE_URL.trim().removeSuffix("/")

/**
 * True when a preset may replace the endpoint.
 *
 * A self-hosted gateway or another relay must survive picking a model, so only blank or
 * stock endpoints follow the preset.
 */
fun canReplaceEndpointForPreset(baseUrl: String): Boolean {
    val normalized = baseUrl.trim().removeSuffix("/")
    return normalized.isEmpty() || normalized in PRESET_ENDPOINTS
}

/** Volume key combination that starts the background screenshot question flow. */
enum class ScreenshotTrigger(
    /** Stable value persisted in DataStore. */
    val storageKey: String,
    /** Short label shown in the settings screen. */
    val label: String,
    /** Longer explanation shown under the option. */
    val description: String,
) {
    VOLUME_DOWN(
        storageKey = "volume_down",
        label = "音量下键",
        description = "按一下就截图，操作最快，但调音量时容易误触",
    ),
    VOLUME_UP_DOWN(
        storageKey = "volume_up_down",
        label = "音量上 + 下键",
        description = "两个键一起按住才截图，不容易误触，但单手不太好按",
    ),
    ;

    companion object {
        fun fromStorageKey(value: String?): ScreenshotTrigger {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.storageKey == normalized } ?: DEFAULT_SCREENSHOT_TRIGGER
        }
    }
}

/** New installs keep the historical behaviour: a single volume-down press. */
val DEFAULT_SCREENSHOT_TRIGGER: ScreenshotTrigger = ScreenshotTrigger.VOLUME_DOWN

fun normalizeScreenshotTrigger(value: String?): ScreenshotTrigger =
    ScreenshotTrigger.fromStorageKey(value)

data class OverlayColorPreset(
    val label: String,
    val colorHex: String,
)

/** Presets are intentionally light so the existing dark answer text remains readable. */
val OVERLAY_COLOR_PRESETS = listOf(
    OverlayColorPreset("浅蓝", "#CCF1FB"),
    OverlayColorPreset("薄荷", "#DDF4E8"),
    OverlayColorPreset("杏橙", "#FFE4D1"),
    OverlayColorPreset("玫瑰", "#F9DDE6"),
    OverlayColorPreset("雾灰", "#E8EEF2"),
)

fun normalizeOverlayBackgroundColor(value: String): String {
    val normalized = value.trim().uppercase()
    return normalized.takeIf { Regex("^#[0-9A-F]{6}$").matches(it) }
        ?: DEFAULT_OVERLAY_BACKGROUND_COLOR
}

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
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val apiKey: String? = null,
    val visionEnabled: Boolean = true,
    /** Enables the background volume-down screenshot question flow. */
    val backgroundCaptureEnabled: Boolean = false,
    /** Instruction sent with screenshots captured by the background question flow. */
    val screenshotPrompt: String = DEFAULT_SCREENSHOT_PROMPT,
    /** Hex RGB color used by the floating screenshot answer background. */
    val overlayBackgroundColor: String = DEFAULT_OVERLAY_BACKGROUND_COLOR,
    /** Enables a translucent background with system blur where supported. */
    val overlayGlassEnabled: Boolean = DEFAULT_OVERLAY_GLASS_ENABLED,
    /** Shows only a one-second answer indicator for recognized choice/judgment questions. */
    val shortAnswerModeEnabled: Boolean = false,
    /** Retries with [FALLBACK_MODEL] when the configured model is rejected as unavailable. */
    val autoFallbackEnabled: Boolean = true,
    /** Volume key combination that starts the background screenshot flow. */
    val screenshotTrigger: ScreenshotTrigger = DEFAULT_SCREENSHOT_TRIGGER,
)

/** A message in the provider request, before it is encoded as JSON. */
data class ChatRequestMessage(
    val role: MessageRole,
    val text: String,
    val imagePaths: List<String> = emptyList(),
)
