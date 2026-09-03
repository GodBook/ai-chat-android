package com.example.aichat.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aichat.data.local.ApiKeyStore
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ChatMessageEntity
import com.example.aichat.data.local.ConfigStore
import com.example.aichat.data.local.ImageFileStore
import com.example.aichat.data.model.DEFAULT_CONVERSATION_ID
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import com.example.aichat.data.network.OpenAiCompatibleClient
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DefaultChatRepositoryConversationTest {
    private lateinit var context: Context
    private lateinit var database: ChatDatabase
    private lateinit var repository: DefaultChatRepository
    private lateinit var imageFileStore: ImageFileStore
    private val testImages = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ChatDatabase::class.java).build()
        imageFileStore = ImageFileStore(context)
        repository = DefaultChatRepository(
            database = database,
            configStore = ConfigStore(context),
            apiKeyStore = ApiKeyStore(context),
            imageFileStore = imageFileStore,
            client = OpenAiCompatibleClient(imageFileStore),
        )
    }

    @After
    fun tearDown() {
        database.close()
        testImages.forEach { it.delete() }
    }

    @Test
    fun renameAndDeleteAffectOnlyTheTargetConversation() = runBlocking {
        val first = repository.createConversation("第一段聊天")
        val second = repository.createConversation("第二段聊天")
        val firstImage = createTestImage()
        val secondImage = createTestImage()
        database.chatMessageDao().insert(message("first-message", first.id, firstImage))
        database.chatMessageDao().insert(message("second-message", second.id, secondImage))
        database.chatMessageDao().insert(message("shared-message", second.id, firstImage))

        val renamed = repository.renameConversation(first.id, "  已重命名  ")
        assertEquals("已重命名", renamed?.title)
        assertEquals("第二段聊天", repository.getConversation(second.id)?.title)

        assertTrue(repository.deleteConversation(first.id))
        assertNull(repository.getConversation(first.id))
        assertNotNull(repository.getConversation(second.id))
        assertNull(database.chatMessageDao().getById("first-message"))
        assertNotNull(database.chatMessageDao().getById("second-message"))
        assertTrue(firstImage.exists())
        assertTrue(secondImage.exists())

        assertTrue(repository.deleteConversation(second.id))
        assertFalse(firstImage.exists())
        assertFalse(secondImage.exists())
        assertEquals(
            listOf(DEFAULT_CONVERSATION_ID),
            database.chatConversationDao().getAll().map { it.id },
        )
    }

    @Test
    fun clearKeepsTheConversationAndOtherChatsIntact() = runBlocking {
        val first = repository.createConversation("保留空会话")
        val second = repository.createConversation("不受影响")
        val firstImage = createTestImage()
        val secondImage = createTestImage()
        database.chatMessageDao().insert(message("first-message", first.id, firstImage))
        database.chatMessageDao().insert(message("second-message", second.id, secondImage))

        repository.clearConversation(first.id)

        assertNotNull(repository.getConversation(first.id))
        assertNotNull(repository.getConversation(second.id))
        assertEquals(emptyList<ChatMessageEntity>(), database.chatMessageDao().getForConversation(first.id))
        assertEquals(1, database.chatMessageDao().getForConversation(second.id).size)
        assertFalse(firstImage.exists())
        assertTrue(secondImage.exists())
    }

    private fun createTestImage(): File {
        val directory = File(context.filesDir, "chat-images").apply { mkdirs() }
        return File(directory, "repository-test-${UUID.randomUUID()}.jpg")
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
            .also { testImages += it }
    }

    private fun message(id: String, conversationId: String, image: File) = ChatMessageEntity(
        id = id,
        conversationId = conversationId,
        role = MessageRole.USER.name,
        text = "图片消息",
        imagePaths = "[\"${image.absolutePath}\"]",
        status = MessageStatus.SENT.name,
        requestId = "request-$id",
        createdAt = System.currentTimeMillis(),
    )
}
