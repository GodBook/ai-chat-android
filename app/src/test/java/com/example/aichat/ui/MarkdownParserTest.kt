package com.example.aichat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `parses common blocks and inline styles`() {
        val blocks = MarkdownDocumentParser.parse(
            """
            # 标题

            正文包含 **粗体**、*斜体*、`代码` 和 [链接](https://example.com)。

            > 引用

            2. 第二项
            3. 第三项

            ```kotlin
            val answer = 42
            ```
            """.trimIndent(),
        )

        assertTrue(blocks[0] is MarkdownBlockModel.Heading)
        val paragraph = blocks[1] as MarkdownBlockModel.Paragraph
        assertTrue(paragraph.spans.any { it.text == "粗体" && it.bold })
        assertTrue(paragraph.spans.any { it.text == "斜体" && it.italic })
        assertTrue(paragraph.spans.any { it.text == "代码" && it.code })
        assertTrue(paragraph.spans.any { it.text == "链接" && it.linkUrl == "https://example.com" })
        assertTrue(blocks[2] is MarkdownBlockModel.Quote)
        val list = blocks[3] as MarkdownBlockModel.ListBlock
        assertTrue(list.ordered)
        assertEquals(2, list.startNumber)
        assertEquals(2, list.items.size)
        val code = blocks[4] as MarkdownBlockModel.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42\n", code.code)
    }

    @Test
    fun `parses gfm table headers rows and alignment`() {
        val table = MarkdownDocumentParser.parse(
            """
            | 名称 | 数值 | 说明 |
            | :--- | ---: | :--: |
            | **速度** | 42 | `正常` |
            """.trimIndent(),
        ).single() as MarkdownBlockModel.Table

        assertEquals(2, table.rows.size)
        assertTrue(table.rows.first().cells.all { it.header })
        assertEquals(MarkdownTableAlignment.START, table.rows[1].cells[0].alignment)
        assertEquals(MarkdownTableAlignment.END, table.rows[1].cells[1].alignment)
        assertEquals(MarkdownTableAlignment.CENTER, table.rows[1].cells[2].alignment)
        assertTrue(table.rows[1].cells[0].spans.single { it.text == "速度" }.bold)
        assertTrue(table.rows[1].cells[2].spans.single { it.text == "正常" }.code)
    }

    @Test
    fun `keeps unsafe link text but does not expose clickable uri`() {
        val paragraph = MarkdownDocumentParser.parse("[不要打开](javascript:alert(1))")
            .single() as MarkdownBlockModel.Paragraph

        assertEquals("不要打开", paragraph.spans.joinToString(separator = "") { it.text })
        assertNull(paragraph.spans.single().linkUrl)
        assertFalse(paragraph.spans.single().code)
    }

    @Test
    fun `parses block math formulas and inline math formulas`() {
        val s = "$"
        val markdown = """
            这是行内公式：${s}E = mc^2${s} 以及 \(a^2 + b^2 = c^2\)。

            $s$s
            \int_0^\infty e^{-x^2} dx = \frac{\sqrt{\pi}}{2}
            $s$s

            \[
            f(x) = \sum_{i=1}^n x_i
            \]

            ```latex
            \lim_{x \to 0} \frac{\sin x}{x} = 1
            ```
        """.trimIndent()

        val blocks = MarkdownDocumentParser.parse(markdown)
        assertEquals(4, blocks.size)

        // 1. Paragraph with inline math
        val p = blocks[0] as MarkdownBlockModel.Paragraph
        assertTrue(p.spans.any { it.text == "E = mc^2" && it.math })
        assertTrue(p.spans.any { it.text == "a^2 + b^2 = c^2" && it.math })

        // 2. Block math $$...$$
        val m1 = blocks[1] as MarkdownBlockModel.MathBlock
        assertTrue(m1.formula.contains("\\int_0^\\infty"))

        // 3. Block math \[...\]
        val m2 = blocks[2] as MarkdownBlockModel.MathBlock
        assertTrue(m2.formula.contains("\\sum_{i=1}^n"))

        // 4. Code block with latex language
        val m3 = blocks[3] as MarkdownBlockModel.MathBlock
        assertTrue(m3.formula.contains("\\lim_{x \\to 0}"))
    }

    @Test
    fun `parses strikethrough formatting`() {
        val paragraph = MarkdownDocumentParser.parse("这是一段 ~~删除的文字~~ 和正常文字。")
            .single() as MarkdownBlockModel.Paragraph
        assertTrue(paragraph.spans.any { it.text == "删除的文字" && it.strikethrough })
    }
}
