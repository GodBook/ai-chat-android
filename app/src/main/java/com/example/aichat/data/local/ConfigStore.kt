package com.example.aichat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aichat.data.model.DEFAULT_BASE_URL
import com.example.aichat.data.model.DEFAULT_MODEL
import com.example.aichat.data.model.DEFAULT_OVERLAY_BACKGROUND_COLOR
import com.example.aichat.data.model.DEFAULT_OVERLAY_GLASS_ENABLED
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_PROMPT
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_TRIGGER
import com.example.aichat.data.model.ScreenshotTrigger
import com.example.aichat.data.model.normalizeOverlayBackgroundColor
import com.example.aichat.data.model.normalizeScreenshotTrigger
import com.example.aichat.data.model.canReplaceEndpointForPreset
import com.example.aichat.data.model.ProviderConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.providerConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "provider_config",
)

/** Stores provider settings that are safe to keep outside Android Keystore. */
class ConfigStore(context: Context) {
    private val dataStore = context.applicationContext.providerConfigDataStore

    val config: Flow<ProviderConfig> = dataStore.data.map { preferences ->
        ProviderConfig(
            baseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL,
            model = preferences[MODEL] ?: DEFAULT_MODEL,
            visionEnabled = preferences[VISION_ENABLED] ?: true,
            backgroundCaptureEnabled = preferences[BACKGROUND_CAPTURE_ENABLED] ?: false,
            screenshotPrompt = preferences[SCREENSHOT_PROMPT] ?: DEFAULT_SCREENSHOT_PROMPT,
            overlayBackgroundColor = normalizeOverlayBackgroundColor(
                preferences[OVERLAY_BACKGROUND_COLOR] ?: DEFAULT_OVERLAY_BACKGROUND_COLOR,
            ),
            overlayGlassEnabled = preferences[OVERLAY_GLASS_ENABLED] ?: DEFAULT_OVERLAY_GLASS_ENABLED,
            shortAnswerModeEnabled = preferences[SHORT_ANSWER_MODE_ENABLED] ?: false,
            autoFallbackEnabled = preferences[AUTO_FALLBACK_ENABLED] ?: true,
            screenshotTrigger = normalizeScreenshotTrigger(preferences[SCREENSHOT_TRIGGER]),
            autoCollapseThinking = preferences[AUTO_COLLAPSE_THINKING] ?: true,
        )
    }

    suspend fun read(): ProviderConfig = config.first()

    suspend fun update(
        baseUrl: String,
        model: String,
        visionEnabled: Boolean,
        backgroundCaptureEnabled: Boolean = false,
        screenshotPrompt: String = DEFAULT_SCREENSHOT_PROMPT,
        overlayBackgroundColor: String = DEFAULT_OVERLAY_BACKGROUND_COLOR,
        overlayGlassEnabled: Boolean = DEFAULT_OVERLAY_GLASS_ENABLED,
        shortAnswerModeEnabled: Boolean = false,
        autoFallbackEnabled: Boolean = true,
        screenshotTrigger: ScreenshotTrigger = DEFAULT_SCREENSHOT_TRIGGER,
        autoCollapseThinking: Boolean = true,
    ) {
        dataStore.edit { preferences ->
            preferences[BASE_URL] = baseUrl.trim()
            preferences[MODEL] = model.trim()
            preferences[VISION_ENABLED] = visionEnabled
            preferences[BACKGROUND_CAPTURE_ENABLED] = backgroundCaptureEnabled
            preferences[SCREENSHOT_PROMPT] = screenshotPrompt.trim()
            preferences[OVERLAY_BACKGROUND_COLOR] = normalizeOverlayBackgroundColor(overlayBackgroundColor)
            preferences[OVERLAY_GLASS_ENABLED] = overlayGlassEnabled
            preferences[SHORT_ANSWER_MODE_ENABLED] = shortAnswerModeEnabled
            preferences[AUTO_FALLBACK_ENABLED] = autoFallbackEnabled
            preferences[SCREENSHOT_TRIGGER] = screenshotTrigger.storageKey
            preferences[AUTO_COLLAPSE_THINKING] = autoCollapseThinking
        }
    }

    suspend fun update(config: ProviderConfig) = update(
        baseUrl = config.baseUrl,
        model = config.model,
        visionEnabled = config.visionEnabled,
        backgroundCaptureEnabled = config.backgroundCaptureEnabled,
        screenshotPrompt = config.screenshotPrompt,
        overlayBackgroundColor = config.overlayBackgroundColor,
        overlayGlassEnabled = config.overlayGlassEnabled,
        shortAnswerModeEnabled = config.shortAnswerModeEnabled,
        autoFallbackEnabled = config.autoFallbackEnabled,
        screenshotTrigger = config.screenshotTrigger,
        autoCollapseThinking = config.autoCollapseThinking,
    )

    /** Updates only overlay appearance so an immediate color choice cannot overwrite other settings. */
    suspend fun updateOverlayAppearance(
        backgroundColor: String,
        glassEnabled: Boolean,
    ) {
        dataStore.edit { preferences ->
            preferences[OVERLAY_BACKGROUND_COLOR] = normalizeOverlayBackgroundColor(backgroundColor)
            preferences[OVERLAY_GLASS_ENABLED] = glassEnabled
        }
    }

    /** Updates only the screenshot shortcut so an immediate choice cannot overwrite other settings. */
    suspend fun updateScreenshotTrigger(trigger: ScreenshotTrigger) {
        dataStore.edit { preferences -> preferences[SCREENSHOT_TRIGGER] = trigger.storageKey }
    }

    /** Updates only the auto fallback switch so toggling it does not require saving the whole form. */
    suspend fun updateAutoFallbackEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_FALLBACK_ENABLED] = enabled }
    }

    /** Updates the auto collapse thinking switch. */
    suspend fun updateAutoCollapseThinking(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[AUTO_COLLAPSE_THINKING] = enabled }
    }

    /** Updates model and optionally endpoint when picking a preset chip. */
    suspend fun updateModelPreset(model: String, baseUrl: String?) {
        dataStore.edit { preferences ->
            preferences[MODEL] = model.trim()
            val currentBaseUrl = preferences[BASE_URL] ?: DEFAULT_BASE_URL
            if (baseUrl != null && canReplaceEndpointForPreset(currentBaseUrl)) {
                preferences[BASE_URL] = baseUrl.trim()
            }
        }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val VISION_ENABLED = booleanPreferencesKey("vision_enabled")
        val BACKGROUND_CAPTURE_ENABLED = booleanPreferencesKey("background_capture_enabled")
        val SCREENSHOT_PROMPT = stringPreferencesKey("screenshot_prompt")
        val OVERLAY_BACKGROUND_COLOR = stringPreferencesKey("overlay_background_color")
        val OVERLAY_GLASS_ENABLED = booleanPreferencesKey("overlay_glass_enabled")
        val SHORT_ANSWER_MODE_ENABLED = booleanPreferencesKey("short_answer_mode_enabled")
        val AUTO_FALLBACK_ENABLED = booleanPreferencesKey("auto_fallback_enabled")
        val SCREENSHOT_TRIGGER = stringPreferencesKey("screenshot_trigger")
        val AUTO_COLLAPSE_THINKING = booleanPreferencesKey("auto_collapse_thinking")
    }
}
