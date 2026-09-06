package com.example.aichat.ui

import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class 聊天筛选Test {
    @Test
    fun `matches title and latest preview without changing order`() {
        val conversations = listOf(
            ChatConversation("one", "工作计划", 1L, 3L),
            ChatConversation("two", "旅行", 2L, 2L),
        )
        val previews = mapOf(
            "one" to ChatMessage("m1", com.example.aichat.data.model.MessageRole.ASSISTANT, "下周安排"),
            "two" to ChatMessage("m2", com.example.aichat.data.model.MessageRole.USER, "周末路线"),
        )

        assertEquals(listOf("one"), filterConversations(conversations, previews, "安排").map { it.id })
        assertEquals(listOf("two"), filterConversations(conversations, previews, "旅").map { it.id })
        assertEquals(listOf("one", "two"), filterConversations(conversations, previews, "  ").map { it.id })
    }
}
