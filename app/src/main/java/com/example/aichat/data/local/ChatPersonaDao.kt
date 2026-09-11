package com.example.aichat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatPersonaDao {
    @Query("SELECT * FROM chat_personas ORDER BY isBuiltIn DESC, createdAt ASC")
    fun getAllPersonas(): Flow<List<ChatPersonaEntity>>

    @Query("SELECT * FROM chat_personas WHERE id = :id LIMIT 1")
    suspend fun getPersonaById(id: String): ChatPersonaEntity?

    @Query("SELECT * FROM chat_personas WHERE category = :category ORDER BY isBuiltIn DESC, createdAt ASC")
    fun getPersonasByCategory(category: String): Flow<List<ChatPersonaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(persona: ChatPersonaEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(personas: List<ChatPersonaEntity>)

    @Update
    suspend fun update(persona: ChatPersonaEntity)

    @Query("DELETE FROM chat_personas WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomPersona(id: String)
}
