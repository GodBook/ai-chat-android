package com.example.aichat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.aichat.data.model.DEFAULT_OVERLAY_BACKGROUND_COLOR
import com.example.aichat.data.model.DEFAULT_OVERLAY_GLASS_ENABLED
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_PROMPT
import com.example.aichat.data.model.normalizeOverlayBackgroundColor
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
    ) {
        dataStore.edit { preferences ->
            preferences[BASE_URL] = baseUrl.trim()
            preferences[MODEL] = model.trim()
            preferences[VISION_ENABLED] = visionEnabled
            preferences[BACKGROUND_CAPTURE_ENABLED] = backgroundCaptureEnabled
            preferences[SCREENSHOT_PROMPT] = screenshotPrompt.trim()
            preferences[OVERLAY_BACKGROUND_COLOR] = normalizeOverlayBackgroundColor(overlayBackgroundColor)
            preferences[OVERLAY_GLASS_ENABLED] = overlayGlassEnabled
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
    )

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val VISION_ENABLED = booleanPreferencesKey("vision_enabled")
        val BACKGROUND_CAPTURE_ENABLED = booleanPreferencesKey("background_capture_enabled")
        val SCREENSHOT_PROMPT = stringPreferencesKey("screenshot_prompt")
        val OVERLAY_BACKGROUND_COLOR = stringPreferencesKey("overlay_background_color")
        val OVERLAY_GLASS_ENABLED = booleanPreferencesKey("overlay_glass_enabled")
    }
}
