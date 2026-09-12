package com.example.aichat.ui

import android.content.Context
import android.net.Uri
import android.content.Intent
import com.example.aichat.data.attachment.AttachmentComposerState
import com.example.aichat.data.attachment.DocumentAttachment
import com.example.aichat.data.attachment.DocumentImporter
import com.example.aichat.data.attachment.DocumentText
import com.example.aichat.data.attachment.IncomingShare
import com.example.aichat.data.attachment.SharePreview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aichat.AppContainer
import com.example.aichat.BuildConfig
import com.example.aichat.data.local.ApiKeyStore
import com.example.aichat.data.local.BackupRestoreManager
import com.example.aichat.data.local.ConfigStore
import com.example.aichat.data.local.ImageFileStore
import com.example.aichat.data.repository.AppStorageStats
import com.example.aichat.ui.export.ChatImageExporter
import com.example.aichat.ui.export.ChatMarkdownExporter
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.ChatPersona
import com.example.aichat.data.model.ProviderProfile
import com.example.aichat.data.local.MessageSearchResultItem
import com.example.aichat.ui.tts.TtsManager
import com.example.aichat.ui.tts.TtsPlaybackState
import com.example.aichat.data.model.DEFAULT_CONVERSATION_TITLE

import com.example.aichat.data.model.MAX_SCREENSHOT_PROMPT_LENGTH
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import com.example.aichat.data.model.ProviderConfig
import com.example.aichat.data.model.DEFAULT_OVERLAY_BACKGROUND_COLOR
import com.example.aichat.data.model.DEFAULT_OVERLAY_GLASS_ENABLED
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_TRIGGER
import com.example.aichat.data.model.ModelPreset
import com.example.aichat.data.model.ScreenshotTrigger
import com.example.aichat.data.model.normalizeOverlayBackgroundColor
import com.example.aichat.data.network.ChatClientException
import com.example.aichat.data.repository.ChatRepository
import com.example.aichat.data.update.AppUpdateException
import com.example.aichat.data.update.AppUpdateInfo
import com.example.aichat.data.update.AppUpdateManager
import com.example.aichat.data.update.InstallPreparation
import com.example.aichat.data.update.UpdateCheckResult
import com.example.aichat.data.update.UpdateConfigStore
import com.example.aichat.data.update.UpdateDownloadProgress
import com.example.aichat.data.update.UpdateDownloadState
import com.example.aichat.data.update.UpdateManifestParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.aichat.data.model.resolveMessageBranches
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val info: AppUpdateInfo) : UpdateUiState
    data class UpToDate(
        val currentVersionCode: Long,
        val latestVersionName: String,
    ) : UpdateUiState

    data class Downloading(val progress: UpdateDownloadProgress? = null) : UpdateUiState
    data class Ready(val apk: File) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

sealed interface ProbeUiState {
    data object Idle : ProbeUiState
    data object Probing : ProbeUiState
    data class Success(val latencyMs: Long, val message: String) : ProbeUiState
    data class Failure(val message: String) : ProbeUiState
}

sealed interface BackupRestoreUiState {
    data object Idle : BackupRestoreUiState
    data object Processing : BackupRestoreUiState
    data class Success(val message: String) : BackupRestoreUiState
    data class Error(val message: String) : BackupRestoreUiState
}

data class MainUiState(
    val conversations: List<ChatConversation> = emptyList(),
    val conversationPreviews: Map<String, ChatMessage> = emptyMap(),
    val selectedConversationId: String? = null,
    val selectedConversationTitle: String = DEFAULT_CONVERSATION_TITLE,
    val messages: List<ChatMessage> = emptyList(),
    val config: ProviderConfig = ProviderConfig(),
    val hasApiKey: Boolean = false,
    val selectedImagePaths: List<String> = emptyList(),
    val attachments: AttachmentComposerState = AttachmentComposerState(),
    val incomingShare: SharePreview? = null,
    val sharedDraft: com.example.aichat.data.attachment.SharedDraft? = null,
    val isWorking: Boolean = false,
    val isAnyWorking: Boolean = false,
    val message: String? = null,
    /** Text to put back into the composer after a request could not be sent. */
    val draftToRestore: String? = null,
    val updateManifestUrl: String = "",
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val collapsedGroups: Set<String> = emptySet(),
    val storageStats: AppStorageStats? = null,
    val probeState: ProbeUiState = ProbeUiState.Idle,
    val backupRestoreState: BackupRestoreUiState = BackupRestoreUiState.Idle,
    val webSearchActive: Boolean = false,
    val isTemporary: Boolean = false,
    val isSearching: Boolean = false,
    val personas: List<ChatPersona> = emptyList(),
    val providerProfiles: List<ProviderProfile> = emptyList(),
    val activePersona: ChatPersona? = null,
    val activeProviderProfile: ProviderProfile? = null,
    val deepSearchQuery: String = "",
    val deepSearchResults: List<MessageSearchResultItem> = emptyList(),
    val isDeepSearching: Boolean = false,
    val searchFilter: com.example.aichat.data.model.MessageSearchFilter = com.example.aichat.data.model.MessageSearchFilter(),
    val searchHasMore: Boolean = false,
    val searchError: String? = null,
    val highlightedMessageId: String? = null,
    val ttsPlaybackState: TtsPlaybackState = TtsPlaybackState(),
)


private data class ConversationSnapshot(
    val conversations: List<ChatConversation>,
    val previews: List<ChatMessage>,
    val selectedId: String?,
    val selectedMessages: List<ChatMessage>,
    val isAnyWorking: Boolean,
)

private data class ConversationSelection(
    val conversations: List<ChatConversation>,
    val selectedId: String?,
)

private data class SettingsSnapshot(
    val config: ProviderConfig,
    val updateManifestUrl: String,
    val collapsedGroups: Set<String>,
)

private data class ComposerSnapshot(
    val images: List<String>,
    val message: String?,
    val draft: String?,
    val webSearchActive: Boolean?,
)

