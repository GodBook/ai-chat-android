package com.example.aichat.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_PROMPT
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
    }

    @Test
    fun backgroundCaptureSettingSurvivesRoundTrip() = runBlocking {
        store.update(
            baseUrl = "https://api.example.test/v1",
            model = "vision-model",
            visionEnabled = true,
            backgroundCaptureEnabled = true,
            screenshotPrompt = "只回复答案",
        )

        val config = store.read()
        assertTrue(config.backgroundCaptureEnabled)
        assertEquals("只回复答案", config.screenshotPrompt)
    }
}
