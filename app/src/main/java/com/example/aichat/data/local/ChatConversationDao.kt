package com.example.aichat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatConversationDao {
    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC, createdAt DESC, id ASC")
    fun observeAll(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM chat_conversations ORDER BY updatedAt DESC, createdAt DESC, id ASC")
    suspend fun getAll(): List<ChatConversationEntity>

    @Query("SELECT * FROM chat_conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChatConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: ChatConversationEntity)

    @Update
    suspend fun update(conversation: ChatConversationEntity)

    @Query(
        "UPDATE chat_conversations " +
            "SET title = :title, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun rename(id: String, title: String, updatedAt: Long): Int

    @Query("UPDATE chat_conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long): Int

    @Query("DELETE FROM chat_conversations WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT COUNT(*) FROM chat_conversations")
    suspend fun count(): Int
}
