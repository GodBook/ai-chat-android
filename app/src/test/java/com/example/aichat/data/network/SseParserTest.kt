package com.example.aichat.data.network

import org.junit.Assert.assertEquals
import org.junit.Test

class SseParserTest {
    @Test
    fun `one event is emitted only at the blank line`() {
        val parser = SseParser()

        assertEquals(emptyList<String>(), parser.accept("data: {\"choices\":[]}"))
        assertEquals(listOf("{\"choices\":[]}"), parser.accept(""))
    }

    @Test
    fun `multiple data lines are joined with a newline`() {
        val parser = SseParser()

        parser.accept("data: first")
        parser.accept("data: second")

        assertEquals(listOf("first\nsecond"), parser.finish())
    }

    @Test
    fun `comments and bom are ignored as framing`() {
        val parser = SseParser()

        assertEquals(emptyList<String>(), parser.accept(": keep-alive"))
        parser.accept("\uFEFFdata: [DONE]")

        assertEquals(listOf("[DONE]"), parser.finish())
    }
}
