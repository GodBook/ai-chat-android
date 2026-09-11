package com.example.aichat.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.example.aichat.data.model.BUILT_IN_PERSONAS
import com.example.aichat.data.model.BUILT_IN_PROVIDER_PROFILES
import com.example.aichat.data.model.DEFAULT_CONVERSATION_ID
import com.example.aichat.data.model.DEFAULT_CONVERSATION_TITLE
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(
    entities = [
        ChatMessageEntity::class,
        ChatConversationEntity::class,
        ChatPersonaEntity::class,
        ProviderProfileEntity::class,
    ],
    version = 9,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatConversationDao(): ChatConversationDao
    abstract fun chatPersonaDao(): ChatPersonaDao
    abstract fun providerProfileDao(): ProviderProfileDao

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

        /** Speeds up the newest-message-per-conversation query used by the chat list. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId_createdAt_id` " +
                        "ON `chat_messages` (`conversationId`, `createdAt`, `id`)",
                )
            }
        }

        /** Adds thinkingContent and thinkingDurationMs to chat_messages. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `thinkingContent` TEXT")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `thinkingDurationMs` INTEGER")
            }
        }

        /** Adds groupName to chat_conversations. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `chat_conversations` ADD COLUMN `groupName` TEXT")
            }
        }

        /** Adds webSearchResults to chat_messages. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `webSearchResults` TEXT")
            }
        }

        /** Adds isPinned to chat_conversations. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `chat_conversations` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_conversations_isPinned_updatedAt` ON `chat_conversations` (`isPinned`, `updatedAt`)")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `chat_conversations` ADD COLUMN `icon` TEXT")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_personas` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `avatar` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `systemPrompt` TEXT NOT NULL,
                        `temperature` REAL NOT NULL DEFAULT 1.0,
                        `preferredModel` TEXT,
                        `category` TEXT NOT NULL DEFAULT 'general',
                        `isBuiltIn` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_personas_category` ON `chat_personas` (`category`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_personas_createdAt` ON `chat_personas` (`createdAt`)")

                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `provider_profiles` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `baseUrl` TEXT NOT NULL,
                        `defaultModel` TEXT NOT NULL,
                        `candidateModels` TEXT NOT NULL DEFAULT '[]',
                        `visionEnabled` INTEGER NOT NULL DEFAULT 1,
                        `customHeaders` TEXT NOT NULL DEFAULT '{}',
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `presetType` TEXT NOT NULL DEFAULT 'custom',
                        `sortOrder` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )

                database.execSQL("ALTER TABLE `chat_conversations` ADD COLUMN `personaId` TEXT")
                database.execSQL("ALTER TABLE `chat_conversations` ADD COLUMN `providerProfileId` TEXT")
                database.execSQL("ALTER TABLE `chat_conversations` ADD COLUMN `contextWindowLimit` INTEGER NOT NULL DEFAULT 8")

                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `promptTokens` INTEGER")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `completionTokens` INTEGER")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `totalTokens` INTEGER")
                database.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `generationDurationMs` INTEGER")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_text` ON `chat_messages` (`text`)")

                insertDefaultSeedData(database)
            }
        }

        private fun insertDefaultSeedData(database: SupportSQLiteDatabase) {
            for (persona in BUILT_IN_PERSONAS) {
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `chat_personas`
                    (`id`, `name`, `avatar`, `description`, `systemPrompt`, `temperature`, `preferredModel`, `category`, `isBuiltIn`, `createdAt`, `updatedAt`)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        persona.id, persona.name, persona.avatar, persona.description,
                        persona.systemPrompt, persona.temperature, persona.preferredModel,
                        persona.category, if (persona.isBuiltIn) 1 else 0, persona.createdAt, persona.updatedAt,
                    ),
                )
            }

            for (profile in BUILT_IN_PROVIDER_PROFILES) {
                database.execSQL(
                    """
                    INSERT OR IGNORE INTO `provider_profiles`
                    (`id`, `name`, `baseUrl`, `defaultModel`, `candidateModels`, `visionEnabled`, `customHeaders`, `isDefault`, `presetType`, `sortOrder`, `createdAt`)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        profile.id, profile.name, profile.baseUrl, profile.defaultModel,
                        Json.encodeToString(profile.candidateModels), if (profile.visionEnabled) 1 else 0,
                        Json.encodeToString(profile.customHeaders), if (profile.isDefault) 1 else 0,
                        profile.presetType, profile.sortOrder, profile.createdAt,
                    ),
                )
            }
        }

        private val CREATE_DEFAULT_CONVERSATION = object : RoomDatabase.Callback() {
            override fun onCreate(database: SupportSQLiteDatabase) {
                super.onCreate(database)
                val now = System.currentTimeMillis()
                database.execSQL(
                    "INSERT OR IGNORE INTO `chat_conversations` " +
                        "(`id`, `title`, `createdAt`, `updatedAt`, `isPinned`, `contextWindowLimit`) VALUES (?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(DEFAULT_CONVERSATION_ID, DEFAULT_CONVERSATION_TITLE, now, now, 0, 8),
                )
                insertDefaultSeedData(database)
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                    )
                    .addCallback(CREATE_DEFAULT_CONVERSATION)
                    .build()
                    .also { instance = it }
            }
    }
}
