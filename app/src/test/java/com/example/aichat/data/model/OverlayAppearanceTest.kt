package com.example.aichat.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayAppearanceTest {
    @Test
    fun normalizesValidHexColor() {
        assertEquals("#DDF4E8", normalizeOverlayBackgroundColor("  #ddf4e8 "))
    }

    @Test
    fun rejectsInvalidHexColor() {
        assertEquals(DEFAULT_OVERLAY_BACKGROUND_COLOR, normalizeOverlayBackgroundColor("#1234"))
        assertEquals(DEFAULT_OVERLAY_BACKGROUND_COLOR, normalizeOverlayBackgroundColor("transparent"))
    }
}
