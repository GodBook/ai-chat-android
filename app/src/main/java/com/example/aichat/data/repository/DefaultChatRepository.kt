package com.example.aichat.data.repository

import com.example.aichat.data.local.ApiKeyStore
import com.example.aichat.data.local.ChatConversationDao
import com.example.aichat.data.local.ChatMessageDao
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
import com.example.aichat.data.model.toDomain
import com.example.aichat.data.model.toEntity
import com.example.aichat.data.network.ChatClientException
import com.example.aichat.data.network.ChatErrorKind
import com.example.aichat.data.network.ChatStreamEvent
import com.example.aichat.data.network.OpenAiCompatibleClient
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import java.util.UUID

class DefaultChatRepository(
    private val database: ChatDatabase,
    private val configStore: ConfigStore,
    private val apiKeyStore: ApiKeyStore,
    private val imageFileStore: ImageFileStore,
    private val client: OpenAiCompatibleClient,
) : ChatRepository {
    private val dao: ChatMessageDao = database.chatMessageDao()
    private val conversationDao: ChatConversationDao = database.chatConversationDao()

    override val conversations: Flow<List<ChatConversation>> = conversationDao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    override val messages: Flow<List<ChatMessage>> = observeMessages(DEFAULT_CONVERSATION_ID)

    override fun observeAllMessages(): Flow<List<ChatMessage>> = dao.observeAll()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeForConversation(normalizeConversationId(conversationId))
        .map { rows -> rows.map { it.toDomain() } }
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

    override suspend fun sendMessage(text: String, imagePaths: List<String>): String =
        sendMessage(DEFAULT_CONVERSATION_ID, text, imagePaths)

    override suspend fun sendMessage(
        conversationId: String,
        text: String,
        imagePaths: List<String>,
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
        )
        val history = database.withTransaction {
            val requestHistory = dao.getForConversation(selectedConversationId).map { it.toDomain() }
                .filter { it.status == MessageStatus.SENT }
                .map { it.toRequestMessage() }
                .plus(user.toRequestMessage())
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
        val history = all.take(userIndex.coerceAtLeast(0))
            .filter { it.status == MessageStatus.SENT }
            .map { it.toRequestMessage() }
            .plus(user.toRequestMessage())
        val pending = target.copy(
            text = "",
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

    override suspend fun createConversation(title: String): ChatConversation {
        val now = System.currentTimeMillis()
        val conversation = ChatConversation(
            id = UUID.randomUUID().toString(),
            title = normalizeTitle(title),
            createdAt = now,
            updatedAt = now,
        )
        withContext(Dispatchers.IO) { conversationDao.insert(conversation.toEntity()) }
        return conversation
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
            child = parentScope.launch(Dispatchers.IO) {
                try {
                    var responseText = ""
                    dao.update(assistant.copy(status = MessageStatus.STREAMING).toEntity())
                    client.streamChat(config, history).collect { event ->
                        when (event) {
                            is ChatStreamEvent.Delta -> {
                                responseText += event.text
                                dao.update(
                                    assistant.copy(
                                        text = responseText,
                                        status = MessageStatus.STREAMING,
                                        errorMessage = null,
                                    ).toEntity(),
                                )
                            }

                            ChatStreamEvent.Done -> {
                                dao.update(
                                    assistant.copy(
                                        text = responseText,
                                        status = MessageStatus.SENT,
                                        errorMessage = null,
                                    ).toEntity(),
                                )
                                conversationDao.touch(assistant.conversationId, System.currentTimeMillis())
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (stopRequestedFor == assistant.id) {
                        withContext(NonCancellable + Dispatchers.IO) {
                            val current = dao.getById(assistant.id)?.toDomain() ?: assistant
                            dao.update(
                                current.copy(
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
                    withContext(NonCancellable + Dispatchers.IO) {
                        val current = dao.getById(assistant.id)?.toDomain() ?: assistant
                        dao.update(
                            current.copy(
                                status = if (interrupted) MessageStatus.INTERRUPTED else MessageStatus.FAILED,
                                errorMessage = exception.message,
                            ).toEntity(),
                        )
                        conversationDao.touch(assistant.conversationId, System.currentTimeMillis())
                    }
                } catch (exception: Exception) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val current = dao.getById(assistant.id)?.toDomain() ?: assistant
                        dao.update(
                            current.copy(
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
}
