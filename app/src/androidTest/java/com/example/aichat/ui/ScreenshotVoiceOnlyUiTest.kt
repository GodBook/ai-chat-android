package com.example.aichat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.aichat.ui.tts.TtsFloatingPlayer
import com.example.aichat.ui.tts.TtsPlaybackState
import org.junit.Rule
import org.junit.Test

class ScreenshotVoiceOnlyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun voiceOnlyRemovesActiveAndPausedPlaybackText() {
        val playback = mutableStateOf(TtsPlaybackState(
            isPlaying = true, currentMessageId = "screenshot_test",
            currentSentenceText = "不应显示的截图答案", totalSentences = 1,
        ))
        compose.setContent {
            MaterialTheme {
                TtsFloatingPlayer(playback.value, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText("不应显示的截图答案").assertIsDisplayed()
        compose.runOnIdle { playback.value = playback.value.copy(voiceOnly = true) }
        compose.onNodeWithText("不应显示的截图答案").assertDoesNotExist()
        compose.runOnIdle { playback.value = playback.value.copy(isPlaying = false, isPaused = true) }
        compose.onNodeWithText("不应显示的截图答案").assertDoesNotExist()
        compose.runOnIdle { playback.value = playback.value.copy(voiceOnly = false) }
        compose.onNodeWithText("不应显示的截图答案").assertIsDisplayed()
    }
}
