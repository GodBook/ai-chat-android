package com.example.aichat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderProfileDao {
    @Query("SELECT * FROM provider_profiles ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllProfiles(): Flow<List<ProviderProfileEntity>>

    @Query("SELECT * FROM provider_profiles WHERE id = :id LIMIT 1")
    suspend fun getProfileById(id: String): ProviderProfileEntity?

    @Query("SELECT * FROM provider_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): ProviderProfileEntity?

    @Query("SELECT * FROM provider_profiles WHERE isDefault = 1 LIMIT 1")
    fun observeDefaultProfile(): Flow<ProviderProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(profile: ProviderProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(profiles: List<ProviderProfileEntity>)

    @Update
    suspend fun update(profile: ProviderProfileEntity)

    @Query("DELETE FROM provider_profiles WHERE id = :id AND isDefault = 0")
    suspend fun deleteProfile(id: String)

    @Query("UPDATE provider_profiles SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("UPDATE provider_profiles SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: String)

    @Transaction
    suspend fun switchActiveProfile(id: String) {
        clearDefaultFlags()
        setAsDefault(id)
    }
}
