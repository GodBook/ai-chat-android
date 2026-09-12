package com.example.aichat.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.aichat.data.model.literalSearchPattern
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MessageSearchTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    @Test fun paginatesBeyond100WithoutDuplicatesAtEqualTimestampsAndNewInsertions() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        try {
            db.chatConversationDao().insert(ChatConversationEntity("a", "聊天", 1, 1))
            db.chatMessageDao().insertAll((0..124).map { ChatMessageEntity("id%03d".format(it), "USER", "中文关键词 $it", status = "SENT", createdAt = 1000, conversationId = "a") })
            val dao = db.chatMessageDao()
            val first = dao.searchMessagePage(literalSearchPattern("中文关键词"), null, null, 0, null, null, 50)
            assertEquals(50, first.size)
            dao.insert(ChatMessageEntity("new", "USER", "中文关键词 新消息", status = "SENT", createdAt = 2000, conversationId = "a"))
            val second = dao.searchMessagePage(literalSearchPattern("中文关键词"), null, null, 0, first.last().createdAt, first.last().id, 50)
            val third = dao.searchMessagePage(literalSearchPattern("中文关键词"), null, null, 0, second.last().createdAt, second.last().id, 50)
            assertEquals(25, third.size)
            assertEquals(125, (first + second + third).map { it.id }.toSet().size)
        } finally { db.close() }
    }
    @Test fun combinesConversationRoleTimeAndLiteralWildcardFilters() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        try {
            db.chatConversationDao().insert(ChatConversationEntity("a", "甲", 1, 1))
            db.chatConversationDao().insert(ChatConversationEntity("b", "乙", 1, 1))
            val dao = db.chatMessageDao()
            listOf(
                ChatMessageEntity("match", "ASSISTANT", "100%_\\资料", status = "SENT", createdAt = 200, conversationId = "a"),
                ChatMessageEntity("user", "USER", "100%_\\资料", status = "SENT", createdAt = 200, conversationId = "a"),
                ChatMessageEntity("other", "ASSISTANT", "100%_\\资料", status = "SENT", createdAt = 200, conversationId = "b"),
                ChatMessageEntity("old", "ASSISTANT", "100%_\\资料", status = "SENT", createdAt = 100, conversationId = "a"),
                ChatMessageEntity("wildcard", "ASSISTANT", "100普通资料", status = "SENT", createdAt = 200, conversationId = "a"),
            ).forEach { dao.insert(it) }
            val results = dao.searchMessagePage(literalSearchPattern("100%_\\"), "a", "ASSISTANT", 150, null, null, 51)
            assertEquals(listOf("match"), results.map { it.id })
            assertTrue(dao.searchMessagePage(literalSearchPattern("missing"), null, null, 0, null, null, 51).isEmpty())
        } finally { db.close() }
    }
}
