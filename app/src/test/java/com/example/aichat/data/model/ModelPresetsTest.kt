package com.example.aichat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPresetsTest {
    @Test
    fun `presets include the default and fallback models`() {
        val models = MODEL_PRESETS.map { it.model }
        assertTrue(DEFAULT_MODEL in models)
        assertTrue(FALLBACK_MODEL in models)
        assertEquals(models.size, models.distinct().size)
    }

    @Test
    fun `stock endpoints follow the preset`() {
        assertTrue(canReplaceEndpointForPreset(""))
        assertTrue(canReplaceEndpointForPreset(DEFAULT_BASE_URL))
        assertTrue(canReplaceEndpointForPreset("$DEFAULT_BASE_URL/"))
        assertTrue(canReplaceEndpointForPreset("https://api.openai.com/v1"))
    }

    @Test
    fun `a self hosted gateway is never replaced`() {
        assertFalse(canReplaceEndpointForPreset("https://my-relay.example.com/v1"))
        assertFalse(canReplaceEndpointForPreset("https://my-relay.example.com/v1/"))
    }
}
