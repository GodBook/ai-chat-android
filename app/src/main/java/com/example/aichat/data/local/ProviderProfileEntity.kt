package com.example.aichat.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.aichat.data.model.ProviderProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

@Entity(tableName = "provider_profiles")
data class ProviderProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val candidateModels: String = "[]",
    val visionEnabled: Boolean = true,
    val customHeaders: String = "{}",
    val isDefault: Boolean = false,
    val presetType: String = "custom",
    val sortOrder: Int = 0,
    val createdAt: Long,
)

