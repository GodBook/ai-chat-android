package com.example.aichat.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotTriggerTest {
    @Test
    fun mapsKnownStorageKeys() {
        assertEquals(ScreenshotTrigger.VOLUME_DOWN, normalizeScreenshotTrigger("volume_down"))
        assertEquals(ScreenshotTrigger.VOLUME_UP_DOWN, normalizeScreenshotTrigger("volume_up_down"))
    }

    @Test
    fun isCaseAndWhitespaceTolerant() {
        assertEquals(ScreenshotTrigger.VOLUME_UP_DOWN, normalizeScreenshotTrigger("  VOLUME_UP_DOWN "))
    }

    @Test
    fun fallsBackToVolumeDownForUnknownOrMissingValues() {
        assertEquals(DEFAULT_SCREENSHOT_TRIGGER, normalizeScreenshotTrigger(null))
        assertEquals(DEFAULT_SCREENSHOT_TRIGGER, normalizeScreenshotTrigger(""))
        assertEquals(DEFAULT_SCREENSHOT_TRIGGER, normalizeScreenshotTrigger("power+volume_down"))
    }

    @Test
    fun storageKeysAreStable() {
        assertEquals("volume_down", ScreenshotTrigger.VOLUME_DOWN.storageKey)
        assertEquals("volume_up_down", ScreenshotTrigger.VOLUME_UP_DOWN.storageKey)
    }
}
