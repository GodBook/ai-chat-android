package com.example.aichat.background

import com.example.aichat.data.model.ProviderConfig

/** Shared output routing for root, accessibility and MediaProjection captures. */
internal fun deliverScreenshotAnswer(
    answer: String,
    config: ProviderConfig,
    dismiss: () -> Unit,
    showText: (String) -> Unit,
    showIndicator: (ShortAnswerIndicator) -> Unit,
    speak: (String, Boolean) -> Unit,
) {
    if (!config.backgroundCaptureEnabled) return
    when {
        config.screenshotVoiceOnlyEnabled -> dismiss()
        config.shortAnswerModeEnabled -> extractShortAnswerIndicator(answer)?.let(showIndicator)
        else -> showText(answer)
    }
    if ((config.screenshotVoiceOnlyEnabled || config.screenshotAssistantEnabled) && answer.isNotBlank()) {
        // Do not read machine-only tags if the user switched modes while the request was running.
        val spokenAnswer = answer.replace(Regex("\\[简答[:：][^\\]]*]"), "").trim()
            .ifEmpty { answer }
        speak(spokenAnswer, config.screenshotVoiceOnlyEnabled)
    }
}
