package com.example.aichat.background

import com.example.aichat.background.ScreenshotKeyHandler.Key
import com.example.aichat.data.model.ScreenshotTrigger
import org.junit.Assert.*
import org.junit.Test

class ScreenshotKeyHandlerTest {
    private val handler = ScreenshotKeyHandler()

    private fun key(
        key: Key,
        down: Boolean = true,
        repeat: Boolean = false,
        now: Long = 10_000L,
        enabled: Boolean = true,
        pair: Boolean = false,
        speaking: Boolean = false,
    ) = handler.handle(key, down, repeat, now, enabled,
        if (pair) ScreenshotTrigger.VOLUME_UP_DOWN else ScreenshotTrigger.VOLUME_DOWN, speaking)

    @Test fun `single screenshot consumes down repeats and up without repeated captures`() {
        assertEquals(ScreenshotKeyHandler.Result(consume = true, capture = true), key(Key.DOWN))
        assertEquals(ScreenshotKeyHandler.Result(consume = true), key(Key.DOWN, repeat = true))
        assertEquals(ScreenshotKeyHandler.Result(consume = true), key(Key.DOWN, down = false))
    }

    @Test fun `release remains consumed if screenshot setting changes while held`() {
        key(Key.DOWN)
        assertTrue(key(Key.DOWN, down = false, enabled = false).consume)
        assertFalse(key(Key.DOWN, enabled = false).consume)
    }

    @Test fun `disabled screenshot preserves ordinary volume keys`() {
        assertEquals(ScreenshotKeyHandler.Result(), key(Key.DOWN, enabled = false))
        assertEquals(ScreenshotKeyHandler.Result(), key(Key.UP, enabled = false))
        assertEquals(ScreenshotKeyHandler.Result(), key(Key.DOWN, down = false, enabled = false))
    }

    @Test fun `up stops speech and consumes the whole press`() {
        assertEquals(ScreenshotKeyHandler.Result(consume = true, stopSpeech = true), key(Key.UP, speaking = true))
        assertTrue(key(Key.UP, repeat = true).consume)
        assertTrue(key(Key.UP, down = false).consume)
        assertFalse(key(Key.UP).consume)
    }

    @Test fun `pair works with up first even when up stops speech`() {
        assertTrue(key(Key.UP, pair = true, speaking = true).stopSpeech)
        assertTrue(key(Key.DOWN, pair = true, now = 10_100).capture)
        assertFalse(key(Key.DOWN, pair = true, repeat = true, now = 10_200).capture)
    }

    @Test fun `pair works with down first even while speech is active`() {
        assertFalse(key(Key.DOWN, pair = true, speaking = true).capture)
        val result = key(Key.UP, pair = true, speaking = true, now = 10_100)
        assertTrue(result.capture)
        assertTrue(result.stopSpeech)
        assertTrue(result.consume)
    }

    @Test fun `released or stale keys cannot form a pair`() {
        key(Key.DOWN, pair = true)
        key(Key.DOWN, pair = true, down = false)
        assertFalse(key(Key.UP, pair = true, now = 10_100).capture)
        assertFalse(key(Key.DOWN, pair = true, now = 13_000).capture)
    }

    @Test fun `changing shortcut does not reuse a held key`() {
        key(Key.DOWN)
        assertFalse(key(Key.UP, pair = true, now = 10_100).capture)
    }
}
