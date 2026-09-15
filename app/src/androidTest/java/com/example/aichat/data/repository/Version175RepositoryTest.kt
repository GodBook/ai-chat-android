package com.example.aichat.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aichat.data.local.*
import com.example.aichat.data.model.*
import com.example.aichat.data.network.OpenAiCompatibleClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlinx.serialization.json.*

@RunWith(AndroidJUnit4::class)
class Version175RepositoryTest {
    private lateinit var db: ChatDatabase
    private lateinit var repo: DefaultChatRepository
    private lateinit var key: ApiKeyStore
    private lateinit var context: Context
    private val requests = mutableListOf<String>()
    private var responseCode = 200
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        key = ApiKeyStore(context, "test175")
        key.save("test-only-key")
        repo = newRepository()
    }
    private fun newRepository(): DefaultChatRepository {
        val images = ImageFileStore(context)
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val buffer = Buffer(); chain.request().body!!.writeTo(buffer); requests += buffer.readUtf8()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(responseCode).message("test")
                .body((if (responseCode == 200) "data: {\"choices\":[{\"delta\":{\"content\":\"完成\"}}]}\n\ndata: [DONE]\n\n" else "invalid test request")
                    .toResponseBody("text/event-stream".toMediaType())).build()
        }.build()
        return DefaultChatRepository(db, ConfigStore(context), key, images, OpenAiCompatibleClient(images, http))
    }
    @After fun cleanup() { db.close(); key.clear() }
    private suspend fun seed(): String {
        val chat = repo.createConversation("175测试")
        db.chatMessageDao().insertAll(listOf(
            ChatMessageEntity("u", "USER", "方案问题", status = "SENT", requestId = "r", createdAt = 1, conversationId = chat.id),
            ChatMessageEntity("a", "ASSISTANT", "旧方案A", status = "SENT", requestId = "r", createdAt = 2, conversationId = chat.id),
            ChatMessageEntity("b", "ASSISTANT", "新方案B", status = "SENT", requestId = "r", createdAt = 3, conversationId = chat.id)))
        return chat.id
    }
    @Test fun previewMatchesCapturedRequestAndSelectionSurvivesRepositoryRecreation() = runBlocking {
        val id = seed()
        repo.selectBranch(id, "r", "a")
        repo = newRepository()
        val card = KnowledgeCard("c", id, "175测试", "a", "r", "旧方案A", "约束", "离线编辑", createdAt = 1, updatedAt = 1)
        repo.saveKnowledgeCard(card)
        val options = ContextOptions(cards = listOf(card))
        val preview = repo.previewContext(id, "继续", emptyList(), options)
        val answer = repo.sendPlannedMessage(id, "继续", emptyList(), false, options)
        val actual = Json.parseToJsonElement(requests.single()).jsonObject["messages"]!!.jsonArray
        assertEquals(preview.messages.map { it.text }, actual.map { it.jsonObject["content"]!!.jsonPrimitive.content })
        assertTrue(requests.single().contains("旧方案A")); assertFalse(requests.single().contains("新方案B"))
        assertEquals("a", repo.getContextRecord(answer)!!.entries.single().assistantId)
        repo.saveKnowledgeCard(card.copy(body = "修改后", revision = 2))
        assertEquals("离线编辑", repo.getContextRecord(answer)!!.cards.single().body)
        repo.deleteKnowledgeCard(card.id)
        assertTrue(repo.getContextRecord(answer)!!.cards.isEmpty())
        assertTrue(repo.getAllMessages().any { it.role == MessageRole.USER && it.text.contains("离线编辑") })
    }
    @Test fun exclusionRetryAndRegenerationDoNotLeakLaterMessages() = runBlocking {
        val id = seed()
        responseCode = 400
        runCatching { repo.sendPlannedMessage(id, "目标问题", emptyList(), false, ContextOptions(setOf("u"))) }
        val failed = repo.observeMessages(id).first().last { it.status == MessageStatus.FAILED }
        db.chatMessageDao().insert(ChatMessageEntity("later", "USER", "绝不能进入旧请求的后续消息", status = "SENT", createdAt = System.currentTimeMillis() + 1000, conversationId = id))
        responseCode = 200
        repo.retryMessage(id, failed.id)
        repo.regenerateMessage(id, failed.id)
        requests.forEach { request -> assertFalse(request.contains("旧方案A")); assertFalse(request.contains("新方案B")); assertFalse(request.contains("绝不能进入")) }
        assertEquals(3, requests.size)
    }
    @Test fun cardSourcesSurviveMessageDeletionAndExplicitDetachSurvivesChatDeletion() = runBlocking {
        val id = seed()
        val card = KnowledgeCard("c", id, "175测试", "a", "r", "旧方案A", "结论", "旧方案A", createdAt = 1, updatedAt = 1)
        repo.saveKnowledgeCard(card)
        repo.deleteMessage("a")
        assertEquals("a", repo.observeKnowledgeCards().first().single().sourceMessageId)
        repo.detachConversationCards(id)
        repo.deleteConversation(id)
        assertNull(repo.observeKnowledgeCards().first().single().conversationId)
        assertEquals("旧方案A", repo.observeKnowledgeCards().first().single().sourceExcerpt)
    }
    @Test fun defaultChatDeletionDeletesCardsAndOverBudgetDoesNotPersistQuestion() = runBlocking {
        val id = seed()
        val before = repo.getAllMessages().size
        assertTrue(runCatching { repo.sendPlannedMessage(id, "x".repeat(48_001), emptyList(), false, ContextOptions()) }.isFailure)
        assertEquals(before, repo.getAllMessages().size)
        repo.saveKnowledgeCard(KnowledgeCard("c", id, title = "卡", body = "资料", createdAt = 1, updatedAt = 1))
        repo.deleteConversation(id)
        assertTrue(repo.observeKnowledgeCards().first().isEmpty())
    }
}