class MainViewModel(
    private val repository: ChatRepository,
    private val configStore: ConfigStore,
    private val apiKeyStore: ApiKeyStore,
    private val imageFileStore: ImageFileStore,
    private val updateConfigStore: UpdateConfigStore,
    private val updateManager: AppUpdateManager,
    private val client: com.example.aichat.data.network.OpenAiCompatibleClient,
    private val webSearchClient: com.example.aichat.data.network.WebSearchClient,
    private val ttsManager: TtsManager,
) : ViewModel() {
    private val temporary = com.example.aichat.data.repository.TemporaryChatSession(viewModelScope, client::streamChat) { query, config -> webSearchClient.search(query, config) }
    private var temporaryPreparation: kotlinx.coroutines.Job? = null
    private var deepSearchJob: kotlinx.coroutines.Job? = null
    private val selectedConversationId = MutableStateFlow<String?>(null)
    private val selectedImagePaths = MutableStateFlow<List<String>>(emptyList())
    private val attachments = MutableStateFlow(AttachmentComposerState())
    private val incomingShare = MutableStateFlow<SharePreview?>(null)
    private val sharedDraft = MutableStateFlow<com.example.aichat.data.attachment.SharedDraft?>(null)
    private var documentImportGeneration = 0L
    private var documentImportJob: kotlinx.coroutines.Job? = null
    private var shareImportJob: kotlinx.coroutines.Job? = null
    private val transientMessage = MutableStateFlow<String?>(null)
    private val draftToRestore = MutableStateFlow<String?>(null)
    private val apiKeyAvailable = MutableStateFlow(runCatching { apiKeyStore.hasKey() }.getOrDefault(false))
    private val updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    private val probeState = MutableStateFlow<ProbeUiState>(ProbeUiState.Idle)
    private val storageStats = MutableStateFlow<AppStorageStats?>(null)
    private val backupRestoreState = MutableStateFlow<BackupRestoreUiState>(BackupRestoreUiState.Idle)
    private val webSearchActive = MutableStateFlow<Boolean?>(null)
    private val selectedBranches = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val conversationGeneration = AtomicLong(0)
    private val deepSearchQuery = MutableStateFlow("")
    private val deepSearchResults = MutableStateFlow<List<MessageSearchResultItem>>(emptyList())
    private val isDeepSearching = MutableStateFlow(false)
    private val searchFilter = MutableStateFlow(com.example.aichat.data.model.MessageSearchFilter())
    private val searchHasMore = MutableStateFlow(false)
    private val searchError = MutableStateFlow<String?>(null)
    private var searchGeneration = 0L
    private var searchSince = 0L
    private val highlightedMessageId = MutableStateFlow<String?>(null)


    private val conversationSelection: Flow<ConversationSelection> = combine(
        repository.conversations,
        selectedConversationId,
    ) { conversations, selectedId ->
        val validSelectedId = selectedId?.takeIf { candidate -> conversations.any { it.id == candidate } }
            ?: conversations.firstOrNull()?.id
        ConversationSelection(conversations, validSelectedId)
    }.distinctUntilChanged()

    /**
     * Keep the high-frequency stream updates scoped to the selected chat. The
     * conversation list only needs one preview row per chat, and the global
     * working flag is a cheap EXISTS query.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val conversationSnapshot: Flow<ConversationSnapshot> = conversationSelection.flatMapLatest { selection ->
        val selectedMessages = selection.selectedId?.let { repository.observeMessages(it) }
            ?: flowOf(emptyList())
        combine(
            flowOf(selection.conversations),
            repository.observeConversationPreviews(),
            selectedMessages,
            repository.observeAnyWorking(),
            selectedBranches,
        ) { conversations, previews, messages, anyWorking, branches ->
            val resolvedMessages = resolveMessageBranches(messages, branches)
            ConversationSnapshot(
                conversations = conversations,
                previews = previews,
                selectedId = selection.selectedId,
                selectedMessages = resolvedMessages,
                isAnyWorking = anyWorking,
            )
        }
    }

    private val settingsSnapshot: Flow<SettingsSnapshot> = combine(
        configStore.config,
        updateConfigStore.manifestUrl,
        configStore.collapsedGroups,
    ) { config, updateUrl, collapsed -> SettingsSnapshot(config, updateUrl, collapsed) }

    private val composerSnapshot: Flow<ComposerSnapshot> = combine(
        selectedImagePaths,
        transientMessage,
        draftToRestore,
        webSearchActive,
    ) { images, message, draft, search -> ComposerSnapshot(images, message, draft, search) }

    private val baseUiState: Flow<MainUiState> = combine(
        conversationSnapshot,
        settingsSnapshot,
        composerSnapshot,
        repository.searchingConversation,
    ) { conversationsState, settings, composer, searchingId ->
        val previews = conversationsState.previews.associateBy { it.conversationId }
        val isWebSearch = composer.webSearchActive ?: settings.config.defaultWebSearchEnabled
        val isSearching = searchingId != null && searchingId == conversationsState.selectedId
        MainUiState(
            conversations = conversationsState.conversations,
            conversationPreviews = previews,
            selectedConversationId = conversationsState.selectedId,
            selectedConversationTitle = conversationsState.conversations
                .firstOrNull { it.id == conversationsState.selectedId }
                ?.title
                ?: DEFAULT_CONVERSATION_TITLE,
            messages = conversationsState.selectedMessages,
            config = settings.config,
            selectedImagePaths = composer.images,
            isWorking = conversationsState.selectedMessages.any { it.isGenerating() } || isSearching,
            isAnyWorking = conversationsState.isAnyWorking,
            message = composer.message,
            draftToRestore = composer.draft,
            updateManifestUrl = settings.updateManifestUrl.ifBlank { BuildConfig.UPDATE_MANIFEST_URL },
            collapsedGroups = settings.collapsedGroups,
            webSearchActive = isWebSearch,
            isSearching = isSearching,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        baseUiState,
        apiKeyAvailable,
        updateState,
        probeState,
        storageStats,
    ) { state, hasApiKey, update, probe, stats ->
        state.copy(
            hasApiKey = hasApiKey,
            updateState = update,
            probeState = probe,
            storageStats = stats,
        )
    }.combine(backupRestoreState) { state, backup ->
        state.copy(backupRestoreState = backup)
    }.combine(temporary.state) { state, temp ->
        if (temp.id == null) state else state.copy(
            isTemporary = true, selectedConversationId = temp.id, selectedConversationTitle = "临时对话",
            messages = temp.messages, selectedImagePaths = temp.images, isWorking = temp.working,
            isAnyWorking = state.isAnyWorking || temp.working, webSearchActive = temp.searchEnabled,
            isSearching = temp.searching,
            draftToRestore = null,
        )
    }.combine(repository.observeAllPersonas()) { state, personas ->
        state.copy(personas = personas)
    }.combine(repository.observeAllProviderProfiles()) { state, profiles ->
        state.copy(providerProfiles = profiles)
    }.combine(deepSearchQuery) { state, query ->
        state.copy(deepSearchQuery = query)
    }.combine(deepSearchResults) { state, results ->
        state.copy(deepSearchResults = results)
    }.combine(isDeepSearching) { state, searching ->
        state.copy(isDeepSearching = searching)
    }.combine(searchFilter) { state, filter -> state.copy(searchFilter = filter)
    }.combine(searchHasMore) { state, more -> state.copy(searchHasMore = more)
    }.combine(searchError) { state, error -> state.copy(searchError = error)
    }.combine(highlightedMessageId) { state, hl ->
        state.copy(highlightedMessageId = hl)
    }.combine(ttsManager.playbackState) { state, tts ->
        state.copy(ttsPlaybackState = tts)
    }.combine(conversationSelection) { state, selection ->
        val selectedConv = selection.conversations.firstOrNull { it.id == selection.selectedId }
        val persona = selectedConv?.personaId?.let { id -> state.personas.firstOrNull { it.id == id } }
        val profile = selectedConv?.providerProfileId?.let { id -> state.providerProfiles.firstOrNull { it.id == id } }
        state.copy(activePersona = persona, activeProviderProfile = profile)
    }.combine(attachments) { state, files ->
        state.copy(attachments = files)
    }.combine(incomingShare) { state, share ->
        state.copy(incomingShare = share)
    }.combine(sharedDraft) { state, draft ->
        state.copy(sharedDraft = draft)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())


    init {
        loadStorageStats()
        viewModelScope.launch {
            repository.recoverInterruptedMessages()
            val existing = repository.conversations.first()
            if (existing.isEmpty()) repository.createConversation()
            if (selectedConversationId.value == null) selectedConversationId.value = repository.conversations.first().firstOrNull()?.id
        }
        viewModelScope.launch {
            repository.conversations.collect { conversations ->
                val current = selectedConversationId.value
                if (current == null || conversations.none { it.id == current }) {
                    selectedConversationId.value = conversations.firstOrNull()?.id
                }
            }
        }
    }

    fun clearMessage() {
        transientMessage.value = null
    }

    fun clearDraftRestore() {
        draftToRestore.value = null
    }

    fun selectConversation(id: String): Boolean {
        if (id == selectedConversationId.value) return true
        if (uiState.value.isAnyWorking) {
            transientMessage.value = "请先停止正在生成的回复"
            return false
        }
        conversationGeneration.incrementAndGet()
        discardComposerImages()
        draftToRestore.value = null
        highlightedMessageId.value = null
        selectedConversationId.value = id
        return true

    }

    fun createConversation(
        title: String = "新聊天",
        groupName: String? = null,
        onCreated: (() -> Unit)? = null,
    ) {
        if (uiState.value.isAnyWorking) {
            transientMessage.value = "请先停止正在生成的回复"
            return
        }
        viewModelScope.launch {
            try {
                val conversation = repository.createConversation(title, groupName)
                conversationGeneration.incrementAndGet()
                draftToRestore.value = null
                discardComposerImages()
                selectedConversationId.value = conversation.id
                onCreated?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun setConversationGroup(id: String, groupName: String?) {
        viewModelScope.launch {
            try {
                if (repository.updateConversationGroup(id, groupName) == null) {
                    transientMessage.value = "聊天不存在"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun setConversationsGroup(ids: Set<String>, groupName: String?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                repository.updateConversationsGroup(ids, groupName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun renameGroup(oldGroupName: String, newGroupName: String) {
        val cleanOld = oldGroupName.trim()
        val cleanNew = newGroupName.trim()
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == cleanNew) return
        viewModelScope.launch {
            try {
                repository.renameGroup(cleanOld, cleanNew)
                val current = configStore.collapsedGroups.first()
                if (cleanOld in current) {
                    configStore.updateCollapsedGroups(current - cleanOld + cleanNew)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun toggleGroupCollapsed(groupName: String) {
        viewModelScope.launch {
            try {
                val current = configStore.collapsedGroups.first()
                val updated = if (groupName in current) current - groupName else current + groupName
                configStore.updateCollapsedGroups(updated)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun updateThemeColor(themeColor: String) {
        viewModelScope.launch {
            try {
                configStore.updateThemeColor(themeColor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun setConversationIcon(id: String, icon: String?) {
        viewModelScope.launch {
            try {
                if (!repository.setConversationIcon(id, icon)) transientMessage.value = "聊天不存在"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun renameConversation(id: String, title: String) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            transientMessage.value = "聊天名称不能为空"
            return
        }
        viewModelScope.launch {
            try {
                if (repository.renameConversation(id, cleanTitle) == null) {
                    transientMessage.value = "聊天不存在"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun deleteConversation(id: String) {
        val deletingSelected = id == selectedConversationId.value
        val generation = if (deletingSelected) {
            conversationGeneration.incrementAndGet()
        } else {
            conversationGeneration.get()
        }
        val draftImages = if (deletingSelected) selectedImagePaths.value else emptyList()
        if (deletingSelected) {
            selectedImagePaths.value = emptyList()
            draftToRestore.value = null
        }
        viewModelScope.launch {
            try {
                if (!repository.deleteConversation(id)) {
                    if (deletingSelected) restoreComposerImages(draftImages, id, generation)
                    transientMessage.value = "聊天不存在"
                    return@launch
                }
                deleteComposerImages(draftImages)
                if (deletingSelected && conversationGeneration.get() == generation) {
                    selectedConversationId.value = repository.conversations.first().firstOrNull()?.id
                }
                transientMessage.value = "聊天已删除"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (deletingSelected) restoreComposerImages(draftImages, id, generation)
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun togglePinConversation(id: String) {
        viewModelScope.launch {
            try {
                val conv = repository.getConversation(id) ?: return@launch
                val nextPinned = !conv.isPinned
                repository.setConversationPinned(id, nextPinned)
                transientMessage.value = if (nextPinned) "已置顶该对话" else "已取消置顶"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun reorderConversationsInGroup(orderedIds: List<String>) {
        if (orderedIds.size <= 1) return
        viewModelScope.launch {
            try {
                repository.reorderConversationsInGroup(orderedIds)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun deleteConversations(ids: Set<String>) {
        if (ids.isEmpty()) return
        val currentSelectedId = selectedConversationId.value
        val deletingSelected = currentSelectedId != null && currentSelectedId in ids
        val generation = if (deletingSelected) {
            conversationGeneration.incrementAndGet()
        } else {
            conversationGeneration.get()
        }
        val draftImages = if (deletingSelected) selectedImagePaths.value else emptyList()
        if (deletingSelected) {
            selectedImagePaths.value = emptyList()
            draftToRestore.value = null
        }
        viewModelScope.launch {
            try {
                val count = repository.deleteConversations(ids)
                if (count == 0) {
                    if (deletingSelected) {
                        restoreComposerImages(draftImages, currentSelectedId!!, generation)
                    }
                    transientMessage.value = "所选聊天不存在"
                    return@launch
                }
                deleteComposerImages(draftImages)
                if (deletingSelected && conversationGeneration.get() == generation) {
                    selectedConversationId.value = repository.conversations.first().firstOrNull()?.id
                }
                transientMessage.value = "已删除 $count 个会话"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (deletingSelected) {
                    restoreComposerImages(draftImages, currentSelectedId!!, generation)
                }
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    /** Builds an export off the UI thread, then lets the screen open the system share sheet. */
    fun exportConversation(id: String, onReady: (title: String, content: String) -> Unit) {
        viewModelScope.launch {
            try {
                val conversation = repository.getConversation(id)
                    ?: throw IllegalArgumentException("聊天不存在")
                val rawMessages = repository.observeMessages(id).first()
                val messages = resolveMessageBranches(rawMessages, selectedBranches.value)
                if (messages.isEmpty()) {
                    throw IllegalArgumentException("当前聊天还没有消息")
                }
                val content = withContext(Dispatchers.Default) {
                    ChatExportFormatter.format(conversation.title, messages)
                }
                onReady(conversation.title, content)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun exportConversations(ids: Set<String>, onReady: (title: String, content: String) -> Unit) {
        if (ids.isEmpty()) {
            transientMessage.value = "未选择任何会话"
            return
        }
        viewModelScope.launch {
            try {
                val targets = withContext(Dispatchers.IO) {
                    ids.mapNotNull { id ->
                        val conv = repository.getConversation(id) ?: return@mapNotNull null
                        val rawMsgs = repository.observeMessages(id).first()
                        val msgs = resolveMessageBranches(rawMsgs, selectedBranches.value)
                        conv to msgs
                    }
                }
                if (targets.isEmpty()) {
                    throw IllegalArgumentException("所选聊天不存在")
                }
                val content = withContext(Dispatchers.Default) {
                    ChatExportFormatter.formatMultiple(targets.map { it.first.title to it.second })
                }
                onReady("批量导出聊天记录（共${targets.size}个会话）", content)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun importImage(uri: Uri) {
        temporary.state.value.id?.let { id ->
            viewModelScope.launch {
                try { temporary.addImage(id, imageFileStore.importInMemory(uri)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { if (temporary.state.value.id == id) transientMessage.value = "无法读取所选图片" }
            }
            return
        }
        val conversationId = selectedConversationId.value
        val generation = conversationGeneration.get()
        viewModelScope.launch {
            try {
                val path = imageFileStore.import(uri)
                if (selectedConversationId.value == conversationId && conversationGeneration.get() == generation) {
                    selectedImagePaths.value += path
                } else {
                    runCatching { imageFileStore.delete(path) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.message ?: "无法读取图片"
            }
        }
    }

    fun removeSelectedImage(path: String) {
        if (temporary.state.value.id != null) { temporary.removeImage(path); return }
        selectedImagePaths.value = selectedImagePaths.value - path
        deleteComposerImages(listOf(path))
    }

    fun toggleWebSearch(active: Boolean? = null) {
        if (temporary.state.value.id != null) { temporary.toggleSearch(); return }
        val current = uiState.value.webSearchActive
        webSearchActive.value = active ?: !current
    }

    fun send(text: String) {
        if (attachments.value.importing) { transientMessage.value = "请等待文件解析完成"; return }
        val files = attachments.value.documents
        val outgoingText = DocumentText.compose(text, files)
        if (temporary.state.value.id != null) {
            prepareTemporaryRequest { config ->
                temporary.send(outgoingText, config)
                attachments.value = AttachmentComposerState()
            }
            return
        }
        if (uiState.value.isWorking) return
        val images = selectedImagePaths.value
        if (outgoingText.isBlank() && images.isEmpty()) {
            transientMessage.value = "请输入消息或选择图片"
            return
        }
        val isWebSearch = uiState.value.webSearchActive
        val generation = conversationGeneration.get()
        val conversationId = selectedConversationId.value
        draftToRestore.value = null
        selectedImagePaths.value = emptyList()
        attachments.value = AttachmentComposerState()
        viewModelScope.launch {
            var targetId = conversationId
            try {
                val resolvedTargetId = targetId ?: repository.createConversation().also {
                    targetId = it.id
                    selectedConversationId.value = it.id
                }.id
                repository.sendMessage(resolvedTargetId, outgoingText, images, webSearch = isWebSearch)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if ((failure as? ChatClientException)?.requestWasPersisted != true) {
                    if (conversationGeneration.get() == generation && selectedConversationId.value == targetId) {
                        selectedImagePaths.value = (images + selectedImagePaths.value).distinct()
                        draftToRestore.value = text
                        attachments.value = AttachmentComposerState(documents = files)
                    } else {
                        deleteComposerImages(images)
                    }
                }
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun stop() {
        if (temporary.state.value.id != null) { temporaryPreparation?.cancel(); temporary.stop() }
        else repository.stopGeneration()
    }

    fun startTemporaryConversation(onReady: () -> Unit) {
        if (uiState.value.isAnyWorking) { transientMessage.value = "请先停止正在生成的回复"; return }
        transientMessage.value = null
        temporaryPreparation?.cancel()
        clearDocuments()
        temporary.start()
        onReady()
    }

    fun closeTemporaryConversation() {
        if (temporary.state.value.id == null) return
        temporaryPreparation?.cancel(); clearDocuments(); temporary.close(); transientMessage.value = null
    }
    fun hasTemporaryConversation(): Boolean = temporary.state.value.id != null
    fun importTemporaryImage(uri: Uri, sessionId: String?) {
        if (sessionId != null && temporary.state.value.id == sessionId) importImage(uri)
    }

    private fun prepareTemporaryRequest(action: (ProviderConfig) -> Unit) {
        val id = temporary.state.value.id ?: return
        if (temporary.state.value.working || temporaryPreparation?.isActive == true) return
        temporaryPreparation = viewModelScope.launch {
            try {
                val config = configStore.read().copy(apiKey = apiKeyStore.read())
                if (temporary.state.value.id == id) action(config)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (temporary.state.value.id == id) transientMessage.value = "临时对话请求失败，请检查设置后重试" }
        }
    }

    private fun retryTemporary(messageId: String) = prepareTemporaryRequest { temporary.retry(messageId, it) }

    fun retry(messageId: String) {
        if (temporary.state.value.id != null) { retryTemporary(messageId); return }
        val conversationId = selectedConversationId.value ?: return
        viewModelScope.launch {
            try {
                repository.retryMessage(conversationId, messageId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun switchMessageBranch(requestId: String, newIndex: Int) {
        selectedBranches.update { it + (requestId to newIndex) }
    }

    fun regenerate(messageId: String) {
        if (temporary.state.value.id != null) { retryTemporary(messageId); return }
        val conversationId = selectedConversationId.value ?: return
        if (uiState.value.isWorking) {
            transientMessage.value = "请先停止正在生成的回复"
            return
        }
        viewModelScope.launch {
            try {
                val targetReqId = uiState.value.messages.firstOrNull { it.id == messageId }?.requestId
                repository.regenerateMessage(conversationId, messageId)
                if (targetReqId != null) {
                    selectedBranches.update { it - targetReqId }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun deleteMessage(messageId: String) {
        if (temporary.state.value.id != null) { temporary.deleteMessage(messageId); return }
        viewModelScope.launch {
            try {
                val targetReqId = uiState.value.messages.firstOrNull { it.id == messageId }?.requestId
                repository.deleteMessage(messageId)
                if (targetReqId != null) {
                    selectedBranches.update { it - targetReqId }
                }
                transientMessage.value = "消息已删除"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun switchModelPreset(preset: ModelPreset) {
        viewModelScope.launch {
            try {
                configStore.updateModelPreset(preset.model, preset.baseUrl)
                transientMessage.value = "已切换模型为 ${preset.label}"
            } catch (failure: Throwable) {
                transientMessage.value = failure.message ?: "切换模型失败"
            }
        }
    }

    fun clearConversation() {
        clearDocuments()
        if (temporary.state.value.id != null) { temporaryPreparation?.cancel(); temporary.clear(); return }
        val conversationId = selectedConversationId.value ?: return
        val generation = conversationGeneration.incrementAndGet()
        val draftImages = selectedImagePaths.value
        selectedImagePaths.value = emptyList()
        draftToRestore.value = null
        viewModelScope.launch {
            try {
                repository.clearConversation(conversationId)
                deleteComposerImages(draftImages)
                transientMessage.value = "聊天记录已清空"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                restoreComposerImages(draftImages, conversationId, generation)
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    suspend fun saveConfig(
        baseUrl: String,
        model: String,
        apiKey: String,
        visionEnabled: Boolean,
        updateManifestUrl: String? = null,
        backgroundCaptureEnabled: Boolean,
        screenshotPrompt: String,
        overlayBackgroundColor: String = DEFAULT_OVERLAY_BACKGROUND_COLOR,
        overlayGlassEnabled: Boolean = DEFAULT_OVERLAY_GLASS_ENABLED,
        shortAnswerModeEnabled: Boolean = false,
        autoFallbackEnabled: Boolean = true,
        screenshotTrigger: ScreenshotTrigger = DEFAULT_SCREENSHOT_TRIGGER,
        autoCollapseThinking: Boolean = true,
    ): Result<Unit> {
        val normalizedUrl = baseUrl.trim().removeSuffix("/")
        val url = runCatching { URI(normalizedUrl) }.getOrNull()
        if (url == null || url.scheme?.lowercase() != "https" || url.host.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("接口地址必须是有效的 HTTPS 地址"))
        }
        if (model.trim().isBlank()) return Result.failure(IllegalArgumentException("模型名称不能为空"))
        if (backgroundCaptureEnabled && !visionEnabled) {
            return Result.failure(IllegalArgumentException("后台截图问答需要开启图片支持"))
        }
        val normalizedScreenshotPrompt = screenshotPrompt.trim()
        if (normalizedScreenshotPrompt.isEmpty()) {
            return Result.failure(IllegalArgumentException("截图问答提示词不能为空"))
        }
        if (normalizedScreenshotPrompt.length > MAX_SCREENSHOT_PROMPT_LENGTH) {
            return Result.failure(
                IllegalArgumentException("截图问答提示词不能超过 $MAX_SCREENSHOT_PROMPT_LENGTH 个字符"),
            )
        }
        val normalizedUpdateUrl = (updateManifestUrl ?: updateConfigStore.readManifestUrl()).trim()
        if (normalizedUpdateUrl.isNotEmpty()) {
            runCatching { UpdateManifestParser.validateHttpsUrl(normalizedUpdateUrl) }
                .onFailure { return Result.failure(it) }
        }
        return runCatching {
            if (apiKey.isNotBlank()) apiKeyStore.save(apiKey.trim())
            val current = configStore.read()
            configStore.update(
                baseUrl = normalizedUrl,
                model = model.trim(),
                visionEnabled = visionEnabled,
                backgroundCaptureEnabled = backgroundCaptureEnabled,
                screenshotPrompt = normalizedScreenshotPrompt,
                overlayBackgroundColor = normalizeOverlayBackgroundColor(overlayBackgroundColor),
                overlayGlassEnabled = overlayGlassEnabled,
                shortAnswerModeEnabled = shortAnswerModeEnabled,
                autoFallbackEnabled = autoFallbackEnabled,
                screenshotTrigger = screenshotTrigger,
                autoCollapseThinking = autoCollapseThinking,
                themeColor = current.themeColor,
            )
            updateConfigStore.setManifestUrl(normalizedUpdateUrl)
            apiKeyAvailable.value = apiKeyStore.hasKey()
        }
    }

    /** Persists the app theme color immediately. */
    suspend fun setThemeColor(themeColor: String): Result<Unit> = runCatching {
        configStore.updateThemeColor(themeColor)
    }

    /** Persists the auto collapse thinking switch immediately. */
    suspend fun setAutoCollapseThinking(enabled: Boolean): Result<Unit> = runCatching {
        configStore.updateAutoCollapseThinking(enabled)
    }

    /** Persists the background screenshot switch without requiring the rest of the form to be saved. */
    suspend fun setBackgroundCaptureEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val current = configStore.read()
        if (enabled && !current.visionEnabled) {
            throw IllegalArgumentException("后台截图问答需要先开启图片支持")
        }
        configStore.update(current.copy(backgroundCaptureEnabled = enabled))
    }

    /** Persists the compact choice/judgment answer switch immediately. */
    suspend fun setShortAnswerModeEnabled(enabled: Boolean): Result<Unit> = runCatching {
        val current = configStore.read()
        configStore.update(current.copy(shortAnswerModeEnabled = enabled))
    }

    /** Persists the auto fallback switch immediately, without saving the rest of the form. */
    suspend fun setAutoFallbackEnabled(enabled: Boolean): Result<Unit> = runCatching {
        configStore.updateAutoFallbackEnabled(enabled)
    }

    /** Persists the default web search switch immediately. */
    suspend fun setDefaultWebSearchEnabled(enabled: Boolean): Result<Unit> = runCatching {
        configStore.updateDefaultWebSearchEnabled(enabled)
    }

    /** Persists a model preset choice immediately so picking a chip survives exiting settings. */
    suspend fun selectModelPreset(preset: ModelPreset): Result<Unit> = runCatching {
        configStore.updateModelPreset(preset.model, preset.baseUrl)
    }

    /** Persists the volume key shortcut immediately, without saving the rest of the form. */
    suspend fun setScreenshotTrigger(trigger: ScreenshotTrigger): Result<Unit> = runCatching {
        configStore.updateScreenshotTrigger(trigger)
    }

    /** Persists color and glass choices as soon as the user selects them. */
    suspend fun setOverlayAppearance(
        backgroundColor: String,
        glassEnabled: Boolean,
    ): Result<Unit> = runCatching {
        configStore.updateOverlayAppearance(
            backgroundColor = normalizeOverlayBackgroundColor(backgroundColor),
            glassEnabled = glassEnabled,
        )
    }

    fun deleteApiKey() {
        apiKeyStore.clear()
        apiKeyAvailable.value = false
        transientMessage.value = "API Key 已删除"
    }

    fun checkForUpdate(manifestUrl: String? = null) {
        if (updateState.value is UpdateUiState.Checking || updateState.value is UpdateUiState.Downloading) return
        val requestedUrl = manifestUrl?.trim()
        if (requestedUrl != null && requestedUrl.isEmpty()) {
            updateState.value = UpdateUiState.Error("请先填写更新清单地址")
            return
        }
        updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            updateManager.checkForUpdate(requestedUrl)
                .onSuccess { result ->
                    updateState.value = when (result) {
                        is UpdateCheckResult.Available -> UpdateUiState.Available(result.update)
                        is UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate(
                            currentVersionCode = result.currentVersionCode,
                            latestVersionName = result.latestVersionName,
                        )
                    }
                }
                .onFailure { failure -> updateState.value = UpdateUiState.Error(failure.userFacingMessage()) }
        }
    }

    fun downloadUpdate(info: AppUpdateInfo) {
        if (updateState.value is UpdateUiState.Downloading) return
        viewModelScope.launch {
            updateManager.download(info).collect { state ->
                updateState.value = when (state) {
                    UpdateDownloadState.Preparing -> UpdateUiState.Downloading()
                    is UpdateDownloadState.Downloading -> UpdateUiState.Downloading(state.progress)
                    is UpdateDownloadState.Ready -> UpdateUiState.Ready(state.apk)
                    is UpdateDownloadState.Failed -> UpdateUiState.Error(state.error.message)
                }
            }
        }
    }

    fun prepareUpdateInstall(): InstallPreparation? {
        val apk = (updateState.value as? UpdateUiState.Ready)?.apk ?: return null
        return runCatching { updateManager.prepareInstall(apk) }
            .onFailure { updateState.value = UpdateUiState.Error(it.userFacingMessage()) }
            .getOrNull()
    }

    fun dismissUpdate() {
        if (updateState.value !is UpdateUiState.Downloading) updateState.value = UpdateUiState.Idle
    }

    private fun discardComposerImages() {
        sharedDraft.value = null
        clearDocuments()
        val oldImages = selectedImagePaths.value
        selectedImagePaths.value = emptyList()
        deleteComposerImages(oldImages)
    }

    private fun clearDocuments() {
        documentImportGeneration++
        documentImportJob?.cancel()
        attachments.value = AttachmentComposerState()
    }

    fun removeDocument(index: Int) {
        attachments.update { it.copy(documents = it.documents.filterIndexed { i, _ -> i != index }) }
    }

    fun importDocuments(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || attachments.value.importing || uiState.value.isWorking) return
        if (uris.size + attachments.value.documents.size > DocumentText.MAX_FILES) {
            transientMessage.value = "一次最多添加 4 个文件"; return
        }
        val importer = DocumentImporter(context.applicationContext)
        val importGeneration = ++documentImportGeneration
        attachments.update { it.copy(importing = true) }
        documentImportJob = viewModelScope.launch {
            val errors = mutableListOf<String>()
            try {
                for (uri in uris) {
                    try {
                        val file = importer.import(uri)
                        require(attachments.value.documents.sumOf { it.text.length } + file.text.length <= DocumentText.MAX_TOTAL_CHARS) { "附件合计最多 24,000 字符，请移除部分文件" }
                        attachments.update { it.copy(documents = it.documents + file) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { errors += failure.message ?: "文件解析失败" }
                }
                if (errors.isNotEmpty()) transientMessage.value = errors.joinToString("\n")
            } finally {
                if (importGeneration == documentImportGeneration) attachments.update { it.copy(importing = false) }
            }
        }
    }

    fun receiveShare(context: Context, intent: Intent) {
        val request = try { IncomingShare.from(intent, context.packageName) }
        catch (failure: Exception) { transientMessage.value = failure.message ?: "无法读取分享内容"; return }
        if (request == null) return
        if (incomingShare.value != null) { transientMessage.value = "请先处理当前分享，再重新分享新内容"; return }
        incomingShare.value = SharePreview(request.text, loading = true)
        val appContext = context.applicationContext
        shareImportJob = viewModelScope.launch {
            val images = mutableListOf<String>()
            val documents = mutableListOf<DocumentAttachment>()
            val errors = mutableListOf<String>()
            var committed = false
            try {
                for (uri in request.uris) {
                    try {
                        val mime = withContext(Dispatchers.IO) { appContext.contentResolver.getType(uri).orEmpty() }
                        if (mime.startsWith("image/")) {
                            require(images.size < 4) { "一次最多分享 4 张图片" }
                            images += imageFileStore.importShared(uri)
                        } else {
                            require(documents.size < DocumentText.MAX_FILES) { "一次最多分享 4 个文件" }
                            val file = DocumentImporter(appContext).import(uri)
                            require(documents.sumOf { it.text.length } + file.text.length <= DocumentText.MAX_TOTAL_CHARS) { "附件合计最多 24,000 字符" }
                            documents += file
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { errors += failure.message ?: "分享项目读取失败" }
                }
                incomingShare.value = SharePreview(request.text, images.toList(), documents.toList(), errors)
                committed = true
            } finally {
                if (!committed) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                    images.forEach { runCatching { imageFileStore.delete(it) } }
                }
            }
        }
    }

    fun dismissShare() {
        shareImportJob?.cancel()
        incomingShare.value?.images?.let(::deleteComposerImages)
        incomingShare.value = null
    }

    fun acceptShare(conversationId: String?, onReady: () -> Unit) {
        val share = incomingShare.value ?: return
        if (share.loading || uiState.value.isAnyWorking) { transientMessage.value = "请等待导入完成，并停止正在生成的回复"; return }
        if (share.text.isBlank() && share.images.isEmpty() && share.documents.isEmpty()) return
        // Prevent a double tap from creating two destination conversations.
        incomingShare.value = share.copy(loading = true)
        viewModelScope.launch {
            try {
                val target = if (conversationId == null) repository.createConversation("分享问答").id
                    else repository.conversations.first().firstOrNull { it.id == conversationId }?.id
                        ?: throw IllegalArgumentException("目标聊天已不存在，请选择新聊天")
                temporaryPreparation?.cancel()
                temporary.close()
                conversationGeneration.incrementAndGet()
                discardComposerImages()
                selectedConversationId.value = target
                selectedImagePaths.value = share.images
                attachments.value = AttachmentComposerState(documents = share.documents)
                draftToRestore.value = null
                sharedDraft.value = com.example.aichat.data.attachment.SharedDraft(target, share.text)
                incomingShare.value = null
                onReady()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                incomingShare.value = share
                transientMessage.value = failure.message ?: "无法导入分享"
            }
        }
    }

    fun consumeSharedDraft() { sharedDraft.value = null }

    private fun restoreComposerImages(
        paths: List<String>,
        conversationId: String,
        generation: Long,
    ) {
        if (paths.isEmpty()) return
        if (selectedConversationId.value == conversationId && conversationGeneration.get() == generation) {
            selectedImagePaths.value = (paths + selectedImagePaths.value).distinct()
        } else {
            deleteComposerImages(paths)
        }
    }

    private fun deleteComposerImages(paths: List<String>) {
        if (paths.isEmpty()) return
        viewModelScope.launch {
            paths.distinct().forEach { path -> runCatching { imageFileStore.delete(path) } }
        }
    }

    private fun ChatMessage.isGenerating(): Boolean =
        role == MessageRole.ASSISTANT && status in setOf(MessageStatus.SENDING, MessageStatus.STREAMING)

    fun testModelConnection(baseUrl: String, model: String, apiKey: String?) {
        viewModelScope.launch {
            probeState.value = ProbeUiState.Probing
            val effectiveKey = if (!apiKey.isNullOrBlank()) apiKey.trim() else apiKeyStore.read()
            val config = ProviderConfig(
                baseUrl = baseUrl,
                model = model,
                apiKey = effectiveKey,
            )
            val result = repository.probeModelConnection(config)
            if (result.isSuccess) {
                probeState.value = ProbeUiState.Success(result.latencyMs, result.message)
            } else {
                probeState.value = ProbeUiState.Failure(result.message)
            }
        }
    }

    fun resetProbeState() {
        probeState.value = ProbeUiState.Idle
    }

    fun loadStorageStats() {
        viewModelScope.launch {
            storageStats.value = repository.getStorageStats()
        }
    }

    fun cleanupOrphanImages() {
        viewModelScope.launch {
            val count = repository.cleanupOrphanImages()
            loadStorageStats()
            transientMessage.value = if (count > 0) "已清理 $count 张孤立图片" else "没有需要清理的孤立图片"
        }
    }

    fun exportBackup(context: Context, targetUri: Uri) {
        viewModelScope.launch {
            backupRestoreState.value = BackupRestoreUiState.Processing
            val convs = repository.getAllConversations()
            val msgs = repository.getAllMessages()
            val imgDir = imageFileStore.getImageDirectory()
            val result = BackupRestoreManager.exportBackup(
                context = context,
                outputUri = targetUri,
                conversations = convs,
                messages = msgs,
                imageDirectory = imgDir,
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
            )
            result.fold(
                onSuccess = { manifest ->
                    backupRestoreState.value = BackupRestoreUiState.Success(
                        "备份成功：已导出 ${manifest.conversationCount} 个会话、${manifest.messageCount} 条消息及 ${manifest.imageCount} 张图片",
                    )
                    loadStorageStats()
                },
                onFailure = { error ->
                    backupRestoreState.value = BackupRestoreUiState.Error(
                        error.localizedMessage ?: "备份导出失败",
                    )
                },
            )
        }
    }

    fun importBackup(context: Context, sourceUri: Uri) {
        viewModelScope.launch {
            backupRestoreState.value = BackupRestoreUiState.Processing
            val existing = repository.getAllConversations().map { it.id }.toSet()
            val imgDir = imageFileStore.getImageDirectory()
            val result = BackupRestoreManager.importBackup(
                context = context,
                inputUri = sourceUri,
                imageDirectory = imgDir,
                existingConversationIds = existing,
                onInsertData = { convs, msgs ->
                    repository.restoreBackupData(convs, msgs)
                },
            )
            result.fold(
                onSuccess = { summary ->
                    backupRestoreState.value = BackupRestoreUiState.Success(
                        "恢复成功：已恢复 ${summary.conversationCount} 个会话、${summary.messageCount} 条消息及 ${summary.imageCount} 张图片",
                    )
                    loadStorageStats()
                },
                onFailure = { error ->
                    backupRestoreState.value = BackupRestoreUiState.Error(
                        error.localizedMessage ?: "备份恢复失败",
                    )
                },
            )
        }
    }

    fun resetBackupRestoreState() {
        backupRestoreState.value = BackupRestoreUiState.Idle
    }

    fun exportMarkdown(context: Context) {
        if (temporary.state.value.id != null) return
        viewModelScope.launch {
            val state = uiState.value
            val convTitle = state.selectedConversationTitle
            val messages = state.messages
            val modelName = state.config.model
            val result = ChatMarkdownExporter.exportAndShareMarkdown(
                context = context,
                conversationTitle = convTitle,
                messages = messages,
                modelName = modelName,
            )
            result.onFailure {
                transientMessage.value = "导出 Markdown 失败: ${it.localizedMessage ?: "未知错误"}"
            }
        }
    }

    fun exportImage(context: Context, includeThinking: Boolean = true) {
        if (temporary.state.value.id != null) return
        viewModelScope.launch {
            val state = uiState.value
            val convTitle = state.selectedConversationTitle
            val messages = state.messages
            val modelName = state.config.model
            val result = ChatImageExporter.exportAndShareImage(
                context = context,
                conversationTitle = convTitle,
                messages = messages,
                modelName = modelName,
                includeThinking = includeThinking,
            )
            result.onFailure {
                transientMessage.value = "生成长图失败: ${it.localizedMessage ?: "未知错误"}"
            }
        }
    }

    // --- 角色面具 (Personas) ---
    fun setConversationPersona(conversationId: String, personaId: String?) {
        viewModelScope.launch {
            try {
                repository.setConversationPersona(conversationId, personaId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun savePersona(persona: ChatPersona) {
        viewModelScope.launch {
            try {
                repository.savePersona(persona)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun deleteCustomPersona(personaId: String) {
        viewModelScope.launch {
            try {
                repository.deleteCustomPersona(personaId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    // --- 服务商配置 (Provider Profiles) ---
    fun setConversationProviderProfile(conversationId: String, profileId: String?) {
        viewModelScope.launch {
            try {
                repository.setConversationProviderProfile(conversationId, profileId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun saveProviderProfile(profile: ProviderProfile, apiKey: String? = null, context: Context? = null) {
        viewModelScope.launch {
            try {
                repository.saveProviderProfile(profile)
                if (apiKey != null && context != null) {
                    ApiKeyStore(context, namespace = profile.id).save(apiKey)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun deleteCustomProfile(profileId: String, context: Context? = null) {
        viewModelScope.launch {
            try {
                repository.deleteProviderProfile(profileId)
                if (context != null) {
                    ApiKeyStore(context, namespace = profileId).clear()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    // --- 上下文滑窗限制 ---
    fun setContextWindowLimit(conversationId: String, limit: Int) {
        viewModelScope.launch {
            try {
                repository.setConversationContextWindowLimit(conversationId, limit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    // --- 跨会话深度全文搜索与定位跳转 ---
    fun setDeepSearchQuery(query: String) {
        deepSearchQuery.value = query.take(200)
        runMessageSearch(reset = true)
    }

    fun setSearchFilter(filter: com.example.aichat.data.model.MessageSearchFilter) {
        searchFilter.value = filter
        runMessageSearch(reset = true)
    }

    fun loadMoreSearch() { if (searchHasMore.value && !isDeepSearching.value) runMessageSearch(reset = false) }

    private fun runMessageSearch(reset: Boolean) {
        deepSearchJob?.cancel()
        val generation = ++searchGeneration
        val keyword = deepSearchQuery.value.trim()
        val filter = searchFilter.value
        if (reset) {
            deepSearchResults.value = emptyList()
            searchHasMore.value = false
            searchSince = if (filter.days > 0) System.currentTimeMillis() - filter.days * 86_400_000L else 0L
        }
        searchError.value = null
        if (keyword.isEmpty()) { isDeepSearching.value = false; return }
        isDeepSearching.value = true
        val cursor = if (reset) null else deepSearchResults.value.lastOrNull()?.let {
            com.example.aichat.data.model.MessageSearchCursor(it.createdAt, it.id)
        }
        deepSearchJob = viewModelScope.launch {
            try {
                if (reset) kotlinx.coroutines.delay(250)
                val rows = repository.searchMessagePage(keyword, filter, searchSince, cursor, 51)
                if (generation != searchGeneration) return@launch
                deepSearchResults.value = (if (reset) emptyList() else deepSearchResults.value) + rows.take(50)
                searchHasMore.value = rows.size > 50
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (generation == searchGeneration) searchError.value = "搜索失败，请重试" }
            finally { if (generation == searchGeneration) isDeepSearching.value = false }
        }
    }
    fun jumpToMessage(conversationId: String, messageId: String) {
        if (uiState.value.isAnyWorking) { transientMessage.value = "请先停止正在生成的回复"; return }
        viewModelScope.launch {
            try {
                val raw = repository.observeMessages(conversationId).first()
                val target = raw.firstOrNull { it.id == messageId }
                if (target == null) { transientMessage.value = "消息已删除，请重新搜索"; return@launch }
                if (target.role == MessageRole.ASSISTANT && target.requestId != null) {
                    val branches = raw.filter { it.role == MessageRole.ASSISTANT && it.requestId == target.requestId }
                    selectedBranches.update { it + (target.requestId to branches.indexOfFirst { it.id == messageId }.coerceAtLeast(0)) }
                }
                if (conversationId != selectedConversationId.value) {
                    conversationGeneration.incrementAndGet()
                    discardComposerImages()
                    draftToRestore.value = null
                    selectedConversationId.value = conversationId
                }
                highlightedMessageId.value = messageId
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { transientMessage.value = "无法定位消息，请重新搜索" }
        }
    }

    fun clearHighlightedMessage() {
        highlightedMessageId.value = null
    }

    // --- 离线 TTS 语音朗读 ---
    fun speakMessage(messageId: String, text: String) {
        ttsManager.speak(messageId, text)
    }

    fun pauseTts() {
        ttsManager.pause()
    }

    fun resumeTts() {
        ttsManager.resume()
    }

    fun stopTts() {
        ttsManager.stop()
    }

    fun seekTtsPrev() {
        ttsManager.seekPrev()
    }

    fun seekTtsNext() {
        ttsManager.seekNext()
    }

    fun cycleTtsSpeechRate() {

        val currentRate = ttsManager.playbackState.value.speechRate
        val rates = listOf(0.8f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIndex = rates.indexOfFirst { kotlin.math.abs(it - currentRate) < 0.05f }
        val nextRate = if (currentIndex in rates.indices) {
            rates[(currentIndex + 1) % rates.size]
        } else {
            1.0f
        }
        ttsManager.setSpeechRate(nextRate)
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }

    private fun Throwable.userFacingMessage(): String = when (this) {
        is ChatClientException -> message
        is AppUpdateException -> message
        else -> message ?: "操作失败，请稍后重试"
    }
}

class MainViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MainViewModel(
            repository = container.chatRepository,
            configStore = container.configStore,
            apiKeyStore = container.apiKeyStore,
            imageFileStore = container.imageFileStore,
            updateConfigStore = container.updateConfigStore,
            updateManager = container.updateManager,
            client = container.client,
            webSearchClient = container.webSearchClient,
            ttsManager = container.ttsManager,
        ) as T
    }
}
