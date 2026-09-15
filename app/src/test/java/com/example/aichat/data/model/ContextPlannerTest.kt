package com.example.aichat.data.model

import org.junit.Assert.*
import org.junit.Test

class ContextPlannerTest {
    private fun user(id: String, text: String = id) = ChatMessage(id, MessageRole.USER, text, requestId = id)
    private fun answer(id: String, request: String, text: String = id) = ChatMessage(id, MessageRole.ASSISTANT, text, requestId = request)
    private val current = ChatRequestMessage(MessageRole.USER, "继续")

    @Test fun selectedVersionIsTheOnlyAnswerInActualMessages() {
        val history = listOf(user("q"), answer("a", "q"), answer("b", "q"))
        val plan = ContextPlanner.build(history, current, "test", selections = mapOf("q" to "a"))
        assertEquals(listOf("q", "a", "继续"), plan.messages.map { it.text })
        assertEquals("a", plan.entries.single().assistantId)
    }
    @Test fun roundsAreWholeAndCurrentQuestionDoesNotConsumeHistorySlot() {
        val history = listOf(user("q1"), answer("a1", "q1"), user("q2"), answer("a2", "q2"))
        val plan = ContextPlanner.build(history, current, "test", roundLimit = 1)
        assertEquals(listOf("q2", "a2", "继续"), plan.messages.map { it.text })
        assertEquals("超出历史轮数", plan.entries.first().reason)
    }
    @Test fun manualExclusionDoesNotChangeHistory() {
        val history = listOf(user("q1"), answer("a1", "q1"), user("q2"), answer("a2", "q2"))
        val plan = ContextPlanner.build(history, current, "test", options = ContextOptions(setOf("q2")))
        assertEquals(listOf("q1", "a1", "继续"), plan.messages.map { it.text })
        assertEquals(4, history.size)
        assertEquals("手动排除", plan.entries.last().reason)
    }
    @Test fun failedVersionFallsBackAndIncompleteRoundIsExcluded() {
        val plan = ContextPlanner.build(listOf(user("q"), answer("a", "q"), answer("b", "q").copy(status = MessageStatus.FAILED), user("unfinished")), current, "test", selections = mapOf("q" to "b"))
        assertEquals(listOf("q", "a", "继续"), plan.messages.map { it.text })
        assertEquals("未完成的问答", plan.entries.last().reason)
    }
    @Test fun imagesAndSystemPromptParticipateInBudgetWithoutTruncatingCurrent() {
        val history = listOf(user("q", "x".repeat(45_000)), answer("a", "q"))
        val now = current.copy(imagePaths = listOf("image"))
        val plan = ContextPlanner.build(history, now, "test", systemPrompt = "s".repeat(2_000))
        assertEquals("超出字符预算", plan.entries.single().reason)
        assertEquals(listOf(MessageRole.SYSTEM, MessageRole.USER), plan.messages.map { it.role })
        assertEquals(1, plan.imageCount)
    }
    @Test(expected = IllegalArgumentException::class) fun mandatoryMaterialsCannotBeSilentlyDropped() {
        ContextPlanner.build(emptyList(), current.copy(text = "x".repeat(48_001)), "test")
    }
    @Test fun cardIsExplicitUserMaterialAndDoesNotAddSystemInstructions() {
        val card = KnowledgeCard("c", title = "约束", body = "离线可编辑", kind = "约束", createdAt = 1, updatedAt = 1)
        val body = ContextPlanner.withCards("继续", listOf(card))
        val plan = ContextPlanner.build(emptyList(), current.copy(text = body), "test", options = ContextOptions(cards = listOf(card)))
        assertEquals(MessageRole.USER, plan.messages.single().role)
        assertTrue(plan.messages.single().text.contains("离线可编辑"))
        assertEquals(card, plan.cards.single())
    }
    @Test(expected = IllegalArgumentException::class) fun tooManyCardsFailBeforeRequest() {
        ContextPlanner.withCards("继续", (0..8).map { KnowledgeCard("$it", title = "卡", body = "内容", createdAt = 1, updatedAt = 1) })
    }
}
