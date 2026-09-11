package com.example.aichat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBranchingAndTemplateTest {

    @Test
    fun `resolveMessageBranches groups assistant replies by requestId and defaults to latest`() {
        val userMsg = ChatMessage(
            id = "u1",
            role = MessageRole.USER,
            text = "解释一下量子计算",
            requestId = "req-1",
            createdAt = 1000L,
        )
        val assistantV1 = ChatMessage(
            id = "a1_v1",
            role = MessageRole.ASSISTANT,
            text = "量子计算是基于量子力学的计算模式（第一版）",
            requestId = "req-1",
            createdAt = 1001L,
        )
        val assistantV2 = ChatMessage(
            id = "a1_v2",
            role = MessageRole.ASSISTANT,
            text = "量子计算利用量子叠加与纠缠特性实现指数级并行（第二版）",
            requestId = "req-1",
            createdAt = 1002L,
        )

        val rawList = listOf(userMsg, assistantV1, assistantV2)
        val resolved = resolveMessageBranches(rawList)

        assertEquals(2, resolved.size)
        assertEquals("u1", resolved[0].id)
        assertEquals(0, resolved[0].branchIndex)
        assertEquals(1, resolved[0].totalBranches)

        // Default to latest branch
        assertEquals("a1_v2", resolved[1].id)
        assertEquals(1, resolved[1].branchIndex)
        assertEquals(2, resolved[1].totalBranches)
    }

    @Test
    fun `resolveMessageBranches respects custom branch selection`() {
        val userMsg = ChatMessage(
            id = "u1",
            role = MessageRole.USER,
            text = "写一首短诗",
            requestId = "req-1",
            createdAt = 1000L,
        )
        val v1 = ChatMessage(id = "v1", role = MessageRole.ASSISTANT, text = "春风拂细柳", requestId = "req-1", createdAt = 1001L)
        val v2 = ChatMessage(id = "v2", role = MessageRole.ASSISTANT, text = "秋水映残阳", requestId = "req-1", createdAt = 1002L)
        val v3 = ChatMessage(id = "v3", role = MessageRole.ASSISTANT, text = "冬雪漫千山", requestId = "req-1", createdAt = 1003L)

        val rawList = listOf(userMsg, v1, v2, v3)

        // Select branch 0 (v1)
        val resolvedV1 = resolveMessageBranches(rawList, mapOf("req-1" to 0))
        assertEquals(2, resolvedV1.size)
        assertEquals("v1", resolvedV1[1].id)
        assertEquals(0, resolvedV1[1].branchIndex)
        assertEquals(3, resolvedV1[1].totalBranches)

        // Select branch 1 (v2)
        val resolvedV2 = resolveMessageBranches(rawList, mapOf("req-1" to 1))
        assertEquals("v2", resolvedV2[1].id)
        assertEquals(1, resolvedV2[1].branchIndex)
        assertEquals(3, resolvedV2[1].totalBranches)

        // Clamping out-of-bounds selection
        val resolvedClamped = resolveMessageBranches(rawList, mapOf("req-1" to 99))
        assertEquals("v3", resolvedClamped[1].id)
        assertEquals(2, resolvedClamped[1].branchIndex)
        assertEquals(3, resolvedClamped[1].totalBranches)
    }

    @Test
    fun `resolveMessageBranches handles unbranched and legacy messages seamlessly`() {
        val legacyUser = ChatMessage(id = "l1", role = MessageRole.USER, text = "旧问题", requestId = null)
        val legacyAssistant = ChatMessage(id = "l2", role = MessageRole.ASSISTANT, text = "旧回答", requestId = null)

        val resolved = resolveMessageBranches(listOf(legacyUser, legacyAssistant))
        assertEquals(2, resolved.size)
        assertEquals("l1", resolved[0].id)
        assertEquals(0, resolved[0].branchIndex)
        assertEquals(1, resolved[0].totalBranches)
        assertEquals("l2", resolved[1].id)
        assertEquals(0, resolved[1].branchIndex)
        assertEquals(1, resolved[1].totalBranches)
    }

    @Test
    fun `prompt templates are valid and slash commands can be matched`() {
        assertTrue(PROMPT_TEMPLATES.isNotEmpty())
        for (template in PROMPT_TEMPLATES) {
            assertTrue(template.command.startsWith("/"))
            assertTrue(template.title.isNotBlank())
            assertTrue(template.category.isNotBlank())
            assertTrue(template.description.isNotBlank())
            assertTrue(template.template.isNotBlank())
        }

        // Test slash command filtering
        val polishMatches = PROMPT_TEMPLATES.filter { it.command.startsWith("/po") }
        assertEquals(1, polishMatches.size)
        assertEquals("/polish", polishMatches.first().command)

        val codeMatches = PROMPT_TEMPLATES.filter { it.category == "编程技术" }
        assertTrue(codeMatches.size >= 3)
    }
}
