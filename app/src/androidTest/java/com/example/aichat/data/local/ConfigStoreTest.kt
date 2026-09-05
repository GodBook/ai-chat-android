package com.example.aichat.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_PROMPT
import com.example.aichat.data.model.DEFAULT_OVERLAY_BACKGROUND_COLOR
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigStoreTest {
    private lateinit var store: ConfigStore

    @Before
    fun setUp() = runBlocking {
        store = ConfigStore(ApplicationProvider.getApplicationContext<Context>())
        store.reset()
    }

    @After
    fun tearDown() = runBlocking {
        store.reset()
    }

    @Test
    fun backgroundCaptureIsDisabledByDefault() = runBlocking {
        val config = store.read()
        assertFalse(config.backgroundCaptureEnabled)
        assertEquals(DEFAULT_SCREENSHOT_PROMPT, config.screenshotPrompt)
        assertEquals(DEFAULT_OVERLAY_BACKGROUND_COLOR, config.overlayBackgroundColor)
        assertFalse(config.overlayGlassEnabled)
        assertFalse(config.shortAnswerModeEnabled)
    }

    @Test
    fun backgroundCaptureSettingSurvivesRoundTrip() = runBlocking {
        store.update(
            baseUrl = "https://api.example.test/v1",
            model = "vision-model",
            visionEnabled = true,
            backgroundCaptureEnabled = true,
            screenshotPrompt = "只回复答案",
            overlayBackgroundColor = "#DDF4E8",
            overlayGlassEnabled = true,
            shortAnswerModeEnabled = true,
        )

        val config = store.read()
        assertTrue(config.backgroundCaptureEnabled)
        assertEquals("只回复答案", config.screenshotPrompt)
        assertEquals("#DDF4E8", config.overlayBackgroundColor)
        assertTrue(config.overlayGlassEnabled)
        assertTrue(config.shortAnswerModeEnabled)
    }

    @Test
    fun invalidOverlayColorFallsBackToDefault() = runBlocking {
        store.update(
            baseUrl = "https://api.example.test/v1",
            model = "vision-model",
            visionEnabled = true,
            overlayBackgroundColor = "not-a-color",
        )

        assertEquals(DEFAULT_OVERLAY_BACKGROUND_COLOR, store.read().overlayBackgroundColor)
    }

    @Test
    fun overlayAppearanceUpdatesImmediatelyWithoutChangingOtherSettings() = runBlocking {
        store.update(
            baseUrl = "https://api.example.test/v1",
            model = "vision-model",
            visionEnabled = false,
            backgroundCaptureEnabled = false,
            screenshotPrompt = "保留这条提示词",
        )

        store.updateOverlayAppearance(
            backgroundColor = "#FFE4D1",
            glassEnabled = true,
        )

        val config = store.read()
        assertEquals("#FFE4D1", config.overlayBackgroundColor)
        assertTrue(config.overlayGlassEnabled)
        assertEquals("https://api.example.test/v1", config.baseUrl)
        assertEquals("vision-model", config.model)
        assertFalse(config.visionEnabled)
        assertEquals("保留这条提示词", config.screenshotPrompt)
    }
}
