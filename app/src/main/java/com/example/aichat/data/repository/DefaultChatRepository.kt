package com.example.aichat.data.repository

import com.example.aichat.data.model.*
import com.example.aichat.data.local.KnowledgeStore
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
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
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
    private val context: android.content.Context? = null,
) : ChatRepository {
    private val dao: ChatMessageDao = database.chatMessageDao()
    private val conversationDao: ChatConversationDao = database.chatConversationDao()
    private val personaDao: com.example.aichat.data.local.ChatPersonaDao = database.chatPersonaDao()
    private val profileDao: com.example.aichat.data.local.ProviderProfileDao = database.providerProfileDao()

    private val knowledge = KnowledgeStore(database)
    override fun observeKnowledgeCards() = knowledge.cards
    override fun observeBranchSelections() = knowledge.branches
    override suspend fun saveKnowledgeCard(card: KnowledgeCard) = knowledge.save(card)
    override suspend fun deleteKnowledgeCard(id: String) = knowledge.deleteCard(id)
    override suspend fun selectBranch(conversationId: String, requestId: String, assistantId: String) = knowledge.select(conversationId, requestId, assistantId)
    override suspend fun getContextRecord(id: String) = knowledge.record(id)
    override suspend fun getKnowledgeBackup() = knowledge.backup()
    override suspend fun detachConversationCards(conversationId: String) = knowledge.detachCards(conversationId)
    override suspend fun restoreKnowledgeData(conversations: List<ChatConversationEntity>, messages: List<ChatMessageEntity>, knowledge: KnowledgeBackup) {
        database.withTransaction { restoreBackupData(conversations, messages); this@DefaultChatRepository.knowledge.restore(knowledge) }
    }

    private suspend fun plan(conversationId: String, prior: List<ChatMessage>, current: ChatRequestMessage,
        model: String, options: ContextOptions, previous: ContextRecord? = null): ContextPlan {
        val conversation = conversationDao.getById(conversationId)
        val persona = conversation?.personaId?.let { personaDao.getPersonaById(it) }
        val selections = previous?.entries?.mapNotNull { entry -> entry.assistantId?.let { id ->
            prior.firstOrNull { it.id == id }?.requestId?.let { it to id }
        } }?.toMap() ?: knowledge.selections(conversationId)
        val exclusions = previous?.entries?.filter { it.reason == "手动排除" }?.map { it.userId }?.toSet() ?: options.excludedUserIds
        return ContextPlanner.build(prior, current, model, previous?.systemPrompt ?: persona?.systemPrompt.orEmpty(),
            conversation?.contextWindowLimit ?: 8, selections, options.copy(excludedUserIds = exclusions))
    }

    override suspend fun previewContext(conversationId: String, text: String, images: List<String>, options: ContextOptions): ContextPlan {
        val (config, _) = readProviderConfigForConversation(conversationId)
        return plan(conversationId, dao.getForConversation(conversationId).map { it.toDomain() },
            ChatRequestMessage(MessageRole.USER, ContextPlanner.withCards(text, options.cards).trim(), images), config.model, options)
    }

    private suspend fun savePlan(plan: ContextPlan, user: ChatMessage, assistant: ChatMessage,
        query: String? = null, search: String? = null, searchEnabled: Boolean = query != null) {
        knowledge.record(ContextRecord(assistantMessageId = assistant.id, conversationId = user.conversationId,
            userMessageId = user.id, model = plan.model, systemPrompt = plan.systemPrompt, entries = plan.entries,
            cards = plan.cards, searchEnabled = searchEnabled, searchQuery = query, searchContext = search,
            characterCount = plan.characterCount, imageCount = plan.imageCount))
    }

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

    override val searchingConversation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private var searchJob: Job? = null

    override fun observeAnyWorking(): Flow<Boolean> = kotlinx.coroutines.flow.combine(dao.observeAnyGenerating(), searchingConversation) { generating, searching -> generating || searching != null }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeForConversation(normalizeConversationId(conversationId))
        .map { rows -> rows.map { it.toDomain() } }
        .conflate()
        .flowOn(Dispatchers.IO)

    override fun searchAllMessages(keyword: String): Flow<List<com.example.aichat.data.local.MessageSearchResultItem>> {
        val clean = keyword.trim()
        if (clean.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return dao.searchAllMessages(clean).flowOn(Dispatchers.IO)
    }

    override suspend fun searchMessagePage(keyword: String, filter: com.example.aichat.data.model.MessageSearchFilter,
        since: Long, cursor: com.example.aichat.data.model.MessageSearchCursor?, limit: Int): List<com.example.aichat.data.local.MessageSearchResultItem> {
        if (keyword.isBlank()) return emptyList()
        return dao.searchMessagePage(com.example.aichat.data.model.literalSearchPattern(keyword), filter.conversationId,
            filter.role, since, cursor?.createdAt, cursor?.id, limit.coerceIn(1, 101))
    }

    override fun observeAllPersonas(): Flow<List<com.example.aichat.data.model.ChatPersona>> =
        personaDao.getAllPersonas()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override suspend fun getPersona(id: String): com.example.aichat.data.model.ChatPersona? = withContext(Dispatchers.IO) {
        personaDao.getPersonaById(id)?.toDomain()
    }

    override suspend fun savePersona(persona: com.example.aichat.data.model.ChatPersona) = withContext(Dispatchers.IO) {
        personaDao.insertOrReplace(persona.toEntity())
    }

    override suspend fun deleteCustomPersona(id: String) = withContext(Dispatchers.IO) {
        personaDao.deleteCustomPersona(id)
    }

    override suspend fun setConversationPersona(conversationId: String, personaId: String?): Boolean =
        withContext(Dispatchers.IO) {
            conversationDao.setPersona(normalizeConversationId(conversationId), personaId, System.currentTimeMillis()) > 0
        }

    override fun observeAllProviderProfiles(): Flow<List<com.example.aichat.data.model.ProviderProfile>> =
        profileDao.getAllProfiles()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    override fun observeDefaultProviderProfile(): Flow<com.example.aichat.data.model.ProviderProfile?> =
        profileDao.observeDefaultProfile()
            .map { it?.toDomain() }
            .flowOn(Dispatchers.IO)

    override suspend fun saveProviderProfile(profile: com.example.aichat.data.model.ProviderProfile) = withContext(Dispatchers.IO) {
        profileDao.insertOrReplace(profile.toEntity())
    }

    override suspend fun switchActiveProviderProfile(id: String) = withContext(Dispatchers.IO) {
        profileDao.switchActiveProfile(id)
    }

    override suspend fun deleteProviderProfile(id: String) = withContext(Dispatchers.IO) {
        profileDao.deleteProfile(id)
    }

    override suspend fun setConversationProviderProfile(conversationId: String, profileId: String?): Boolean =
        withContext(Dispatchers.IO) {
            conversationDao.setProviderProfile(normalizeConversationId(conversationId), profileId, System.currentTimeMillis()) > 0
        }

    override suspend fun setConversationContextWindowLimit(conversationId: String, limit: Int): Boolean =
        withContext(Dispatchers.IO) {
            conversationDao.setContextWindowLimit(normalizeConversationId(conversationId), limit.coerceIn(0, 100), System.currentTimeMillis()) > 0
        }

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

    override suspend fun sendMessage(conversationId: String, text: String, imagePaths: List<String>, webSearch: Boolean): String =
        sendPlannedMessage(conversationId, text, imagePaths, webSearch, ContextOptions())

    override suspend fun sendPlannedMessage(
        conversationId: String, text: String, images: List<String>, webSearch: Boolean, options: ContextOptions,
    ): String = withRequestLock {
        val imagePaths = images
        val selectedConversationId = normalizeConversationId(conversationId)
        ensureConversation(selectedConversationId)
        val cleanText = ContextPlanner.withCards(text, options.cards).trim()
        require(cleanText.isNotEmpty() || imagePaths.isNotEmpty()) { "消息内容不能为空" }

        val (config, personaTemperature) = readProviderConfigForConversation(selectedConversationId)
        val conversation = conversationDao.getById(selectedConversationId)?.toDomain()
        client.validateConfig(config)
        if (imagePaths.isNotEmpty() && !config.visionEnabled) {
            throw ChatClientException(
                ChatErrorKind.MISSING_CONFIG,
                "请先在设置中开启图片支持",
            )
        }

        // Freeze choices before network work; invalid local budgets must not consume the draft.
        val initialPlan = plan(selectedConversationId, dao.getForConversation(selectedConversationId).map { it.toDomain() },
            ChatRequestMessage(MessageRole.USER, cleanText, imagePaths), config.model, options)
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
            webSearchResults = null,
        )

        // Persist immediately so user message and AI response bubble appear instantly on screen
        database.withTransaction {
            dao.insertAll(listOf(user.toEntity(), assistant.toEntity()))
            conversationDao.touch(selectedConversationId, now)
        }

        synchronized(activeRequestLock) {
            activeAssistantId = assistant.id
            activeConversationId = selectedConversationId
            stopRequestedFor = null
        }

        val previousQuery = initialPlan.entries.lastOrNull { it.reason == null }?.userId?.let { dao.getById(it)?.text }
        savePlan(initialPlan, user, assistant, searchEnabled = webSearch)
        var searchRequestText: String? = null

        val searchResults = if (webSearch && cleanText.isNotEmpty()) {
            try {
                searchForConversation(selectedConversationId, config, cleanText, previousQuery) { searchRequestText = it }
            } catch (cancelled: CancellationException) {
                dao.update(assistant.copy(status = MessageStatus.INTERRUPTED, errorMessage = "已停止").toEntity())
                throw cancelled
            } catch (failure: ChatClientException) {
                val failureStatus = if (failure.message == "已停止联网搜索") MessageStatus.INTERRUPTED else MessageStatus.FAILED
                dao.update(assistant.copy(status = failureStatus, errorMessage = failure.message).toEntity())
                throw failure.asPersistedRequestFailure()
            } catch (failure: Exception) {
                dao.update(assistant.copy(status = MessageStatus.FAILED, errorMessage = failure.message ?: "联网搜索失败").toEntity())
                throw ChatClientException(
                    kind = ChatErrorKind.PROVIDER,
                    message = failure.message ?: "联网搜索失败",
                    cause = failure,
                    requestWasPersisted = true,
                )
            }
        } else null

        if (searchResults != null) {
            dao.update(assistant.copy(webSearchResults = searchResults).toEntity())
        }

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

        val finalPlan = try {
            // Search may add evidence, so rebuild against the same selected history and choices.
            val frozenIds = initialPlan.entries.flatMap { listOfNotNull(it.userId, it.assistantId) }.toSet()
            val prior = dao.getForConversation(selectedConversationId).map { it.toDomain() }.filter { it.id in frozenIds }
            ContextPlanner.build(prior, userRequestMessage, config.model, initialPlan.systemPrompt,
                conversation?.contextWindowLimit ?: 8,
                prior.filter { it.role == MessageRole.ASSISTANT && it.requestId != null }.associate { it.requestId!! to it.id }, options)
        } catch (failure: Exception) {
            dao.update(assistant.copy(status = MessageStatus.FAILED, errorMessage = failure.message).toEntity())
            throw ChatClientException(ChatErrorKind.PROVIDER, failure.message ?: "上下文超限", cause = failure, requestWasPersisted = true)
        }
        savePlan(finalPlan, user, assistant, searchRequestText,
            if (searchResults != null) userRequestMessage.text.removePrefix(cleanText) else null)
        val history = finalPlan.messages

        val completed = try {
            runGenerationInChild(
                parentScope = this,
                config = config,
                history = history,
                assistant = if (searchResults != null) assistant.copy(webSearchResults = searchResults) else assistant,
                temperature = personaTemperature,
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
        val userIndex = all.indexOfFirst { it.id == user.id }
        if (user.imagePaths.isNotEmpty() && !config.visionEnabled) {
            throw ChatClientException(ChatErrorKind.MISSING_CONFIG, "请先在设置中开启图片支持")
        }
        val pending = target.copy(
            webSearchResults = null,
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
        synchronized(activeRequestLock) {
            activeAssistantId = pending.id
            activeConversationId = selectedConversationId
            stopRequestedFor = null
        }

        var searchRequestText: String? = null
        val previousRecord = knowledge.record(target.id)
        val previousQuestion = if (previousRecord != null) previousRecord.entries.lastOrNull { it.reason == null }?.userId?.let { dao.getById(it)?.text }
            else all.take(userIndex).lastOrNull { it.role == MessageRole.USER }?.text
        val refreshedResults = if (target.webSearchResults != null || previousRecord?.searchEnabled == true) {
            try {
                searchForConversation(selectedConversationId, config, user.text, previousQuestion) { searchRequestText = it }
            } catch (cancelled: CancellationException) {
                dao.update(pending.copy(status = MessageStatus.INTERRUPTED, errorMessage = "已停止").toEntity())
                throw cancelled
            } catch (failure: ChatClientException) {
                val failureStatus = if (failure.message == "已停止联网搜索") MessageStatus.INTERRUPTED else MessageStatus.FAILED
                dao.update(pending.copy(status = failureStatus, errorMessage = failure.message).toEntity())
                throw failure.asPersistedRequestFailure()
            } catch (failure: Exception) {
                dao.update(pending.copy(status = MessageStatus.FAILED, errorMessage = failure.message ?: "联网搜索失败").toEntity())
                throw ChatClientException(kind = ChatErrorKind.PROVIDER, message = failure.message ?: "联网搜索失败", cause = failure, requestWasPersisted = true)
            }
        } else null

        if (refreshedResults != null) {
            dao.update(pending.copy(webSearchResults = refreshedResults).toEntity())
        }

        val userRequestMessage = if (refreshedResults != null) {
            val searchContext = buildWebSearchPrompt(user.text, refreshedResults)
            ChatRequestMessage(
                role = MessageRole.USER,
                text = "${user.text}\n\n$searchContext",
                imagePaths = user.imagePaths,
            )
        } else {
            user.toRequestMessage()
        }
        val conversation = conversationDao.getById(selectedConversationId)?.toDomain()
        val persona = conversation?.personaId?.let { personaDao.getPersonaById(it)?.toDomain() }
        val previousPlan = knowledge.record(target.id)
        val requestPlan = try {
            plan(selectedConversationId, all.take(userIndex.coerceAtLeast(0)), userRequestMessage, config.model,
                ContextOptions(cards = previousPlan?.cards.orEmpty()), previousPlan)
        } catch (failure: Exception) {
            dao.update(pending.copy(status = MessageStatus.FAILED, errorMessage = failure.message).toEntity())
            throw ChatClientException(ChatErrorKind.PROVIDER, failure.message ?: "上下文超限", cause = failure, requestWasPersisted = true)
        }
        savePlan(requestPlan, user, pending, searchRequestText,
            if (refreshedResults != null) userRequestMessage.text.removePrefix(user.text) else null)
        val history = requestPlan.messages

        val completed = runGenerationInChild(this, config, history, if (refreshedResults != null) pending.copy(webSearchResults = refreshedResults) else pending, temperature = persona?.temperature)
        completed.throwIfUnsuccessful()
        pending.requestId?.let { knowledge.select(selectedConversationId, it, pending.id) }
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
        val (config, personaTemperature) = readProviderConfigForConversation(selectedConversationId)
        val conversation = conversationDao.getById(selectedConversationId)?.toDomain()
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
            webSearchResults = null,
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
        synchronized(activeRequestLock) {
            activeAssistantId = pending.id
            activeConversationId = selectedConversationId
            stopRequestedFor = null
        }

        var searchRequestText: String? = null
        val previousRecord = knowledge.record(target.id)
        val previousQuestion = if (previousRecord != null) previousRecord.entries.lastOrNull { it.reason == null }?.userId?.let { dao.getById(it)?.text }
            else all.take(userIndex).lastOrNull { it.role == MessageRole.USER }?.text
        val refreshedResults = if (target.webSearchResults != null || previousRecord?.searchEnabled == true) {
            try {
                searchForConversation(selectedConversationId, config, user.text, previousQuestion) { searchRequestText = it }
            } catch (cancelled: CancellationException) {
                dao.update(pending.copy(status = MessageStatus.INTERRUPTED, errorMessage = "已停止").toEntity())
                throw cancelled
            } catch (failure: ChatClientException) {
                val failureStatus = if (failure.message == "已停止联网搜索") MessageStatus.INTERRUPTED else MessageStatus.FAILED
                dao.update(pending.copy(status = failureStatus, errorMessage = failure.message).toEntity())
                throw failure.asPersistedRequestFailure()
            } catch (failure: Exception) {
                dao.update(pending.copy(status = MessageStatus.FAILED, errorMessage = failure.message ?: "联网搜索失败").toEntity())
                throw ChatClientException(kind = ChatErrorKind.PROVIDER, message = failure.message ?: "联网搜索失败", cause = failure, requestWasPersisted = true)
            }
        } else null

        if (refreshedResults != null) {
            dao.update(pending.copy(webSearchResults = refreshedResults).toEntity())
        }

        val userRequestMessage = if (refreshedResults != null) {
            val searchContext = buildWebSearchPrompt(user.text, refreshedResults)
            ChatRequestMessage(
                role = MessageRole.USER,
                text = "${user.text}\n\n$searchContext",
                imagePaths = user.imagePaths,
            )
        } else {
            user.toRequestMessage()
        }
        val persona = conversation?.personaId?.let { personaDao.getPersonaById(it)?.toDomain() }
        val previousPlan = knowledge.record(target.id)
        val requestPlan = try {
            plan(selectedConversationId, all.take(userIndex.coerceAtLeast(0)), userRequestMessage, config.model,
                ContextOptions(cards = previousPlan?.cards.orEmpty()), previousPlan)
        } catch (failure: Exception) {
            dao.update(pending.copy(status = MessageStatus.FAILED, errorMessage = failure.message).toEntity())
            throw ChatClientException(ChatErrorKind.PROVIDER, failure.message ?: "上下文超限", cause = failure, requestWasPersisted = true)
        }
        savePlan(requestPlan, user, pending, searchRequestText,
            if (refreshedResults != null) userRequestMessage.text.removePrefix(user.text) else null)
        val history = requestPlan.messages

        val completed = runGenerationInChild(this, config, history, if (refreshedResults != null) pending.copy(webSearchResults = refreshedResults) else pending, temperature = personaTemperature)
        completed.throwIfUnsuccessful()
        pending.requestId?.let { knowledge.select(selectedConversationId, it, pending.id) }
        pending.id
    }

    override suspend fun deleteMessage(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val target = dao.getById(messageId)?.toDomain() ?: return@withContext false
        if (target.imagePaths.isNotEmpty()) {
            target.imagePaths.forEach { path ->
                runCatching { imageFileStore.delete(path) }
            }
        }
        val deleted = database.withTransaction { knowledge.deleteMessage(messageId); dao.deleteById(messageId) > 0 }
        if (deleted) {
            conversationDao.touch(target.conversationId, System.currentTimeMillis())
        }
        deleted
    }

    override fun stopGeneration() {
        val jobToCancel = synchronized(activeRequestLock) {
            if (searchJob != null) searchJob else {
                val assistantId = activeAssistantId ?: return
                stopRequestedFor = assistantId
                activeJob
            }
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

    override suspend fun setConversationIcon(conversationId: String, icon: String?): Boolean =
        withContext(Dispatchers.IO) {
            require(icon == null || icon.length <= 100_000) { "图标过大，请重新选择" }
            conversationDao.setIcon(normalizeConversationId(conversationId), icon?.takeIf { it.isNotBlank() }) > 0
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

    override suspend fun reorderConversationsInGroup(orderedConversationIds: List<String>): Boolean {
        if (orderedConversationIds.isEmpty()) return true
        return withContext(Dispatchers.IO) {
            val orders = orderedConversationIds.mapIndexed { index, id ->
                normalizeConversationId(id) to (index + 1)
            }
            conversationDao.updateSortOrders(orders)
            true
        }
    }

    override suspend fun deleteConversation(conversationId: String) = deleteConversationConfigured(conversationId, false)
    override suspend fun deleteConversationConfigured(conversationId: String, keepCards: Boolean): Boolean {
        val id = normalizeConversationId(conversationId)
        awaitActiveRequestIfNeeded(id)
        requestMutex.lock()
        try {
            val removedMessages = withContext(Dispatchers.IO) {
                database.withTransaction {
                    if (conversationDao.getById(id) == null) return@withTransaction null
                    val oldMessages = dao.getForConversation(id).map { it.toDomain() }
                    // Keep this explicit because the v1 table could not have a conversation FK.
                    knowledge.clearConversation(id, deleteCards = false)
                    dao.deleteForConversation(id)
                    if (keepCards) knowledge.detachCards(id)
                    knowledge.clearConversation(id, deleteCards = !keepCards)
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
            configStore.saveContinuationDraft(id, null)
            deleteUnreferencedMessageImages(removedMessages)
            return true
        } finally {
            requestMutex.unlock()
        }
    }

    override suspend fun deleteConversations(conversationIds: Collection<String>) = deleteConversationsConfigured(conversationIds, false)
    override suspend fun deleteConversationsConfigured(ids: Collection<String>, keepCards: Boolean): Int {
        val conversationIds = ids
        val targetIds = conversationIds.map { normalizeConversationId(it) }.distinct()
        if (targetIds.isEmpty()) return 0
        targetIds.forEach { awaitActiveRequestIfNeeded(it) }
        requestMutex.lock()
        try {
            val (deletedCount, removedMessages) = withContext(Dispatchers.IO) {
                database.withTransaction {
                    val messages = dao.getForConversations(targetIds).map { it.toDomain() }
                    targetIds.forEach { if (keepCards) knowledge.detachCards(it); knowledge.clearConversation(it, deleteCards = !keepCards) }
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
            targetIds.forEach { configStore.saveContinuationDraft(it, null) }
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
                    knowledge.clearConversation(id, deleteCards = false)
                    dao.deleteForConversation(id)
                    conversationDao.touch(id, System.currentTimeMillis())
                    oldMessages
                }
            }
            configStore.saveContinuationDraft(id, null)
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

    private suspend fun readProviderConfigForConversation(conversationId: String): Pair<ProviderConfig, Float?> {
        val conversation = conversationDao.getById(conversationId)?.toDomain()
        var baseConfig = readProviderConfig()
        var temperature: Float? = null

        val profileId = conversation?.providerProfileId
        if (!profileId.isNullOrBlank()) {
            val profile = profileDao.getProfileById(profileId)?.toDomain()
            require(profile != null) { "绑定的服务商配置已删除，请重新选择服务商" }
            if (profile != null) {
                val profileKey = if (context != null) ApiKeyStore(context, namespace = profile.id).read() else null
                baseConfig = baseConfig.copy(
                    baseUrl = profile.baseUrl,
                    model = profile.defaultModel,
                    apiKey = profileKey ?: baseConfig.apiKey,
                    visionEnabled = profile.visionEnabled,
                )
            }
        }

        val personaId = conversation?.personaId
        if (!personaId.isNullOrBlank()) {
            val persona = personaDao.getPersonaById(personaId)?.toDomain()
            if (persona != null) {
                temperature = persona.temperature
                if (!persona.preferredModel.isNullOrBlank()) {
                    baseConfig = baseConfig.copy(model = persona.preferredModel)
                }
            }
        }

        return baseConfig to temperature
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
            if (searchingConversation.value == conversationId) {
                searchJob
            } else if (activeConversationId == conversationId) {
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

    private suspend fun searchForConversation(id: String, config: ProviderConfig, query: String, previousQuery: String?, onRequestText: (String) -> Unit): List<com.example.aichat.data.network.WebSearchResult> = coroutineScope {
        val child = async(Dispatchers.IO, start = CoroutineStart.LAZY) { webSearchClient.search(query, config, previousQuery = previousQuery, onRequestText = onRequestText) }
        synchronized(activeRequestLock) { searchJob = child; searchingConversation.value = id }
        try {
            child.start()
            child.await()
        } catch (cancelled: CancellationException) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            throw ChatClientException(ChatErrorKind.INVALID_REQUEST, "已停止联网搜索")
        } finally {
            synchronized(activeRequestLock) { if (searchJob === child) { searchJob = null; searchingConversation.value = null } }
        }
    }

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
        temperature: Float? = null,
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
                val generationStart = System.currentTimeMillis()
                var thinkingDurationMs: Long? = null
                var lastPersistedLength = 0
                var lastPersistedThinkingLength = 0
                var lastPersistedAt = 0L
                var generationCompleted = false
                var promptTokens: Int? = null
                var completionTokens: Int? = null
                var totalTokens: Int? = null

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
                            promptTokens = promptTokens,
                            completionTokens = completionTokens,
                            totalTokens = totalTokens,
                        ).toEntity(),
                    )
                    lastPersistedLength = responseText.length
                    lastPersistedThinkingLength = thinkingText.length
                    lastPersistedAt = now
                }

                try {
                    dao.update(assistant.copy(status = MessageStatus.STREAMING).toEntity())
                    client.streamChatWithModelTracking(config, history, temperature) { attemptedModel ->
                        knowledge.record(assistant.id)?.let { if (it.model != attemptedModel) knowledge.record(it.copy(model = attemptedModel)) }
                    }.collect { event ->
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

                            is ChatStreamEvent.Usage -> {
                                promptTokens = event.promptTokens
                                completionTokens = event.completionTokens
                                totalTokens = event.totalTokens
                            }

                            ChatStreamEvent.Done -> {
                                val durationMs = System.currentTimeMillis() - generationStart
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
                                            promptTokens = promptTokens,
                                            completionTokens = completionTokens,
                                            totalTokens = totalTokens,
                                            generationDurationMs = durationMs,
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
                        val durationMs = System.currentTimeMillis() - generationStart
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
                                    promptTokens = promptTokens,
                                    completionTokens = completionTokens,
                                    totalTokens = totalTokens,
                                    generationDurationMs = durationMs,
                                ).toEntity(),
                            )
                            conversationDao.touch(assistant.conversationId, System.currentTimeMillis())
                        }
                    } else {
                        throw cancelled
                    }
                } catch (exception: ChatClientException) {
                    val interrupted = exception.kind == ChatErrorKind.STREAM_INTERRUPTED
                    val durationMs = System.currentTimeMillis() - generationStart
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
                                promptTokens = promptTokens,
                                completionTokens = completionTokens,
                                totalTokens = totalTokens,
                                generationDurationMs = durationMs,
                            ).toEntity(),
                        )
                        conversationDao.touch(assistant.conversationId, System.currentTimeMillis())
                    }
                } catch (exception: Exception) {
                    val durationMs = System.currentTimeMillis() - generationStart
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
                                promptTokens = promptTokens,
                                completionTokens = completionTokens,
                                totalTokens = totalTokens,
                                generationDurationMs = durationMs,
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
    ): String = com.example.aichat.data.network.buildSearchContext(query, results, java.time.Clock.systemDefaultZone())

    private companion object {
        const val STREAM_PERSIST_INTERVAL_MS = 120L
        const val STREAM_PERSIST_MIN_DELTA_CHARS = 512
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_CONTEXT_CHARS = 48_000
        const val IMAGE_CONTEXT_COST = 2_000
    }
}
