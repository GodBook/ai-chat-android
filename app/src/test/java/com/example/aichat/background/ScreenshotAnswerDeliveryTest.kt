package com.example.aichat.background

import com.example.aichat.data.model.ProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotAnswerDeliveryTest {
    private fun deliver(config: ProviderConfig, answer: String = "答案是 A。[简答:A]"): List<String> {
        val events = mutableListOf<String>()
        deliverScreenshotAnswer(answer, config,
            dismiss = { events += "dismiss" },
            showText = { events += "text" },
            showIndicator = { events += "indicator" },
            speak = { text, hidden -> events += "speech:$hidden:$text" },
        )
        return events
    }

    @Test fun voiceOnlyNeverShowsAnswersEvenWhenOtherModesAreEnabled() {
        for (compact in listOf(false, true)) for (assistant in listOf(false, true)) {
            assertEquals(listOf("dismiss", "speech:true:答案是 A。"), deliver(ProviderConfig(
                backgroundCaptureEnabled = true, screenshotVoiceOnlyEnabled = true,
                shortAnswerModeEnabled = compact, screenshotAssistantEnabled = assistant,
            )))
        }
    }

    @Test fun disabledCaptureDiscardsAnAnswerThatArrivesLate() {
        assertEquals(emptyList<String>(), deliver(ProviderConfig(
            screenshotVoiceOnlyEnabled = true, screenshotAssistantEnabled = true,
        )))
    }

    @Test fun unrecognizedCompactAnswerDoesNotFallBackToText() {
        assertEquals(emptyList<String>(), deliver(ProviderConfig(
            backgroundCaptureEnabled = true, shortAnswerModeEnabled = true,
        ), "无法识别题目"))
    }

    @Test fun ordinaryAndCompactModesRemainAvailableWhenVoiceOnlyIsOff() {
        assertEquals(listOf("text"), deliver(ProviderConfig(backgroundCaptureEnabled = true)))
        assertEquals(listOf("indicator", "speech:false:答案是 A。"), deliver(ProviderConfig(
            backgroundCaptureEnabled = true, shortAnswerModeEnabled = true, screenshotAssistantEnabled = true,
        )))
    }
}
