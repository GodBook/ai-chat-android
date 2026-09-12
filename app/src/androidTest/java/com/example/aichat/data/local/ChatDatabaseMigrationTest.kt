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
import org.junit.Assert.assertNull
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
            .addMigrations(ChatDatabase.MIGRATION_1_2, ChatDatabase.MIGRATION_2_3, ChatDatabase.MIGRATION_3_4, ChatDatabase.MIGRATION_4_5, ChatDatabase.MIGRATION_5_6, ChatDatabase.MIGRATION_6_7, ChatDatabase.MIGRATION_7_8, ChatDatabase.MIGRATION_8_9, ChatDatabase.MIGRATION_9_10)
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
            .addMigrations(ChatDatabase.MIGRATION_1_2, ChatDatabase.MIGRATION_2_3, ChatDatabase.MIGRATION_3_4, ChatDatabase.MIGRATION_4_5, ChatDatabase.MIGRATION_5_6, ChatDatabase.MIGRATION_6_7, ChatDatabase.MIGRATION_7_8, ChatDatabase.MIGRATION_8_9, ChatDatabase.MIGRATION_9_10)
            .build()
        roomDatabase = migrated

        assertEquals(emptyList<ChatMessageEntity>(), migrated.chatMessageDao().getAll())
        assertEquals(
            listOf(DEFAULT_CONVERSATION_ID),
            migrated.chatConversationDao().getAll().map { it.id },
        )
    }

    @Test
    fun migrationFromVersion4AddsGroupNameColumn() = runBlocking {
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_conversations` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_messages` (
                    `id` TEXT NOT NULL,
                    `conversationId` TEXT NOT NULL DEFAULT 'default',
                    `role` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `imagePaths` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `requestId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    `thinkingContent` TEXT,
                    `thinkingDurationMs` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO `chat_conversations` (`id`, `title`, `createdAt`, `updatedAt`) VALUES ('chat-1', '测试聊天', 100, 200)",
            )
            db.version = 4
            db.execSQL("CREATE INDEX index_chat_conversations_updatedAt ON chat_conversations(updatedAt)")
            db.execSQL("CREATE INDEX index_chat_messages_createdAt ON chat_messages(createdAt)")
            db.execSQL("CREATE INDEX index_chat_messages_requestId ON chat_messages(requestId)")
            db.execSQL("CREATE INDEX index_chat_messages_conversationId ON chat_messages(conversationId)")
            db.execSQL("CREATE INDEX index_chat_messages_conversationId_createdAt_id ON chat_messages(conversationId, createdAt, id)")
        }

        val migrated = Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME)
            .addMigrations(ChatDatabase.MIGRATION_1_2, ChatDatabase.MIGRATION_2_3, ChatDatabase.MIGRATION_3_4, ChatDatabase.MIGRATION_4_5, ChatDatabase.MIGRATION_5_6, ChatDatabase.MIGRATION_6_7, ChatDatabase.MIGRATION_7_8, ChatDatabase.MIGRATION_8_9, ChatDatabase.MIGRATION_9_10)
            .build()
        roomDatabase = migrated

        val conversation = migrated.chatConversationDao().getById("chat-1")
        assertNotNull(conversation)
        assertEquals("chat-1", conversation?.id)
        assertEquals("测试聊天", conversation?.title)
        assertNull(conversation?.groupName)

        migrated.chatConversationDao().updateGroup("chat-1", "工作", 300)
        assertEquals("工作", migrated.chatConversationDao().getById("chat-1")?.groupName)
    }

    @Test
    fun migrationFromVersion7PreservesChatsAndSavesIndividualIcons() = runBlocking {
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE chat_conversations (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, groupName TEXT, isPinned INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX index_chat_conversations_updatedAt ON chat_conversations(updatedAt)")
            db.execSQL("CREATE INDEX index_chat_conversations_isPinned_updatedAt ON chat_conversations(isPinned, updatedAt)")
            db.execSQL("CREATE TABLE chat_messages (id TEXT NOT NULL PRIMARY KEY, role TEXT NOT NULL, text TEXT NOT NULL, imagePaths TEXT NOT NULL, status TEXT NOT NULL, requestId TEXT, createdAt INTEGER NOT NULL, errorMessage TEXT, conversationId TEXT NOT NULL DEFAULT 'default', thinkingContent TEXT, thinkingDurationMs INTEGER, webSearchResults TEXT)")
            db.execSQL("CREATE INDEX index_chat_messages_createdAt ON chat_messages(createdAt)")
            db.execSQL("CREATE INDEX index_chat_messages_requestId ON chat_messages(requestId)")
            db.execSQL("CREATE INDEX index_chat_messages_conversationId ON chat_messages(conversationId)")
            db.execSQL("CREATE INDEX index_chat_messages_conversationId_createdAt_id ON chat_messages(conversationId, createdAt, id)")
            db.execSQL("INSERT INTO chat_conversations VALUES ('one','保留聊天',100,200,'工作',1), ('two','第二个聊天',101,201,NULL,0)")
            db.execSQL("INSERT INTO chat_messages (id,role,text,imagePaths,status,createdAt,conversationId) VALUES ('message','USER','保留消息','[]','SENT',100,'one')")
            db.version = 7
        }
        val migrated = Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME).addMigrations(ChatDatabase.MIGRATION_7_8, ChatDatabase.MIGRATION_8_9, ChatDatabase.MIGRATION_9_10).build()
        roomDatabase = migrated
        assertEquals("保留消息", migrated.chatMessageDao().getAll().single().text)
        val original = migrated.chatConversationDao().getById("one")!!
        assertNull(original.icon)
        assertEquals(true, original.isPinned)
        migrated.chatConversationDao().setIcon("one", "🧠")
        assertNull(migrated.chatConversationDao().getById("two")!!.icon)
        migrated.close()
        val reopened = Room.databaseBuilder(context, ChatDatabase::class.java, DATABASE_NAME).build()
        roomDatabase = reopened
        assertEquals(original.copy(icon = "🧠"), reopened.chatConversationDao().getById("one"))
        reopened.chatConversationDao().setIcon("one", null)
        assertNull(reopened.chatConversationDao().getById("one")!!.icon)
    }

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
    }
}
