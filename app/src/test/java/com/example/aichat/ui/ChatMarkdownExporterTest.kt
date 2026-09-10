package com.example.aichat.ui

import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import com.example.aichat.ui.export.ChatMarkdownExporter
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMarkdownExporterTest {
    @Test
    fun `formats markdown with title, model, user, assistant and thinking`() {
        val messages = listOf(
            ChatMessage(
                id = "m1",
                role = MessageRole.USER,
                text = "如何写好 Kotlin 代码？",
                imagePaths = listOf("mock/path/img.png"),
            ),
            ChatMessage(
                id = "m2",
                role = MessageRole.ASSISTANT,
                text = "建议遵循官方编码规范，并善用协程与密封类。",
                thinkingContent = "需要考虑习惯用法、空安全与协程并发安全...",
                thinkingDurationMs = 3500L,
            ),
        )

        val markdown = ChatMarkdownExporter.formatMarkdown(
            conversationTitle = "Kotlin 技巧",
            messages = messages,
            modelName = "deepseek-reasoner",
            exportTimeMs = 1757497200000L,
        )

        assertTrue(markdown.startsWith("# Kotlin 技巧"))
        assertTrue(markdown.contains("所用模型：deepseek-reasoner"))
        assertTrue(markdown.contains("### 👤 用户"))
        assertTrue(markdown.contains("如何写好 Kotlin 代码？"))
        assertTrue(markdown.contains("[附带图片 1 张]"))
        assertTrue(markdown.contains("### 🤖 AI (deepseek-reasoner)"))
        assertTrue(markdown.contains("<details>"))
        assertTrue(markdown.contains("<summary>🧠 思考过程（用时 3 秒）</summary>"))
        assertTrue(markdown.contains("需要考虑习惯用法、空安全与协程并发安全..."))
        assertTrue(markdown.contains("建议遵循官方编码规范，并善用协程与密封类。"))
        assertTrue(markdown.contains("— 对话由 AI BOTOY 导出 —"))
    }
}
