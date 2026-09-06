package com.example.aichat.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    fun observeAll(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    /** Emits only the newest row for each conversation, instead of the full history. */
    @Query(
        "SELECT message.* FROM chat_messages AS message " +
            "WHERE NOT EXISTS (" +
            "SELECT 1 FROM chat_messages AS newer " +
            "WHERE newer.conversationId = message.conversationId " +
            "AND (newer.createdAt > message.createdAt " +
            "OR (newer.createdAt = message.createdAt AND newer.id > message.id))" +
            ") " +
            "ORDER BY message.createdAt ASC, message.id ASC",
    )
    fun observeLatestPerConversation(): Flow<List<ChatMessageEntity>>

    @Query(
        "SELECT EXISTS(" +
            "SELECT 1 FROM chat_messages " +
            "WHERE status IN ('SENDING', 'STREAMING')" +
            ")",
    )
    fun observeAnyGenerating(): Flow<Boolean>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getForConversation(conversationId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages WHERE requestId = :requestId ORDER BY createdAt ASC")
    suspend fun getByRequestId(requestId: String): List<ChatMessageEntity>

    @Query(
        "SELECT * FROM chat_messages " +
            "WHERE conversationId = :conversationId AND requestId = :requestId " +
            "ORDER BY createdAt ASC, id ASC",
    )
    suspend fun getByRequestId(conversationId: String, requestId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Delete
    suspend fun delete(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: String): Int
}
