package com.example.aichat.data.local

import com.example.aichat.data.model.*
import android.content.Context
import android.net.Uri
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupManifest(
    val appVersion: String,
    val versionCode: Long,
    val schemaVersion: Int,
    val exportedAt: Long,
    val conversationCount: Int,
    val messageCount: Int,
    val imageCount: Int,
)

@Serializable
data class BackupConversation(
    val id: String,
    val title: String,
    val groupName: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val icon: String? = null,
    val contextWindowLimit: Int = 8,
)

@Serializable
data class BackupMessage(
    val id: String,
    val role: String,
    val text: String,
    val imageFileNames: List<String> = emptyList(),
    val status: String,
    val requestId: String? = null,
    val createdAt: Long,
    val errorMessage: String? = null,
    val conversationId: String,
    val thinkingContent: String? = null,
    val thinkingDurationMs: Long? = null,
    val webSearchResults: String? = null,
)

@Serializable
data class BackupPayload(
    val manifest: BackupManifest,
    val conversations: List<BackupConversation>,
    val messages: List<BackupMessage>,
    val knowledge: KnowledgeBackup = KnowledgeBackup(),
)

data class ImportSummary(
    val conversationCount: Int,
    val messageCount: Int,
    val imageCount: Int,
)

object BackupRestoreManager {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    suspend fun exportBackup(
        context: Context,
        outputUri: Uri,
        conversations: List<ChatConversation>,
        messages: List<ChatMessage>,
        imageDirectory: File,
        appVersion: String = "1.6.4",
        versionCode: Long = 26L,
        knowledge: KnowledgeBackup = KnowledgeBackup(),
    ): Result<BackupManifest> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(outputUri)?.use { rawOut ->
                exportBackupToStream(rawOut, conversations, messages, imageDirectory, appVersion, versionCode, knowledge)
            } ?: throw IOException("无法打开输出流写入备份文件")
        }
    }

    fun exportBackupToStream(
        outputStream: OutputStream,
        conversations: List<ChatConversation>,
        messages: List<ChatMessage>,
        imageDirectory: File,
        appVersion: String = "1.6.4",
        versionCode: Long = 26L,
        knowledge: KnowledgeBackup = KnowledgeBackup(),
    ): BackupManifest {
        val allImagePaths = messages.flatMap { it.imagePaths }.toSet()
        val existingImageFiles = allImagePaths.mapNotNull { path ->
            val f = File(path)
            if (f.isFile) f else null
        }
        val imageEntryNames = mutableMapOf<String, String>()
        existingImageFiles.forEachIndexed { index, file ->
            val ext = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
            imageEntryNames[file.absolutePath] = "image_${index + 1}_${file.nameWithoutExtension}$ext"
        }

        val backupConversations = conversations.map {
            BackupConversation(
                id = it.id,
                title = it.title,
                groupName = it.groupName,
                createdAt = it.createdAt,
                updatedAt = it.updatedAt,
                isPinned = it.isPinned,
                icon = it.icon,
                contextWindowLimit = it.contextWindowLimit,
            )
        }

        val backupMessages = messages.map { msg ->
            BackupMessage(
                id = msg.id,
                role = msg.role.name,
                text = msg.text,
                imageFileNames = msg.imagePaths.mapNotNull { imageEntryNames[it] },
                status = msg.status.name,
                requestId = msg.requestId,
                createdAt = msg.createdAt,
                errorMessage = msg.errorMessage,
                conversationId = msg.conversationId,
                thinkingContent = msg.thinkingContent,
                thinkingDurationMs = msg.thinkingDurationMs,
                webSearchResults = com.example.aichat.data.model.encodeWebSearchResults(msg.webSearchResults),
            )
        }

        val manifest = BackupManifest(
            appVersion = appVersion,
            versionCode = versionCode,
            schemaVersion = 9,
            exportedAt = System.currentTimeMillis(),
            conversationCount = backupConversations.size,
            messageCount = backupMessages.size,
            imageCount = existingImageFiles.size,
        )

        val payload = BackupPayload(
            manifest = manifest,
            conversations = backupConversations,
            messages = backupMessages,
            knowledge = knowledge,
        )

        val payloadJson = json.encodeToString(payload)

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            // 1. Write data.json
            val dataEntry = ZipEntry("data.json")
            zipOut.putNextEntry(dataEntry)
            zipOut.write(payloadJson.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 2. Write images
            val buffer = ByteArray(8 * 1024)
            for (file in existingImageFiles) {
                val entryName = imageEntryNames[file.absolutePath] ?: continue
                val imageEntry = ZipEntry("images/$entryName")
                zipOut.putNextEntry(imageEntry)
                file.inputStream().use { fileIn ->
                    var read: Int
                    while (fileIn.read(buffer).also { read = it } != -1) {
                        zipOut.write(buffer, 0, read)
                    }
                }
                zipOut.closeEntry()
            }
        }

        return manifest
    }

    suspend fun importBackup(
        context: Context,
        inputUri: Uri,
        imageDirectory: File,
        existingConversationIds: Set<String>,
        onInsertKnowledge: (suspend (List<ChatConversationEntity>, List<ChatMessageEntity>, KnowledgeBackup) -> Unit)? = null,
        onInsertData: suspend (List<ChatConversationEntity>, List<ChatMessageEntity>) -> Unit,
    ): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val tempDir = File(context.cacheDir, "import_temp_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                context.contentResolver.openInputStream(inputUri)?.use { rawIn ->
                    importBackupFromStream(rawIn, tempDir, imageDirectory, existingConversationIds, onInsertKnowledge, onInsertData)
                } ?: throw IOException("无法打开输入流读取备份文件")
            } finally {
                tempDir.deleteRecursively()
            }
        }
    }

    suspend fun importBackupFromStream(
        inputStream: InputStream,
        tempDir: File,
        imageDirectory: File,
        existingConversationIds: Set<String>,
        onInsertKnowledge: (suspend (List<ChatConversationEntity>, List<ChatMessageEntity>, KnowledgeBackup) -> Unit)? = null,
        onInsertData: suspend (List<ChatConversationEntity>, List<ChatMessageEntity>) -> Unit,
    ): ImportSummary {
        var payloadJson: String? = null
        val imageFilesMap = mutableMapOf<String, File>()

        tempDir.mkdirs()
        imageDirectory.mkdirs()

        ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
            var entry = zipIn.nextEntry
            val buffer = ByteArray(8 * 1024)
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    if (name == "data.json") {
                        payloadJson = zipIn.bufferedReader(Charsets.UTF_8).readText()
                    } else if (name.startsWith("images/")) {
                        val entryFileName = File(name).name
                        val targetFile = File(tempDir, entryFileName)
                        targetFile.outputStream().use { fileOut ->
                            var read: Int
                            while (zipIn.read(buffer).also { read = it } != -1) {
                                fileOut.write(buffer, 0, read)
                            }
                        }
                        imageFilesMap[entryFileName] = targetFile
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        val payload = payloadJson?.let {
            json.decodeFromString<BackupPayload>(it)
        } ?: throw IOException("备份文件中未找到有效的 data.json 数据")

        require(payload.manifest.schemaVersion in 1..9) { "不支持此备份格式，请使用对应或更新版本的应用" }
        require(payload.conversations.map { it.id }.distinct().size == payload.conversations.size &&
            payload.messages.map { it.id }.distinct().size == payload.messages.size) { "备份包含重复 ID" }
        require(payload.knowledge.cards.size <= 100_000 && payload.knowledge.records.size <= 100_000) { "备份资料数量超限" }
        payload.knowledge.cards.forEach { it.validate() }
        val messageIdMap = payload.messages.associate { it.id to UUID.randomUUID().toString() }
        val requestIdMap = payload.messages.mapNotNull { it.requestId }.distinct().associateWith { UUID.randomUUID().toString() }
        val cardIdMap = payload.knowledge.cards.associate { it.id to UUID.randomUUID().toString() }
        val conversationIdMap = mutableMapOf<String, String>()
        val importedConversations = payload.conversations.map { conv ->
            val finalId = if (conv.id in existingConversationIds) {
                UUID.randomUUID().toString()
            } else {
                conv.id
            }
            conversationIdMap[conv.id] = finalId
            ChatConversationEntity(
                id = finalId,
                title = conv.title,
                groupName = conv.groupName,
                createdAt = conv.createdAt,
                updatedAt = conv.updatedAt,
                isPinned = conv.isPinned,
                icon = conv.icon?.takeIf { it.length <= 100_000 },
                contextWindowLimit = conv.contextWindowLimit.coerceIn(0, 100),
            )
        }

        val finalImagePathMap = mutableMapOf<String, String>()
        for ((entryFileName, tempFile) in imageFilesMap) {
            var destFile = File(imageDirectory, entryFileName)
            if (destFile.exists()) {
                destFile = File(imageDirectory, "imported_${UUID.randomUUID()}_$entryFileName")
            }
            tempFile.copyTo(destFile, overwrite = true)
            finalImagePathMap[entryFileName] = destFile.absolutePath
        }

        try {
        val importedMessages = payload.messages.map { msg ->
            val finalConversationId = conversationIdMap[msg.conversationId] ?: throw IOException("消息关联的会话缺失")
            val finalImagePaths = msg.imageFileNames.mapNotNull { finalImagePathMap[it] }
            val role = runCatching { MessageRole.valueOf(msg.role) }.getOrDefault(MessageRole.ASSISTANT)
            val status = runCatching { MessageStatus.valueOf(msg.status) }.getOrDefault(MessageStatus.SENT)

            ChatMessageEntity(
                id = messageIdMap.getValue(msg.id),
                conversationId = finalConversationId,
                role = role.name,
                text = msg.text,
                imagePaths = finalImagePaths.joinToString(separator = "\n"),
                status = status.name,
                requestId = msg.requestId?.let { requestIdMap.getValue(it) },
                createdAt = msg.createdAt,
                errorMessage = msg.errorMessage,
                thinkingContent = msg.thinkingContent,
                thinkingDurationMs = msg.thinkingDurationMs,
                webSearchResults = msg.webSearchResults,
            )
        }

        fun remapCard(card: KnowledgeCard) = card.copy(id = cardIdMap[card.id] ?: UUID.randomUUID().toString(),
            conversationId = card.conversationId?.let { conversationIdMap[it] },
            sourceMessageId = card.sourceMessageId?.let { messageIdMap[it] ?: "deleted:${UUID.randomUUID()}" },
            sourceRequestId = card.sourceRequestId?.let { requestIdMap[it] })
        val knowledge = KnowledgeBackup(
            cards = payload.knowledge.cards.map(::remapCard),
            branches = payload.knowledge.branches.map { branch ->
                val message = payload.messages.firstOrNull { it.id == branch.assistantMessageId }
                require(message?.conversationId == branch.conversationId && message.requestId == branch.requestId && message.role == "ASSISTANT") { "回答版本关联无效" }
                branch.copy(conversationId = conversationIdMap.getValue(branch.conversationId),
                    requestId = requestIdMap.getValue(branch.requestId), assistantMessageId = messageIdMap.getValue(branch.assistantMessageId))
            },
            records = payload.knowledge.records.map { record ->
                require(record.formatVersion == 1 && record.cards.size <= 8 && record.systemPrompt.length <= ContextPlanner.MAX_CHARS &&
                    (record.searchContext?.length ?: 0) <= ContextPlanner.MAX_CHARS) { "上下文清单格式无效" }
                record.cards.forEach { it.validate() }
                require(record.entries.size <= 100_000) { "上下文清单过大" }
                val references = record.entries.flatMap { listOfNotNull(it.userId, it.assistantId) + it.otherAssistantIds } + record.userMessageId
                require(references.all { id -> payload.messages.none { it.id == id && it.conversationId != record.conversationId } }) { "上下文不能关联其他会话的消息" }
                require(payload.messages.any { it.id == record.assistantMessageId && it.conversationId == record.conversationId && it.role == "ASSISTANT" }) { "上下文清单关联无效" }
                record.copy(assistantMessageId = messageIdMap.getValue(record.assistantMessageId),
                    conversationId = conversationIdMap.getValue(record.conversationId),
                    userMessageId = messageIdMap[record.userMessageId] ?: "deleted:${UUID.randomUUID()}",
                    entries = record.entries.map { entry -> entry.copy(userId = messageIdMap[entry.userId] ?: "deleted:${UUID.randomUUID()}", assistantId = entry.assistantId?.let { messageIdMap[it] }, otherAssistantIds = entry.otherAssistantIds.mapNotNull { messageIdMap[it] }) },
                    cards = record.cards.map(::remapCard))
            })
        if (onInsertKnowledge != null) onInsertKnowledge(importedConversations, importedMessages, knowledge)
        else {
            require(knowledge.cards.isEmpty() && knowledge.branches.isEmpty() && knowledge.records.isEmpty()) { "此恢复入口不支持结论卡，请使用应用的数据恢复入口" }
            onInsertData(importedConversations, importedMessages)
        }

        return ImportSummary(
            conversationCount = importedConversations.size,
            messageCount = importedMessages.size,
            imageCount = imageFilesMap.size,
        )
        } catch (failure: Throwable) {
            finalImagePathMap.values.forEach { path -> File(path).delete() }
            throw failure
        }
    }
}
