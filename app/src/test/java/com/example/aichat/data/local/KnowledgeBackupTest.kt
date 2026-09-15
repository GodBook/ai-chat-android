package com.example.aichat.data.local

import com.example.aichat.data.model.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KnowledgeBackupTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun newBackupRemapsEveryRelationshipIncludingRequestAndSnapshotCard() = runBlocking {
        val card = KnowledgeCard("card", "chat", "原聊天", "answer", "request", "原文", "约束", "资料", createdAt = 1, updatedAt = 1)
        val record = ContextRecord(assistantMessageId = "answer", conversationId = "chat", userMessageId = "user", model = "test", systemPrompt = "角色",
            entries = emptyList(), cards = listOf(card), characterCount = 20, imageCount = 0)
        val out = ByteArrayOutputStream()
        BackupRestoreManager.exportBackupToStream(out, listOf(ChatConversation("chat", "原聊天", createdAt = 1, updatedAt = 1)), listOf(
            ChatMessage("user", MessageRole.USER, "问题", conversationId = "chat", requestId = "request"),
            ChatMessage("answer", MessageRole.ASSISTANT, "原文", conversationId = "chat", requestId = "request")), temp.newFolder(),
            knowledge = KnowledgeBackup(listOf(card), listOf(BranchSelection("chat", "request", "answer")), listOf(record)))
        var restored: KnowledgeBackup? = null
        BackupRestoreManager.importBackupFromStream(out.toByteArray().inputStream(), temp.newFolder(), temp.newFolder(), setOf("chat"),
            onInsertKnowledge = { conversations, messages, data ->
                restored = data
                val chat = conversations.single().id
                val answer = messages.single { it.role == "ASSISTANT" }
                assertNotEquals("chat", chat); assertNotEquals("request", answer.requestId)
                assertEquals(chat, data.cards.single().conversationId)
                assertEquals(answer.id, data.cards.single().sourceMessageId)
                assertEquals(answer.requestId, data.cards.single().sourceRequestId)
                assertEquals(answer.id, data.branches.single().assistantMessageId)
                assertEquals(answer.id, data.records.single().assistantMessageId)
                assertEquals(data.cards.single().id, data.records.single().cards.single().id)
            }, onInsertData = { _, _ -> fail("必须使用完整恢复事务") })
        assertNotNull(restored)
    }
    @Test fun oldBackupDefaultsToEmptyKnowledgeAndFutureFormatsAreRejected() = runBlocking {
        suspend fun read(version: Int, callback: (KnowledgeBackup) -> Unit) {
            val data = """{"manifest":{"appVersion":"old","versionCode":1,"schemaVersion":$version,"exportedAt":1,"conversationCount":0,"messageCount":0,"imageCount":0},"conversations":[],"messages":[]}"""
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("data.json")); it.write(data.toByteArray()); it.closeEntry() }
            BackupRestoreManager.importBackupFromStream(out.toByteArray().inputStream(), temp.newFolder(), temp.newFolder(), emptySet(),
                onInsertKnowledge = { _, _, k -> callback(k) }, onInsertData = { _, _ -> fail() })
        }
        read(8) { assertEquals(KnowledgeBackup(), it) }
        assertTrue(runCatching { read(99) { fail("不应写入未知格式") } }.isFailure)
    }
}
