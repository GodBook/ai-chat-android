package com.example.aichat.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aichat.AppContainer
import com.example.aichat.BuildConfig
import com.example.aichat.data.local.ApiKeyStore
import com.example.aichat.data.local.ConfigStore
import com.example.aichat.data.local.ImageFileStore
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

data class MainUiState(
    val conversations: List<ChatConversation> = emptyList(),
    val conversationPreviews: Map<String, ChatMessage> = emptyMap(),
    val selectedConversationId: String? = null,
    val selectedConversationTitle: String = DEFAULT_CONVERSATION_TITLE,
    val messages: List<ChatMessage> = emptyList(),
    val config: ProviderConfig = ProviderConfig(),
    val hasApiKey: Boolean = false,
    val selectedImagePaths: List<String> = emptyList(),
    val isWorking: Boolean = false,
    val isAnyWorking: Boolean = false,
    val message: String? = null,
    /** Text to put back into the composer after a request could not be sent. */
    val draftToRestore: String? = null,
    val updateManifestUrl: String = "",
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val collapsedGroups: Set<String> = emptySet(),
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
)

class MainViewModel(
    private val repository: ChatRepository,
    private val configStore: ConfigStore,
    private val apiKeyStore: ApiKeyStore,
    private val imageFileStore: ImageFileStore,
    private val updateConfigStore: UpdateConfigStore,
    private val updateManager: AppUpdateManager,
) : ViewModel() {
    private val selectedConversationId = MutableStateFlow<String?>(null)
    private val selectedImagePaths = MutableStateFlow<List<String>>(emptyList())
    private val transientMessage = MutableStateFlow<String?>(null)
    private val draftToRestore = MutableStateFlow<String?>(null)
    private val apiKeyAvailable = MutableStateFlow(runCatching { apiKeyStore.hasKey() }.getOrDefault(false))
    private val updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    private val conversationGeneration = AtomicLong(0)

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
        ) { conversations, previews, messages, anyWorking ->
            ConversationSnapshot(
                conversations = conversations,
                previews = previews,
                selectedId = selection.selectedId,
                selectedMessages = messages,
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
    ) { images, message, draft -> ComposerSnapshot(images, message, draft) }

    private val baseUiState: Flow<MainUiState> = combine(
        conversationSnapshot,
        settingsSnapshot,
        composerSnapshot,
    ) { conversationsState, settings, composer ->
        val previews = conversationsState.previews.associateBy { it.conversationId }
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
            isWorking = conversationsState.selectedMessages.any { it.isGenerating() },
            isAnyWorking = conversationsState.isAnyWorking,
            message = composer.message,
            draftToRestore = composer.draft,
            updateManifestUrl = settings.updateManifestUrl.ifBlank { BuildConfig.UPDATE_MANIFEST_URL },
            collapsedGroups = settings.collapsedGroups,
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        baseUiState,
        apiKeyAvailable,
        updateState,
    ) { state, hasApiKey, update ->
        state.copy(hasApiKey = hasApiKey, updateState = update)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            repository.recoverInterruptedMessages()
            val existing = repository.conversations.first()
            if (existing.isEmpty()) repository.createConversation()
            selectedConversationId.value = repository.conversations.first().firstOrNull()?.id
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
                val messages = repository.observeMessages(id).first()
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
                        val msgs = repository.observeMessages(id).first()
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
        selectedImagePaths.value = selectedImagePaths.value - path
        deleteComposerImages(listOf(path))
    }

    fun send(text: String) {
        if (uiState.value.isWorking) return
        val images = selectedImagePaths.value
        if (text.isBlank() && images.isEmpty()) {
            transientMessage.value = "请输入消息或选择图片"
            return
        }
        val generation = conversationGeneration.get()
        val conversationId = selectedConversationId.value
        draftToRestore.value = null
        selectedImagePaths.value = emptyList()
        viewModelScope.launch {
            var targetId = conversationId
            try {
                val resolvedTargetId = targetId ?: repository.createConversation().also {
                    targetId = it.id
                    selectedConversationId.value = it.id
                }.id
                repository.sendMessage(resolvedTargetId, text, images)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if ((failure as? ChatClientException)?.requestWasPersisted != true) {
                    if (conversationGeneration.get() == generation && selectedConversationId.value == targetId) {
                        selectedImagePaths.value = (images + selectedImagePaths.value).distinct()
                        draftToRestore.value = text
                    } else {
                        deleteComposerImages(images)
                    }
                }
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun stop() = repository.stopGeneration()

    fun retry(messageId: String) {
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

    fun regenerate(messageId: String) {
        val conversationId = selectedConversationId.value ?: return
        if (uiState.value.isWorking) {
            transientMessage.value = "请先停止正在生成的回复"
            return
        }
        viewModelScope.launch {
            try {
                repository.regenerateMessage(conversationId, messageId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                transientMessage.value = failure.userFacingMessage()
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            try {
                repository.deleteMessage(messageId)
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
        val oldImages = selectedImagePaths.value
        selectedImagePaths.value = emptyList()
        deleteComposerImages(oldImages)
    }

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
        ) as T
    }
}
