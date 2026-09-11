package com.example.aichat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPersonaAndProviderProfileTest {

    @Test
    fun `built-in personas are comprehensive and valid`() {
        assertEquals(12, BUILT_IN_PERSONAS.size)
        assertTrue(BUILT_IN_PERSONAS.all { it.isBuiltIn })
        assertTrue(BUILT_IN_PERSONAS.none { it.isCustom })
        assertTrue(BUILT_IN_PERSONAS.all { it.name.isNotBlank() })
        assertTrue(BUILT_IN_PERSONAS.all { it.avatar.isNotBlank() })
        assertTrue(BUILT_IN_PERSONAS.all { it.systemPrompt.isNotBlank() })
        assertTrue(BUILT_IN_PERSONAS.all { it.temperature in 0.0f..2.0f })
    }

    @Test
    fun `custom persona properties behave as expected`() {
        val custom = ChatPersona(
            id = "persona_custom_test",
            name = "测试导师",
            avatar = "🧪",
            description = "测试用描述",
            systemPrompt = "测试系统提示词",
            temperature = 0.5f,
            category = "custom",
            isBuiltIn = false,
        )

        assertTrue(custom.isCustom)
        assertFalse(custom.isBuiltIn)
        assertEquals("测试导师", custom.name)

        val entity = custom.toEntity()
        val backToDomain = entity.toDomain()
        assertEquals(custom, backToDomain)
    }

    @Test
    fun `built-in provider profiles are comprehensive and valid`() {
        assertEquals(6, BUILT_IN_PROVIDER_PROFILES.size)
        val deepseek = BUILT_IN_PROVIDER_PROFILES.first { it.id == DEFAULT_PROVIDER_PROFILE_ID }
        assertTrue(deepseek.isDefault)
        assertFalse(deepseek.isCustom)
        assertEquals("deepseek", deepseek.presetType)
        assertEquals("DeepSeek", deepseek.presetLabel)
        assertEquals("deepseek-chat", deepseek.model)
    }

    @Test
    fun `custom provider profile properties and entity mapping work`() {
        val customProfile = ProviderProfile(
            id = "profile_custom_openai_proxy",
            name = "私人 OpenAI 反代",
            baseUrl = "https://my-proxy.example.com/v1",
            defaultModel = "gpt-4o",
            candidateModels = listOf("gpt-4o", "gpt-4o-mini"),
            visionEnabled = true,
            presetType = "custom",
        )

        assertTrue(customProfile.isCustom)
        assertEquals("自定义", customProfile.presetLabel)
        assertEquals("gpt-4o", customProfile.model)

        val entity = customProfile.toEntity()
        val domain = entity.toDomain()
        assertEquals(customProfile, domain)
    }

    @Test
    fun `chat message preserves token analytics and duration through entity mapping`() {
        val msg = ChatMessage(
            id = "msg_token_test",
            role = MessageRole.ASSISTANT,
            text = "这是 AI 回复内容",
            promptTokens = 25,
            completionTokens = 80,
            totalTokens = 105,
            generationDurationMs = 1200L,
            status = MessageStatus.SENT,
        )

        val entity = msg.toEntity()
        assertEquals(25, entity.promptTokens)
        assertEquals(80, entity.completionTokens)
        assertEquals(105, entity.totalTokens)
        assertEquals(1200L, entity.generationDurationMs)

        val domain = entity.toDomain()
        assertEquals(msg, domain)
    }

    @Test
    fun `conversation preserves context window and persona profile ids through mapping`() {
        val conv = ChatConversation(
            id = "conv_window_test",
            title = "多轮深度会话",
            createdAt = 1000L,
            updatedAt = 2000L,
            contextWindowLimit = 16,
            personaId = "persona_senior_architect",
            providerProfileId = "profile_siliconflow",
        )

        val entity = conv.toEntity()
        assertEquals(16, entity.contextWindowLimit)
        assertEquals("persona_senior_architect", entity.personaId)
        assertEquals("profile_siliconflow", entity.providerProfileId)

        val domain = entity.toDomain()
        assertEquals(conv, domain)
    }
}
