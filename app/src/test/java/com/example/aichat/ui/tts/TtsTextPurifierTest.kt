package com.example.aichat.ui.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextPurifierTest {

    @Test
    fun `purify removes code blocks and formatting markers`() {
        val markdown = """
            # 欢迎使用 AI 助手
            这是一个**粗体**与*斜体*测试。
            
            ```kotlin
            fun hello() { println("world") }
            ```
            
            这是行内代码 `val x = 10`。
            > 这是引用段落
            
            请访问 [Google](https://google.com) 查看更多。
            ![图片描述](https://example.com/img.png)
        """.trimIndent()

        val purified = TtsTextPurifier.purify(markdown)

        assertTrue("Code block should be stripped", !purified.contains("println"))
        assertTrue("Markdown link URL should be stripped", !purified.contains("https://google.com"))
        assertTrue("Headers should be cleaned", !purified.contains("#"))
        assertTrue("Asterisks should be cleaned", !purified.contains("*"))
        assertTrue("Quotes should be cleaned", !purified.contains(">"))
        assertTrue("Clean text should retain words", purified.contains("欢迎使用 AI 助手"))
        assertTrue("Clean text should retain link text", purified.contains("Google"))
    }

    @Test
    fun `purify removes short answer machine markers`() {
        val raw = "正确答案是A选项。\n[简答:A]"
        val purified = TtsTextPurifier.purify(raw)
        assertEquals("正确答案是A选项。", purified)

        val raw2 = "该说法是错误的。\n[简答:错误]"
        val purified2 = TtsTextPurifier.purify(raw2)
        assertEquals("该说法是错误的。", purified2)
    }

    @Test
    fun `splitIntoSentences splits correctly on punctuation and newlines`() {
        val text = "你好！这是第一句。你想听更多吗？当然可以，没问题！\n下一段第一句。完结。"
        val sentences = TtsTextPurifier.splitIntoSentences(text)

        assertTrue(sentences.size >= 5)
        assertEquals("你好！", sentences[0])
        assertEquals("这是第一句。", sentences[1])
        assertEquals("你想听更多吗？", sentences[2])
    }

    @Test
    fun `splitIntoSentences handles blank text safely`() {
        assertEquals(emptyList<String>(), TtsTextPurifier.splitIntoSentences(""))
        assertEquals(emptyList<String>(), TtsTextPurifier.splitIntoSentences("   \n\n  \t  "))
    }

    @Test
    fun `splitIntoSentences handles long sentences exceeding limit`() {
        val longSentence = "A".repeat(300) + "。"
        val sentences = TtsTextPurifier.splitIntoSentences(longSentence)
        assertTrue(sentences.size >= 2)
        assertTrue(sentences.all { it.length <= 150 })
    }
}
