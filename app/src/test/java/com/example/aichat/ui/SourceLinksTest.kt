package com.example.aichat.ui

import com.example.aichat.data.network.WebSearchResult
import org.junit.Assert.*
import org.junit.Test

class SourceLinksTest {
    private val sources = listOf(WebSearchResult("A", "https://example.org/a(b)", ""))
    @Test fun `valid numbered citations become clickable while nonexistent citations remain text`() {
        assertEquals("See [来源 1](https://example.org/a%28b%29) [2] [0]", linkSearchCitations("See [来源 1] [2] [0]", sources))
        assertEquals("[1](https://example.org/a%28b%29)", linkSearchCitations("[1]", sources))
    }
    @Test fun `existing links and code are unchanged`() {
        val text = "[1](https://example.org/existing) `[1]`\n```\n[1]\n```\n~~~\n[来源 1]\n~~~"
        assertEquals(text, linkSearchCitations(text, sources))
    }
    @Test fun `unsafe sources cannot become links`() {
        assertEquals("[1]", linkSearchCitations("[1]", listOf(WebSearchResult("X", "javascript:alert(1)", ""))))
    }
}
