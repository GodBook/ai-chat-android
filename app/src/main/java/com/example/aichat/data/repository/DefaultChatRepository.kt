package com.example.aichat.data.repository

import com.example.aichat.data.local.ApiKeyStore
import com.example.aichat.data.local.ChatConversationDao
import com.example.aichat.data.local.ChatConversationEntity
import com.example.aichat.data.local.ChatMessageDao
import com.example.aichat.data.local.ChatMessageEntity
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ConfigStore
import com.example.aichat.data.local.ImageFileStore
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatRequestMessage
import com.example.aichat.data.model.DEFAULT_CONVERSATION_ID
import com.example.aichat.data.model.DEFAULT_CONVERSATION_TITLE
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import com.example.aichat.data.model.ProviderConfig
import com.example.aichat.data.model.resolveMessageBranches
import com.example.aichat.data.model.toDomain
import com.example.aichat.data.model.toEntity
import com.example.aichat.data.network.ChatClientException
import com.example.aichat.data.network.ChatErrorKind
import com.example.aichat.data.network.ChatStreamEvent
import com.example.aichat.data.network.OpenAiCompatibleClient
import com.example.aichat.data.network.ProbeResult
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DefaultChatRepository(
    private val database: ChatDatabase,
    private val configStore: ConfigStore,
    private val apiKeyStore: ApiKeyStore,
    private val imageFileStore: ImageFileStore,
    private val client: OpenAiCompatibleClient,
    private val webSearchClient: com.example.aichat.data.network.WebSearchClient = com.example.aichat.data.network.WebSearchClient(),
) : ChatRepository {
    private val dao: ChatMessageDao = database.chatMessageDao()
    private val conversationDao: ChatConversationDao = database.chatConversationDao()

    override val conversations: Flow<List<ChatConversation>> = conversationDao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    override val messages: Flow<List<ChatMessage>> = observeMessages(DEFAULT_CONVERSATION_ID)

    override fun observeAllMessages(): Flow<List<ChatMessage>> = dao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }
        .conflate()
        .flowOn(Dispatchers.IO)

    override fun observeConversationPreviews(): Flow<List<ChatMessage>> = dao.observeLatestPerConversation()
        .map { rows -> rows.map { it.toDomain() } }
        .conflate()
        .flowOn(Dispatchers.IO)

    override fun observeAnyWorking(): Flow<Boolean> = dao.observeAnyGenerating()
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeForConversation(normalizeConversationId(conversationId))
        .map { rows -> rows.map { it.toDomain() } }
        .conflate()
        .flowOn(Dispatchers.IO)

    @Volatile
    private var activeJob: Job? = null

    @Volatile
    private var activeAssistantId: String? = null

    @Volatile
    private var activeConversationId: String? = null

    @Volatile
    private var stopRequestedFor: String? = null

    private val activeRequestLock = Any()
    private val requestMutex = Mutex()

    override suspend fun sendMessage(text: String, imagePaths: List<String>, webSearch: Boolean): String =
        sendMessage(DEFAULT_CONVERSATION_ID, text, imagePaths, webSearch)

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        imagePaths: List<String>,
        webSearch: Boolean,
    ): String = withRequestLock {
        val selectedConversationId = normalizeConversationId(conversationId)
        ensureConversation(selectedConversationId)
        val cleanText = text.trim()
        require(cleanText.isNotEmpty() || imagePaths.isNotEmpty()) { "消息内容不能为空" }

        val config = readProviderConfig()
        client.validateConfig(config)
        if (imagePaths.isNotEmpty() && !config.visionEnabled) {
            throw ChatClientException(
                ChatErrorKind.MISSING_CONFIG,
                "请先在设置中开启图片支持",
            )
        }

        val searchResults = if (webSearch && cleanText.isNotEmpty()) {
            runCatching { webSearchClient.search(cleanText) }.getOrNull()?.takeIf { it.isNotEmpty() }
        } else null

        val requestId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val user = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = selectedConversationId,
            role = MessageRole.USER,
            text = cleanText,
            imagePaths = imagePaths,
            status = MessageStatus.SENT,
            requestId = requestId,
            createdAt = now,
        )
        val assistant = ChatMessage(
            id = UUID.randomUUID().toString(),
            conversationId = selectedConversationId,
            role = MessageRole.ASSISTANT,
            text = "",
            status = MessageStatus.SENDING,
            requestId = requestId,
            // Keep the assistant after the user even when both are inserted in one transaction.
            createdAt = now + 1,
            webSearchResults = searchResults,
        )
        val userRequestMessage = if (searchResults != null) {
            val searchContext = buildWebSearchPrompt(cleanText, searchResults)
            ChatRequestMessage(
                role = MessageRole.USER,
                text = "$cleanText\n\n$searchContext",
                imagePaths = imagePaths,
            )
        } else {
            user.toRequestMessage()
        }
        val history = database.withTransaction {
            val rawMessages = dao.getForConversation(selectedConversationId).map { it.toDomain() }
            val activeMessages = resolveMessageBranches(rawMessages)
            val requestHistory = limitContext(activeMessages
                .filter { it.status == MessageStatus.SENT }
                .map { it.toRequestMessage() }
                .plus(userRequestMessage))
            dao.insertAll(listOf(user.toEntity(), assistant.toEntity()))
            conversationDao.touch(selectedConversationId, now)
            requestHistory
        }

        val completed = try {
            runGenerationInChild(
                parentScope = this,
                config = config,
                history = history,
                assistant = assistant,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ChatClientException) {
            throw failure.asPersistedRequestFailure()
        } catch (failure: Exception) {
            throw ChatClientException(
                kind = ChatErrorKind.PROVIDER,
                message = failure.message ?: "发送失败，请点击重试",
                cause = failure,
                requestWasPersisted = true,
            )
        }
        completed.throwIfUnsuccessful()
        assistant.id
    }

    override suspend fun retryMessage(messageId: String): String? {
        val conversationId = withContext(Dispatchers.IO) {
            dao.getById(messageId)?.conversationId
        } ?: return null
        return retryMessage(conversationId, messageId)
    }

    override suspend fun retryMessage(conversationId: String, messageId: String): String? =
        withRequestLock {
        val selectedConversationId = normalizeConversationId(conversationId)
        ensureConversation(selectedConversationId)
        val target = dao.getById(messageId)?.toDomain() ?: return@withRequestLock null
        if (target.role != MessageRole.ASSISTANT ||
            target.status !in setOf(MessageStatus.FAILED, MessageStatus.INTERRUPTED) ||
            target.conversationId != selectedConversationId
        ) {
            return@withRequestLock null
        }
        val config = readProviderConfig()
        client.validateConfig(config)
        val all = dao.getForConversation(selectedConversationId).map { it.toDomain() }
        val user = target.requestId?.let { requestId ->
            all.firstOrNull { it.requestId == requestId && it.role == MessageRole.USER }
        } ?: all.lastOrNull { it.role == MessageRole.USER && it.createdAt < target.createdAt }
            ?: return@withRequestLock null
        if (user.imagePaths.isNotEmpty() && !config.visionEnabled) {
            throw ChatClientException(ChatErrorKind.MISSING_CONFIG, "请先在设置中开启图片支持")
        }
        val userIndex = all.indexOfFirst { it.id == user.id }
        val userRequestMessage = if (target.webSearchResults != null) {
            val searchContext = buildWebSearchPrompt(user.text, target.webSearchResults)
            ChatRequestMessage(
                role = MessageRole.USER,
                text = "${user.text}\n\n$searchContext",
                imagePaths = user.imagePaths,
            )
        } else {
            user.toRequestMessage()
        }
        val activePriorHistory = resolveMessageBranches(all.take(userIndex.coerceAtLeast(0)))
        val history = limitContext(activePriorHistory
            .filter { it.status == MessageStatus.SENT }
            .map { it.toRequestMessage() }
            .plus(userRequestMessage))
        val pending = target.copy(
            text = "",
            thinkingContent = null,
            thinkingDurationMs = null,
            status = MessageStatus.SENDING,
            errorMessage = null,
        )
        database.withTransaction {
            dao.update(pending.toEntity())
            conversationDao.touch(selectedConversationId, System.currentTimeMillis())
        }
        val completed = runGenerationInChild(this, config, history, pending)
        completed.throwIfUnsuccessful()
        pending.id
    }

    override suspend fun regenerateMessage(
        conversationId: String,
        messageId: String,
    ): String? = withRequestLock {
        val selectedConversationId = normalizeConversationId(conversationId)
        ensureConversation(selectedConversationId)
        val target = dao.getById(messageId)?.toDomain() ?: return@withRequestLock null
        if (target.role != MessageRole.ASSISTANT || target.conversationId != selectedConversationId) {
            return@withRequestLock null
        }
        val config = readProviderConfig()
        client.validateConfig(config)
        val all = dao.getForConversation(selectedConversationId).map { it.toDomain() }
        val user = target.requestId?.let { requestId ->
            all.firstOrNull { it.requestId == requestId && it.role == MessageRole.USER }
        } ?: all.lastOrNull { it.role == MessageRole.USER && it.createdAt < target.createdAt }
            ?: return@withRequestLock null
        if (user.imagePaths.isNotEmpty() && !config.visionEnabled) {
            throw ChatClientException(ChatErrorKind.MISSING_CONFIG, "请先在设置中开启图片支持")
        }
        val userIndex = all.indexOfFirst { it.id == user.id }
        val userRequestMessage = if (target.webSearchResults != null) {
            val searchContext = buildWebSearchPrompt(user.text, target.webSearchResults)
            ChatRequestMessage(
                role = MessageRole.USER,
                text = "${user.text}\n\n$searchContext",
                imagePaths = user.imagePaths,
            )
        } else {
            user.toRequestMessage()
        }
        val activePriorHistory = resolveMessageBranches(all.take(userIndex.coerceAtLeast(0)))
        val history = limitContext(activePriorHistory
            .filter { it.status == MessageStatus.SENT }
            .map { it.toRequestMessage() }
            .plus(userRequestMessage))

        val commonRequestId = target.requestId ?: user.requestId ?: java.util.UUID.randomUUID().toString()
        val newAssistantId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val pending = ChatMessage(
            id = newAssistantId,
            conversationId = selectedConversationId,
            role = MessageRole.ASSISTANT,
            text = "",
            status = MessageStatus.SENDING,
            errorMessage = null,
            imagePaths = emptyList(),
            requestId = commonRequestId,
            createdAt = now,
            thinkingContent = null,
            thinkingDurationMs = null,
            webSearchResults = target.webSearchResults,
        )
        database.withTransaction {
            if (target.requestId == null) {
                dao.update(target.copy(requestId = commonRequestId).toEntity())
            }
            if (user.requestId == null) {
                dao.update(user.copy(requestId = commonRequestId).toEntity())
            }
            dao.insert(pending.toEntity())
            conversationDao.touch(selectedConversationId, now)
        }
        val completed = runGenerationInChild(this, config, history, pending)
        completed.throwIfUnsuccessful()
        pending.id
    }

    override suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val target = dao.getById(messageId)?.toDomain() ?: return@withContext false
        if (target.imagePaths.isNotEmpty()) {
            target.imagePaths.forEach { path ->
                runCatching { imageFileStore.delete(path) }
            }
        }
        val deleted = dao.deleteById(messageId) > 0
        if (deleted) {
            conversationDao.touch(target.conversationId, System.currentTimeMillis())
        }
        deleted
    }

    override fun stopGeneration() {
        val jobToCancel = synchronized(activeRequestLock) {
            val assistantId = activeAssistantId ?: return
            stopRequestedFor = assistantId
            activeJob
        }
        jobToCancel?.cancel(CancellationException("用户停止生成"))
    }

    override suspend fun getConversation(conversationId: String): ChatConversation? =
        withContext(Dispatchers.IO) {
            conversationDao.getById(normalizeConversationId(conversationId))?.toDomain()
        }

    override suspend fun createConversation(
        title: String,
        groupName: String?,
    ): ChatConversation {
        val now = System.currentTimeMillis()
        val conversation = ChatConversation(
            id = UUID.randomUUID().toString(),
            title = normalizeTitle(title),
            createdAt = now,
            updatedAt = now,
            groupName = groupName?.trim()?.takeIf { it.isNotEmpty() },
        )
        withContext(Dispatchers.IO) { conversationDao.insert(conversation.toEntity()) }
        return conversation
    }

    override suspend fun updateConversationGroup(
        conversationId: String,
        groupName: String?,
    ): ChatConversation? {
        val id = normalizeConversationId(conversationId)
        val cleanGroup = groupName?.trim()?.takeIf { it.isNotEmpty() }
        return withContext(Dispatchers.IO) {
            if (conversationDao.updateGroup(id, cleanGroup, System.currentTimeMillis()) == 0) {
                null
            } else {
                conversationDao.getById(id)?.toDomain()
            }
        }
    }

    override suspend fun updateConversationsGroup(
        conversationIds: Collection<String>,
        groupName: String?,
    ): Int {
        val targetIds = conversationIds.map { normalizeConversationId(it) }.distinct()
        if (targetIds.isEmpty()) return 0
        val cleanGroup = groupName?.trim()?.takeIf { it.isNotEmpty() }
        return withContext(Dispatchers.IO) {
            conversationDao.updateGroups(targetIds, cleanGroup, System.currentTimeMillis())
        }
    }

    override suspend fun renameGroup(
        oldGroupName: String,
        newGroupName: String,
    ): Int {
        val cleanOld = oldGroupName.trim()
        val cleanNew = newGroupName.trim()
        if (cleanOld.isEmpty() || cleanNew.isEmpty() || cleanOld == cleanNew) return 0
        return withContext(Dispatchers.IO) {
            conversationDao.renameGroup(cleanOld, cleanNew, System.currentTimeMillis())
        }
    }

    override suspend fun renameConversation(
        conversationId: String,
        title: String,
    ): ChatConversation? {
        val cleanTitle = normalizeTitle(title)
        val id = normalizeConversationId(conversationId)
        return withContext(Dispatchers.IO) {
            if (conversationDao.rename(id, cleanTitle, System.currentTimeMillis()) == 0) {
                null
            } else {
                conversationDao.getById(id)?.toDomain()
            }
        }
    }

    override suspend fun setConversationPinned(
        conversationId: String,
        isPinned: Boolean,
    ): ChatConversation? {
        val id = normalizeConversationId(conversationId)
        return withContext(Dispatchers.IO) {
            if (conversationDao.setPinned(id, isPinned) == 0) {
                null
            } else {
                conversationDao.getById(id)?.toDomain()
            }
        }
    }

    override suspend fun deleteConversation(conversationId: String): Boolean {
        val id = normalizeConversationId(conversationId)
        awaitActiveRequestIfNeeded(id)
        requestMutex.lock()
        try {
            val removedMessages = withContext(Dispatchers.IO) {
                database.withTransaction {
                    if (conversationDao.getById(id) == null) return@withTransaction null
                    val oldMessages = dao.getForConversation(id).map { it.toDomain() }
                    // Keep this explicit because the v1 table could not have a conversation FK.
                    dao.deleteForConversation(id)
                    check(conversationDao.deleteById(id) == 1) { "删除聊天失败" }
                    if (conversationDao.count() == 0) {
                        val now = System.currentTimeMillis()
                        conversationDao.insert(
                            ChatConversation(
                                id = DEFAULT_CONVERSATION_ID,
                                title = DEFAULT_CONVERSATION_TITLE,
                                createdAt = now,
                                updatedAt = now,
                            ).toEntity(),
                        )
                    }
                    oldMessages
                }
            }
            if (removedMessages == null) return false
            deleteUnreferencedMessageImages(removedMessages)
            return true
        } finally {
            requestMutex.unlock()
        }
    }

    override suspend fun deleteConversations(conversationIds: Collection<String>): Int {
        val targetIds = conversationIds.map { normalizeConversationId(it) }.distinct()
        if (targetIds.isEmpty()) return 0
        targetIds.forEach { awaitActiveRequestIfNeeded(it) }
        requestMutex.lock()
        try {
            val (deletedCount, removedMessages) = withContext(Dispatchers.IO) {
                database.withTransaction {
                    val messages = dao.getForConversations(targetIds).map { it.toDomain() }
                    dao.deleteForConversations(targetIds)
                    val count = conversationDao.deleteByIds(targetIds)
                    if (conversationDao.count() == 0) {
                        val now = System.currentTimeMillis()
                        conversationDao.insert(
                            ChatConversation(
                                id = DEFAULT_CONVERSATION_ID,
                                title = DEFAULT_CONVERSATION_TITLE,
                                createdAt = now,
                                updatedAt = now,
                            ).toEntity(),
                        )
                    }
                    count to messages
                }
            }
            deleteUnreferencedMessageImages(removedMessages)
            return deletedCount
        } finally {
            requestMutex.unlock()
        }
    }

    override suspend fun clearConversation() = clearConversation(DEFAULT_CONVERSATION_ID)

    override suspend fun clearConversation(conversationId: String) {
        val id = normalizeConversationId(conversationId)
        awaitActiveRequestIfNeeded(id)
        val runningJob = synchronized(activeRequestLock) { activeJob }
        runningJob?.join()
        requestMutex.lock()
        try {
            val removedMessages = withContext(Dispatchers.IO) {
                database.withTransaction {
                    val oldMessages = dao.getForConversation(id).map { it.toDomain() }
                    dao.deleteForConversation(id)
                    conversationDao.touch(id, System.currentTimeMillis())
                    oldMessages
                }
            }
            deleteUnreferencedMessageImages(removedMessages)
        } finally {
            requestMutex.unlock()
        }
    }

    override suspend fun recoverInterruptedMessages() {
        withContext(Dispatchers.IO) {
            dao.getAll()
                .filter { it.status == MessageStatus.SENDING.name || it.status == MessageStatus.STREAMING.name }
                .forEach { row ->
                    dao.update(
                        row.copy(
                            status = MessageStatus.INTERRUPTED.name,
                            errorMessage = "应用关闭时生成被中断，可点击重试",
                        ),
                    )
                }
        }
    }

    override suspend fun recoverInterruptedMessages(conversationId: String) {
        val id = normalizeConversationId(conversationId)
        withContext(Dispatchers.IO) {
            dao.getForConversation(id)
                .filter { it.status == MessageStatus.SENDING.name || it.status == MessageStatus.STREAMING.name }
                .forEach { row ->
                    dao.update(
                        row.copy(
                            status = MessageStatus.INTERRUPTED.name,
                            errorMessage = "应用关闭时生成被中断，可点击重试",
                        ),
                    )
                }
        }
    }

    override suspend fun getAllConversations(): List<ChatConversation> = withContext(Dispatchers.IO) {
        conversationDao.getAll().map { it.toDomain() }
    }

    override suspend fun getAllMessages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        dao.getAll().map { it.toDomain() }
    }

    override suspend fun getStorageStats(): AppStorageStats = withContext(Dispatchers.IO) {
        val convCount = conversationDao.count()
        val msgCount = dao.getAll().size
        val imageStats = imageFileStore.getImageStorageStats()
        AppStorageStats(
            conversationCount = convCount,
            messageCount = msgCount,
            imageCount = imageStats.fileCount,
            imageSizeBytes = imageStats.totalSizeBytes,
        )
    }

    override suspend fun cleanupOrphanImages(): Int = withContext(Dispatchers.IO) {
        val allReferenced = dao.getAll().asSequence()
            .map { it.toDomain() }
            .flatMap { it.imagePaths.asSequence() }
            .toSet()
        imageFileStore.cleanupOrphanImages(allReferenced)
    }

    override suspend fun restoreBackupData(
        conversations: List<ChatConversationEntity>,
        messages: List<ChatMessageEntity>,
    ) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                conversationDao.insertAll(conversations)
                dao.insertAll(messages)
            }
        }
    }

    override suspend fun probeModelConnection(config: ProviderConfig): ProbeResult {
        val effectiveConfig = if (config.apiKey.isNullOrBlank()) {
            config.copy(apiKey = apiKeyStore.read())
        } else config
        return client.probeConnection(effectiveConfig)
    }

    private suspend fun readProviderConfig(): ProviderConfig {
        val stored = configStore.read()
        return stored.copy(apiKey = apiKeyStore.read())
    }

    /** Creates a row when a caller targets a newly selected or legacy id. */
    private suspend fun ensureConversation(id: String) {
        withContext(Dispatchers.IO) {
            if (conversationDao.getById(id) == null) {
                val now = System.currentTimeMillis()
                conversationDao.insert(
                    ChatConversation(
                        id = id,
                        title = if (id == DEFAULT_CONVERSATION_ID) {
                            DEFAULT_CONVERSATION_TITLE
                        } else {
                            "新聊天"
                        },
                        createdAt = now,
                        updatedAt = now,
                    ).toEntity(),
                )
            }
        }
    }

    private suspend fun awaitActiveRequestIfNeeded(conversationId: String) {
        val runningJob = synchronized(activeRequestLock) {
            if (activeConversationId == conversationId) {
                stopRequestedFor = activeAssistantId
                activeJob
            } else {
                null
            }
        }
        runningJob?.cancel(CancellationException("会话操作停止生成"))
        runningJob?.join()
    }

    private fun normalizeConversationId(value: String): String =
        value.trim().ifEmpty { DEFAULT_CONVERSATION_ID }

    private fun normalizeTitle(value: String): String = value.trim().ifEmpty { "新聊天" }

    private suspend fun <T> withRequestLock(
        block: suspend kotlinx.coroutines.CoroutineScope.() -> T,
    ): T = coroutineScope {
        if (!requestMutex.tryLock()) {
            throw ChatClientException(ChatErrorKind.INVALID_REQUEST, "当前仍在生成回复，请先停止")
        }
        try {
            block(this)
        } finally {
            requestMutex.unlock()
        }
    }

    private suspend fun runGenerationInChild(
        parentScope: kotlinx.coroutines.CoroutineScope,
        config: ProviderConfig,
        history: List<ChatRequestMessage>,
        assistant: ChatMessage,
    ): ChatMessage {
        // Publish the cancellation handle and launch the child under one lock. This closes the
        // small window where a stop tap could observe an id without a cancellable Job.
        val child: Job
        synchronized(activeRequestLock) {
            activeAssistantId = assistant.id
            activeConversationId = assistant.conversationId
            stopRequestedFor = null
            child = parentScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val responseText = StringBuilder()
                val thinkingText = StringBuilder()
                val thinkingStart = System.currentTimeMillis()
                var thinkingDurationMs: Long? = null
                var lastPersistedLength = 0
                var lastPersistedThinkingLength = 0
                var lastPersistedAt = 0L
                var generationCompleted = false

                suspend fun persistStreamingText() {
                    val now = System.nanoTime() / NANOS_PER_MILLISECOND
                    val totalLen = responseText.length + thinkingText.length
                    val lastTotalLen = lastPersistedLength + lastPersistedThinkingLength
                    val enoughText = totalLen - lastTotalLen >= STREAM_PERSIST_MIN_DELTA_CHARS
                    val enoughTime = now - lastPersistedAt >= STREAM_PERSIST_INTERVAL_MS
                    if (!enoughText && !enoughTime) return
                    dao.update(
                        assistant.copy(
                            text = responseText.toString(),
                            thinkingContent = thinkingText.toString().takeIf { it.isNotEmpty() },
                            thinkingDurationMs = thinkingDurationMs,
                            status = MessageStatus.STREAMING,
                            errorMessage = null,
                        ).toEntity(),
                    )
                    lastPersistedLength = responseText.length
                    lastPersistedThinkingLength = thinkingText.length
                    lastPersistedAt = now
                }

                try {
                    dao.update(assistant.copy(status = MessageStatus.STREAMING).toEntity())
                    client.streamChat(config, history).collect { event ->
                        when (event) {
                            is ChatStreamEvent.ThinkingDelta -> {
                                thinkingText.append(event.text)
                                persistStreamingText()
                            }

                            is ChatStreamEvent.Delta -> {
                                if (thinkingDurationMs == null && thinkingText.isNotEmpty()) {
                                    thinkingDurationMs = System.currentTimeMillis() - thinkingStart
                                }
                                responseText.append(event.text)
                                persistStreamingText()
                            }

                            ChatStreamEvent.Done -> {
                                if (thinkingDurationMs == null && thinkingText.isNotEmpty()) {
                                    thinkingDurationMs = System.currentTimeMillis() - thinkingStart
                                }
                                withContext(NonCancellable) {
                                    dao.update(
                                        assistant.copy(
                                            text = responseText.toString(),
                                            thinkingContent = thinkingText.toString().takeIf { it.isNotEmpty() },
                                            thinkingDurationMs = thinkingDurationMs,
                                            status = MessageStatus.SENT,
                                            errorMessage = null,
                                        ).toEntity(),
                                    )
                                    conversationDao.touch(assistant.conversationId, System.currentTimeMillis())
                                }
                                generationCompleted = true
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (generationCompleted) {
                        // The answer is already terminal; keep the SENT row even if the parent
                        // scope is cancelled while the collector is unwinding.
                    } else if (stopRequestedFor == assistant.id) {
                        if (thinkingDurationMs == null && thinkingText.isNotEmpty()) {
                            thinkingDurationMs = System.currentTimeMillis() - thinkingStart
                        }
                        withContext(NonCancellable + Dispatchers.IO) {
                            val current = dao.getById(assistant.id)?.toDomain() ?: assistant
                            dao.update(
                                current.copy(
                                    text = responseText.toString(),
                                    thinkingContent = thinkingText.toString().takeIf { it.isNotEmpty() },
                                    thinkingDurationMs = thinkingDurationMs,
                                    status = MessageStatus.INTERRUPTED,
                                    errorMessage = "已停止生成，可点击重试",
                                ).toEntity(),
                            )
                            conversationDao.touch(assistant.conversationId, System.currentTimeMillis())
                        }
                    } else {
                        throw cancelled
                    }
                } catch (exception: ChatClientException) {
                    val interrupted = exception.kind == ChatErrorKind.STREAM_INTERRUPTED
                    if (thinkingDurationMs == null && thinkingText.isNotEmpty()) {
                        thinkingDurationMs = System.currentTimeMillis() - thinkingStart
                    }
                    withContext(NonCancellable + Dispatchers.IO) {
                        val current = dao.getById(assistant.id)?.toDomain() ?: assistant
                        dao.update(
                            current.copy(
                                text = responseText.toString(),
                                thinkingContent = thinkingText.toString().takeIf { it.isNotEmpty() },
                                thinkingDurationMs = thinkingDurationMs,
                                status = if (interrupted) MessageStatus.INTERRUPTED else MessageStatus.FAILED,
                                errorMessage = exception.message,
                            ).toEntity(),
                        )
                        conversationDao.touch(assistant.conversationId, System.currentTimeMillis())
                    }
                } catch (exception: Exception) {
                    if (thinkingDurationMs == null && thinkingText.isNotEmpty()) {
                        thinkingDurationMs = System.currentTimeMillis() - thinkingStart
                    }
                    withContext(NonCancellable + Dispatchers.IO) {
                        val current = dao.getById(assistant.id)?.toDomain() ?: assistant
                        dao.update(
                            current.copy(
                                text = responseText.toString(),
                                thinkingContent = thinkingText.toString().takeIf { it.isNotEmpty() },
                                thinkingDurationMs = thinkingDurationMs,
                                status = MessageStatus.FAILED,
                                errorMessage = exception.message ?: "发送失败",
                            ).toEntity(),
                        )
                        conversationDao.touch(assistant.conversationId, System.currentTimeMillis())
                    }
                } finally {
                    synchronized(activeRequestLock) {
                        if (activeAssistantId == assistant.id) {
                            activeAssistantId = null
                            activeConversationId = null
                            activeJob = null
                            stopRequestedFor = null
                        }
                    }
                }
            }
            activeJob = child
            // Publish the handle before starting so stopGeneration() can always cancel this
            // request, including when the provider completes immediately.
            child.start()
        }
        child.join()
        return withContext(Dispatchers.IO) {
            dao.getById(assistant.id)?.toDomain() ?: assistant
        }
    }

    private fun ChatMessage.throwIfUnsuccessful() {
        if (status == MessageStatus.SENT) return
        val kind = when (status) {
            MessageStatus.INTERRUPTED -> ChatErrorKind.STREAM_INTERRUPTED
            else -> ChatErrorKind.PROVIDER
        }
        throw ChatClientException(
            kind = kind,
            message = errorMessage ?: "发送失败，请点击重试",
            requestWasPersisted = true,
        )
    }

    private fun ChatClientException.asPersistedRequestFailure(): ChatClientException {
        if (requestWasPersisted) return this
        return ChatClientException(
            kind = kind,
            message = message,
            statusCode = statusCode,
            cause = this,
            requestWasPersisted = true,
        )
    }

    private suspend fun deleteUnreferencedMessageImages(messages: List<ChatMessage>) {
        val candidates = messages.asSequence()
            .flatMap { it.imagePaths.asSequence() }
            .distinct()
            .toSet()
        if (candidates.isEmpty()) return
        val stillReferenced = withContext(Dispatchers.IO) {
            dao.getAll().asSequence()
                .map { it.toDomain() }
                .flatMap { it.imagePaths.asSequence() }
                .filter { it in candidates }
                .toSet()
        }
        (candidates - stillReferenced).forEach { path ->
            runCatching { imageFileStore.delete(path) }
        }
    }

    private fun ChatMessage.toRequestMessage() = ChatRequestMessage(
        role = role,
        text = text,
        imagePaths = imagePaths,
    )

    /** Keeps recent context bounded so long chats remain responsive and fit provider limits. */
    private fun limitContext(messages: List<ChatRequestMessage>): List<ChatRequestMessage> {
        var remaining = MAX_CONTEXT_CHARS
        val kept = ArrayDeque<ChatRequestMessage>()
        messages.asReversed().forEach { message ->
            val cost = message.text.length + message.imagePaths.size * IMAGE_CONTEXT_COST
            if (kept.isEmpty() || remaining - cost >= 0) {
                kept.addFirst(message)
                remaining -= cost
            }
        }
        return kept.toList()
    }

    private fun buildWebSearchPrompt(
        query: String,
        results: List<com.example.aichat.data.network.WebSearchResult>,
    ): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val formattedResults = results.mapIndexed { index, r ->
            "[来源 ${index + 1}] ${r.title}\n网址: ${r.url}\n摘要: ${r.snippet}"
        }.joinToString("\n\n")

        return """
        [系统提示：互联网实时检索结果已就绪]
        当前基准日期: $dateStr
        检索关键词: $query

        以下为互联网最新实时检索结果：
        $formattedResults

        【重要回答准则】
        1. 请优先依据上述最新网络检索事实回答用户的问题，确保人名、职务、时间、数据等时效性信息准确无误。
        2. 若用户询问当前/最新的客观事实，严禁使用模型过期的预训练知识进行臆测或捏造；以检索到的最新事实为准。
        3. 回答请自然流畅，并在引用关键事实处适度注明参考来源编号（如 [来源 1]）。
        """.trimIndent()
    }

    private companion object {
        const val STREAM_PERSIST_INTERVAL_MS = 120L
        const val STREAM_PERSIST_MIN_DELTA_CHARS = 512
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_CONTEXT_CHARS = 48_000
        const val IMAGE_CONTEXT_COST = 2_000
    }
}
