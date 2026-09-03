package com.example.aichat.data.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appUpdateDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "app_update_config",
)

/**
 * Stores the HTTPS update-manifest URL independently from provider settings.
 *
 * Keeping this in its own DataStore means an update never touches API keys,
 * chat history, or the model configuration. An empty value means that online
 * update checking has not been configured yet.
 */
class UpdateConfigStore(context: Context) {
    private val dataStore = context.applicationContext.appUpdateDataStore

    val manifestUrl: Flow<String> = dataStore.data.map { preferences ->
        preferences[MANIFEST_URL].orEmpty()
    }

    suspend fun readManifestUrl(): String = manifestUrl.first()

    suspend fun setManifestUrl(value: String) {
        val normalized = value.trim()
        if (normalized.isNotEmpty()) UpdateManifestParser.validateHttpsUrl(normalized)
        dataStore.edit { preferences ->
            if (normalized.isEmpty()) {
                preferences.remove(MANIFEST_URL)
            } else {
                preferences[MANIFEST_URL] = normalized
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(MANIFEST_URL) }
    }

    private companion object {
        val MANIFEST_URL = stringPreferencesKey("manifest_url")
    }
}
