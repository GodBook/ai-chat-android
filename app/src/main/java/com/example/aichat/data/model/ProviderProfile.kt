package com.example.aichat.data.model

import kotlinx.serialization.Serializable

enum class ProviderType(val key: String, val label: String) {
    DEEPSEEK("deepseek", "DeepSeek"),
    SILICONFLOW("siliconflow", "硅基流动"),
    OPENAI("openai", "OpenAI"),
    QWEN("qwen", "通义千问"),
    MOONSHOT("moonshot", "月之暗面"),
    CUSTOM("custom", "自定义"),
}

@Serializable
data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val candidateModels: List<String> = emptyList(),
    val visionEnabled: Boolean = true,
    val customHeaders: Map<String, String> = emptyMap(),
    val isDefault: Boolean = false,
    val presetType: String = "custom",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val model: String get() = defaultModel
    val isCustom: Boolean get() = presetType == "custom" || id.startsWith("profile_custom_")
    val providerType: ProviderType get() = ProviderType.entries.firstOrNull { it.key.equals(presetType, ignoreCase = true) } ?: ProviderType.CUSTOM
    val presetLabel: String get() = providerType.label
}

val DEFAULT_PROVIDER_PROFILE_ID = "profile_deepseek_official"

val BUILT_IN_PROVIDER_PROFILES: List<ProviderProfile> = listOf(
    ProviderProfile(
        id = DEFAULT_PROVIDER_PROFILE_ID,
        name = "DeepSeek 官方",
        baseUrl = DEFAULT_BASE_URL,
        defaultModel = "deepseek-chat",
        candidateModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner",
            DEFAULT_MODEL,
            FALLBACK_MODEL,
        ),
        visionEnabled = true,
        isDefault = true,
        presetType = "deepseek",
        sortOrder = 0,
    ),
    ProviderProfile(
        id = "profile_siliconflow",
        name = "硅基流动 (SiliconFlow)",
        baseUrl = "https://api.siliconflow.cn/v1",
        defaultModel = "deepseek-ai/DeepSeek-V3",
        candidateModels = listOf(
            "deepseek-ai/DeepSeek-V3",
            "deepseek-ai/DeepSeek-R1",
            "Qwen/Qwen2.5-72B-Instruct",
            "Pro/deepseek-ai/DeepSeek-V3",
        ),
        visionEnabled = true,
        isDefault = false,
        presetType = "siliconflow",
        sortOrder = 1,
    ),
    ProviderProfile(
        id = "profile_openai",
        name = "OpenAI 官方",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        candidateModels = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "gpt-4.1-mini",
            "o1-mini",
            "o3-mini",
        ),
        visionEnabled = true,
        isDefault = false,
        presetType = "openai",
        sortOrder = 2,
    ),
    ProviderProfile(
        id = "profile_qwen_dashscope",
        name = "阿里云百炼 (通义千问)",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        defaultModel = "qwen-plus",
        candidateModels = listOf(
            "qwen-plus",
            "qwen-max",
            "qwen-turbo",
            "qwen-vl-max",
        ),
        visionEnabled = true,
        isDefault = false,
        presetType = "qwen",
        sortOrder = 3,
    ),
    ProviderProfile(
        id = "profile_moonshot",
        name = "月之暗面 (Kimi)",
        baseUrl = "https://api.moonshot.cn/v1",
        defaultModel = "moonshot-v1-8k",
        candidateModels = listOf(
            "moonshot-v1-8k",
            "moonshot-v1-32k",
            "moonshot-v1-128k",
        ),
        visionEnabled = false,
        isDefault = false,
        presetType = "moonshot",
        sortOrder = 4,
    ),
    ProviderProfile(
        id = "profile_ollama_local",
        name = "本地内网 Ollama",
        baseUrl = "http://192.168.1.100:11434/v1",
        defaultModel = "deepseek-r1:8b",
        candidateModels = listOf(
            "deepseek-r1:8b",
            "deepseek-r1:14b",
            "qwen2.5:7b",
            "llama3.1:8b",
        ),
        visionEnabled = true,
        isDefault = false,
        presetType = "ollama",
        sortOrder = 5,
    ),
)
