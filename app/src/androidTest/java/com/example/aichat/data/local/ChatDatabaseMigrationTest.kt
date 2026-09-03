package com.example.aichat.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aichat.data.model.DEFAULT_CONVERSATION_ID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatDatabaseMigrationTest {
    private lateinit var context: Context
    private var roomDatabase: ChatDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        roomDatabase?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationFromVersion1KeepsLegacyMessages() = runBlocking {
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { legacy ->
            legacy.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `imagePaths` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `requestId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAt` ON `chat_messages` (`createdAt`)",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_messages_requestId` ON `chat_messages` (`requestId`)",
            )
            legacy.execSQL(
                """
                INSERT INTO `chat_messages`
                    (`id`, `role`, `text`, `imagePaths`, `status`, `requestId`, `createdAt`, `errorMessage`)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "legacy-message",
                    "USER",
                    "旧版聊天内容",
                    "[\"/private/legacy.jpg\"]",
                    "SENT",
                    "legacy-request",
                    1234L,
                    null,
                ),
            )
            legacy.version = 1
        }

        val migrated = Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME)
            .addMigrations(ChatDatabase.MIGRATION_1_2)
            .build()
        roomDatabase = migrated

        val messages = migrated.chatMessageDao().getForConversation(DEFAULT_CONVERSATION_ID)
        val conversation = migrated.chatConversationDao().getById(DEFAULT_CONVERSATION_ID)

        assertEquals(1, messages.size)
        assertEquals("legacy-message", messages.single().id)
        assertEquals("旧版聊天内容", messages.single().text)
        assertEquals("[\"/private/legacy.jpg\"]", messages.single().imagePaths)
        assertEquals("legacy-request", messages.single().requestId)
        assertEquals(DEFAULT_CONVERSATION_ID, messages.single().conversationId)
        assertNotNull(conversation)
        assertEquals(1234L, conversation?.createdAt)
        assertEquals(1234L, conversation?.updatedAt)
    }

    @Test
    fun migrationFromEmptyVersion1StillCreatesDefaultConversation() = runBlocking {
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { legacy ->
            legacy.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` TEXT NOT NULL,
                    `role` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `imagePaths` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `requestId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_messages_createdAt` ON `chat_messages` (`createdAt`)",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_messages_requestId` ON `chat_messages` (`requestId`)",
            )
            legacy.version = 1
        }

        val migrated = Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME)
            .addMigrations(ChatDatabase.MIGRATION_1_2)
            .build()
        roomDatabase = migrated

        assertEquals(emptyList<ChatMessageEntity>(), migrated.chatMessageDao().getAll())
        assertEquals(
            listOf(DEFAULT_CONVERSATION_ID),
            migrated.chatConversationDao().getAll().map { it.id },
        )
    }

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
    }
}
