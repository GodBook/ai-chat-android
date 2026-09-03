package com.example.aichat.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.example.aichat.data.model.DEFAULT_CONVERSATION_ID
import com.example.aichat.data.model.DEFAULT_CONVERSATION_TITLE

@Database(
    entities = [ChatMessageEntity::class, ChatConversationEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatConversationDao(): ChatConversationDao

    companion object {
        /**
         * Adds the conversation dimension to the v1 schema. The old table is
         * deliberately altered in place so message ids, image paths and
         * request ids remain untouched.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
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
                database.execSQL(
                    "ALTER TABLE `chat_messages` " +
                        "ADD COLUMN `conversationId` TEXT NOT NULL DEFAULT '$DEFAULT_CONVERSATION_ID'",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_conversations_updatedAt` " +
                        "ON `chat_conversations` (`updatedAt`)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId` " +
                        "ON `chat_messages` (`conversationId`)",
                )
                // An aggregate SELECT always yields one row, including for an
                // empty v1 database. SQLite's clock is only a fallback for the
                // fresh-install-without-messages case.
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `chat_conversations` (`id`, `title`, `createdAt`, `updatedAt`)
                    SELECT '$DEFAULT_CONVERSATION_ID', '$DEFAULT_CONVERSATION_TITLE',
                        COALESCE(MIN(`createdAt`), CAST(strftime('%s','now') AS INTEGER) * 1000),
                        COALESCE(MAX(`createdAt`), CAST(strftime('%s','now') AS INTEGER) * 1000)
                    FROM `chat_messages`
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    UPDATE `chat_conversations`
                    SET `updatedAt` = COALESCE(
                        (SELECT MAX(`createdAt`) FROM `chat_messages`
                         WHERE `conversationId` = '$DEFAULT_CONVERSATION_ID'),
                        `updatedAt`
                    )
                    WHERE `id` = '$DEFAULT_CONVERSATION_ID'
                    """.trimIndent(),
                )
            }
        }

        private val CREATE_DEFAULT_CONVERSATION = object : RoomDatabase.Callback() {
            override fun onCreate(database: SupportSQLiteDatabase) {
                super.onCreate(database)
                val now = System.currentTimeMillis()
                database.execSQL(
                    "INSERT OR IGNORE INTO `chat_conversations` " +
                        "(`id`, `title`, `createdAt`, `updatedAt`) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(DEFAULT_CONVERSATION_ID, DEFAULT_CONVERSATION_TITLE, now, now),
                )
            }
        }

        @Volatile
        private var instance: ChatDatabase? = null

        fun getInstance(context: Context): ChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "ai_chat.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(CREATE_DEFAULT_CONVERSATION)
                    .build()
                    .also { instance = it }
            }
    }
}
