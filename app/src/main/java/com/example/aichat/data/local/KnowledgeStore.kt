package com.example.aichat.data.local

import androidx.room.withTransaction
import com.example.aichat.data.model.*
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KnowledgeStore(private val database: ChatDatabase) {
    private val dao = database.knowledgeDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val cards = dao.observeCards().map { rows -> rows.map { json.decodeFromString<KnowledgeCard>(it.payload) }.sortedByDescending { it.updatedAt } }
    val branches = dao.observeBranches().map { rows -> rows.map { BranchSelection(it.conversationId, it.requestId, it.assistantMessageId) } }
    suspend fun selections(conversationId: String): Map<String, String> = dao.branches().filter { it.conversationId == conversationId }.associate { it.requestId to it.assistantMessageId }
    suspend fun select(conversationId: String, requestId: String, assistantId: String) {
        val message = database.chatMessageDao().getById(assistantId)
        require(message?.conversationId == conversationId && message.requestId == requestId && message.role == "ASSISTANT") { "回答版本不存在" }
        dao.selectBranch(BranchSelectionEntity(conversationId, requestId, assistantId))
    }
    suspend fun save(card: KnowledgeCard) {
        card.validate()
        require(card.conversationId == null || database.chatConversationDao().getById(card.conversationId) != null) { "来源聊天已删除" }
        val source = card.sourceMessageId?.let { database.chatMessageDao().getById(it) }
        require(source == null || source.role == "ASSISTANT" && (card.conversationId == null || source.conversationId == card.conversationId)) { "结论卡来源关联无效" }
        dao.saveCard(KnowledgeCardEntity(card.id, card.conversationId, json.encodeToString(card)))
    }
    suspend fun record(id: String): ContextRecord? = dao.record(id)?.let { json.decodeFromString<ContextRecord>(it.payload) }
    suspend fun record(value: ContextRecord) {
        require(value.formatVersion == 1 && value.systemPrompt.length <= ContextPlanner.MAX_CHARS && (value.searchContext?.length ?: 0) <= ContextPlanner.MAX_CHARS)
        dao.saveRecord(ContextRecordEntity(value.assistantMessageId, value.conversationId, json.encodeToString(value)))
    }
    suspend fun deleteCard(id: String) = database.withTransaction {
        dao.deleteCard(id)
        dao.records().forEach { row ->
            val value = json.decodeFromString<ContextRecord>(row.payload)
            if (value.cards.any { it.id == id }) record(value.copy(cards = value.cards.filterNot { it.id == id }))
        }
    }
    suspend fun deleteMessage(id: String) { dao.deleteBranch(id); dao.deleteRecord(id) }
    suspend fun detachCards(id: String) {
        dao.cards().filter { it.conversationId == id }.forEach { row ->
            val card = json.decodeFromString<KnowledgeCard>(row.payload)
            save(card.copy(conversationId = null))
        }
    }
    suspend fun clearConversation(id: String, deleteCards: Boolean) {
        if (deleteCards) dao.cards().filter { it.conversationId == id }.forEach { deleteCard(it.id) }
        dao.deleteConversationRecords(id); dao.deleteConversationBranches(id)
    }
    suspend fun backup(): KnowledgeBackup = KnowledgeBackup(
        dao.cards().map { json.decodeFromString<KnowledgeCard>(it.payload) },
        dao.branches().map { BranchSelection(it.conversationId, it.requestId, it.assistantMessageId) },
        dao.records().map { json.decodeFromString<ContextRecord>(it.payload) },
    )
    suspend fun restore(data: KnowledgeBackup) {
        data.cards.forEach { save(it) }
        data.branches.forEach { select(it.conversationId, it.requestId, it.assistantMessageId) }
        data.records.forEach { record(it) }
    }
}
