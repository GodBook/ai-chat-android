package com.example.aichat.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "knowledge_cards", indices = [Index("conversationId")])
data class KnowledgeCardEntity(@PrimaryKey val id: String, val conversationId: String?, val payload: String)

@Entity(tableName = "conversation_branch_selections", primaryKeys = ["conversationId", "requestId"])
data class BranchSelectionEntity(val conversationId: String, val requestId: String, val assistantMessageId: String)

@Entity(tableName = "request_context_records", indices = [Index("conversationId")])
data class ContextRecordEntity(@PrimaryKey val assistantMessageId: String, val conversationId: String, val payload: String)

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_cards") fun observeCards(): Flow<List<KnowledgeCardEntity>>
    @Query("SELECT * FROM knowledge_cards") suspend fun cards(): List<KnowledgeCardEntity>
    @Query("SELECT * FROM knowledge_cards WHERE id = :id") suspend fun card(id: String): KnowledgeCardEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCard(card: KnowledgeCardEntity)
    @Query("DELETE FROM knowledge_cards WHERE id = :id") suspend fun deleteCard(id: String)
    @Query("SELECT * FROM conversation_branch_selections") fun observeBranches(): Flow<List<BranchSelectionEntity>>
    @Query("SELECT * FROM conversation_branch_selections") suspend fun branches(): List<BranchSelectionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun selectBranch(branch: BranchSelectionEntity)
    @Query("DELETE FROM conversation_branch_selections WHERE assistantMessageId = :id") suspend fun deleteBranch(id: String)
    @Query("SELECT * FROM request_context_records") suspend fun records(): List<ContextRecordEntity>
    @Query("SELECT * FROM request_context_records WHERE assistantMessageId = :id") suspend fun record(id: String): ContextRecordEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRecord(record: ContextRecordEntity)
    @Query("DELETE FROM request_context_records WHERE assistantMessageId = :id") suspend fun deleteRecord(id: String)
    @Query("DELETE FROM request_context_records WHERE conversationId = :id") suspend fun deleteConversationRecords(id: String)
    @Query("DELETE FROM conversation_branch_selections WHERE conversationId = :id") suspend fun deleteConversationBranches(id: String)
}
