package com.example.aichat.ui
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.LinearProgressIndicator

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import com.example.aichat.ui.tts.TtsFloatingPlayer
import com.example.aichat.ui.tts.TtsPlaybackState
import com.example.aichat.data.model.ChatPersona
import com.example.aichat.data.model.ProviderProfile
import com.example.aichat.data.model.PROMPT_TEMPLATES

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MODEL_PRESETS
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import com.example.aichat.data.model.ModelPreset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportImage: (android.net.Uri) -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: (String) -> Unit,
    onRegenerate: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onClear: () -> Unit,
    onDraftRestored: () -> Unit,
    onSelectModelPreset: (ModelPreset) -> Unit,
    onExport: () -> Unit,
    onExportMarkdown: () -> Unit = {},
    onExportImage: (Boolean) -> Unit = {},
    onToggleWebSearch: () -> Unit = {},
    onSwitchBranch: (requestId: String, newIndex: Int) -> Unit = { _, _ -> },
    onRenameConversation: (String, String) -> Unit = { _, _ -> },
    onSpeakMessage: (messageId: String, text: String) -> Unit = { _, _ -> },
    onPauseTts: () -> Unit = {},
    onResumeTts: () -> Unit = {},
    onStopTts: () -> Unit = {},
    onPrevTts: () -> Unit = {},
    onNextTts: () -> Unit = {},
    onCycleTtsSpeechRate: () -> Unit = {},
    onSetPersona: (personaId: String?) -> Unit = {},
    onSetProviderProfile: (profileId: String?) -> Unit = {},
    onSetContextWindowLimit: (limit: Int) -> Unit = {},
    onClearHighlightedMessage: () -> Unit = {},
    onImportDocuments: (List<android.net.Uri>) -> Unit = {},
    onRemoveDocument: (Int) -> Unit = {},
    onSharedDraftConsumed: () -> Unit = {},
) {
    var draft by if (state.isTemporary) remember(state.selectedConversationId) { mutableStateOf("") } else rememberSaveable { mutableStateOf("") }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    var messageToDelete by remember(state.selectedConversationId) { mutableStateOf<String?>(null) }
    var messageToEdit by remember(state.selectedConversationId) { mutableStateOf<ChatMessage?>(null) }
    var quotedMessage by remember(state.selectedConversationId) { mutableStateOf<ChatMessage?>(null) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var showPersonaDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showContextWindowDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(state.highlightedMessageId) {
        val targetId = state.highlightedMessageId ?: return@LaunchedEffect
        val targetIndex = state.messages.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
            delay(3000)
            onClearHighlightedMessage()
        }
    }
    var shouldFollowTail by remember { mutableStateOf(true) }
    var automaticScrollDepth by remember { mutableIntStateOf(0) }
    val isNearBottom by remember { derivedStateOf { listState.isNearBottom() } }
    var pickerConversationId by remember { mutableStateOf<String?>(null) }
    var documentPickerConversationId by remember { mutableStateOf<String?>(null) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (documentPickerConversationId == state.selectedConversationId) onImportDocuments(uris)
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && (!state.isTemporary || pickerConversationId == state.selectedConversationId)) onImportImage(uri)
        pickerConversationId = null
    }

    suspend fun scrollToTail(animated: Boolean) {
        automaticScrollDepth += 1
        try {
            if (state.messages.isEmpty()) return
            if (animated) {
                listState.animateScrollToItem(state.messages.lastIndex)
            } else {
                listState.scrollToItem(state.messages.lastIndex)
            }
        } finally {
            automaticScrollDepth = (automaticScrollDepth - 1).coerceAtLeast(0)
        }
    }

    LaunchedEffect(listState) {
        var wasAutomaticallyScrolling = false
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.isNearBottom(),
                automaticScrollDepth > 0,
            )
        }.collect { (scrolling, nearBottom, automaticallyScrolling) ->
            // Programmatic tail scrolling also sets isScrollInProgress. Ignore those frames,
            // but sample the final position when an automatic animation finishes or is
            // interrupted by a user drag.
            if (!automaticallyScrolling && (scrolling || wasAutomaticallyScrolling)) {
                shouldFollowTail = nearBottom
            }
            wasAutomaticallyScrolling = automaticallyScrolling
        }
    }
    LaunchedEffect(state.selectedConversationId) {
        shouldFollowTail = true
        draft = ""
        if (state.messages.isNotEmpty()) scrollToTail(animated = false)
    }
    LaunchedEffect(state.messages.size) {
        val followTail = shouldFollowTail
        if (state.messages.isNotEmpty() && followTail && shouldFollowTail) {
            scrollToTail(animated = true)
        }
    }
    LaunchedEffect(
        state.messages.lastOrNull()?.id,
        state.messages.lastOrNull()?.text?.length,
        state.messages.lastOrNull()?.thinkingContent?.length,
        state.messages.lastOrNull()?.status,
    ) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val followTail = shouldFollowTail
        if (!followTail) return@LaunchedEffect
        delay(STREAM_SCROLL_DEBOUNCE_MS)
        if (shouldFollowTail) {
            scrollToTail(animated = false)
        }
    }
    LaunchedEffect(state.draftToRestore) {
        state.draftToRestore?.takeIf { draft.isBlank() }?.let {
            draft = it
            onDraftRestored()
        }
    }
    LaunchedEffect(state.selectedConversationId, state.sharedDraft?.id) {
        state.sharedDraft?.takeIf { it.conversationId == state.selectedConversationId }?.let {
            draft = it.text
            quotedMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !state.isTemporary) { showRenameDialog = true }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    ) {
                        AiAvatar(size = 36.dp)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.selectedConversationTitle,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.width(4.dp))
                                if (!state.isTemporary) Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "重命名会话",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { showModelMenu = true }
                                            .padding(vertical = 1.dp),
                                    ) {
                                        val currentPreset = MODEL_PRESETS.firstOrNull { it.model == state.config.model }
                                        Text(
                                            text = currentPreset?.label ?: state.config.model,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "切换模型",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showModelMenu,
                                        onDismissRequest = { showModelMenu = false },
                                    ) {
                                        MODEL_PRESETS.forEach { preset ->
                                            val isSelected = preset.model == state.config.model
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth(),
                                                    ) {
                                                        Text(
                                                            preset.label,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                        )
                                                        if (isSelected) {
                                                            Spacer(Modifier.size(8.dp))
                                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    showModelMenu = false
                                                    onSelectModelPreset(preset)
                                                },
                                            )
                                        }
                                    }
                                }
                                if (!state.isTemporary && state.activePersona != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.clickable { showPersonaDialog = true },
                                    ) {
                                        Text(
                                            text = "${state.activePersona.avatar} ${state.activePersona.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                        ) {
                            if (!state.isTemporary) {
                                DropdownMenuItem(
                                    text = { Text("角色面具 (${state.activePersona?.name ?: "默认"})") },
                                    leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showPersonaDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("服务商预设 (${state.activeProviderProfile?.name ?: "全局默认"})") },
                                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showProfileDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        val conv = state.conversations.firstOrNull { it.id == state.selectedConversationId }
                                        val limit = conv?.contextWindowLimit ?: 8
                                        val limitText = if (limit > 0) "${limit}条" else "不限"
                                        Text("上下文滑窗 ($limitText)")
                                    },
                                    leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showContextWindowDialog = true
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("重命名会话") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        showRenameDialog = true
                                    },
                                )

                            DropdownMenuItem(
                                text = { Text("生成对话长图 (PNG)") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onExportImage(true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导出 Markdown (.md)") },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onExportMarkdown()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("导出纯文本 (TXT)") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onExport()
                                },
                            )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (state.isTemporary) "清空临时内容" else "清空记录", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    showClearConfirmation = true
                                },
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            val lastUserMessage = remember(state.messages) {
                state.messages.lastOrNull { it.role == MessageRole.USER }
            }
            val handleSend: () -> Unit = {
                val cleanDraft = draft.trim()
                val fullText = if (quotedMessage != null) {
                    val q = quotedMessage!!
                    val senderLabel = if (q.role == MessageRole.USER) "用户" else "AI"
                    val quoteLines = q.text.lines().take(5).joinToString("\n> ") { it.take(200) }
                    "> [引用 $senderLabel]: $quoteLines\n\n$cleanDraft"
                } else {
                    cleanDraft
                }
                if (!state.attachments.importing && !state.isWorking && (fullText.isNotBlank() || state.selectedImagePaths.isNotEmpty() || state.attachments.documents.isNotEmpty())) {
                    onSharedDraftConsumed()
                    onSend(fullText)
                    draft = ""
                    quotedMessage = null
                }
            }

            Column {
                if (state.ttsPlaybackState.isPlaying || state.ttsPlaybackState.isPaused) {
                    TtsFloatingPlayer(
                        playbackState = state.ttsPlaybackState,
                        onPlayPause = {
                            if (state.ttsPlaybackState.isPlaying) onPauseTts() else onResumeTts()
                        },
                        onStop = onStopTts,
                        onPrev = onPrevTts,
                        onNext = onNextTts,
                        onCycleRate = onCycleTtsSpeechRate,
                    )
                }
                if (state.isTemporary) Text(

                "临时对话 · 不保存历史或记忆，每次提问独立，退出即清除。内容仍会发送给所选模型；开启联网后也会发送给搜索服务。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            Composer(
                draft = draft,
                files = state.attachments.documents,
                importingFiles = state.attachments.importing,
                onRemoveDocument = onRemoveDocument,
                onPickDocument = {
                    documentPickerConversationId = state.selectedConversationId
                    documentPicker.launch(arrayOf("*/*"))
                },
                selectedImages = state.selectedImagePaths,
                isWorking = state.isWorking,
                webSearchActive = state.webSearchActive,
                quotedMessage = quotedMessage,
                lastUserMessage = lastUserMessage,
                onDraftChange = { draft = it; onSharedDraftConsumed() },
                onCancelQuote = { quotedMessage = null },
                onEditLastMessage = {
                    lastUserMessage?.let { lastMsg ->
                        draft = lastMsg.text
                        onSharedDraftConsumed()
                        quotedMessage = null
                    }
                },
                onPickImage = {
                    pickerConversationId = state.selectedConversationId
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveImage = onRemoveImage,
                onToggleWebSearch = onToggleWebSearch,
                onSend = handleSend,
                onStop = onStop,
                onUsePrompt = { draft = it; onSharedDraftConsumed() },
            )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.messages.isEmpty()) {
                EmptyConversation(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        Box(modifier = Modifier.animateItem()) {
                            MessageBubble(
                                message = message,
                                isWorking = state.isWorking,
                                isSearching = state.isSearching && message.status == MessageStatus.SENDING && message.text.isEmpty(),
                                autoCollapseThinking = state.config.autoCollapseThinking,
                                isHighlighted = message.id == state.highlightedMessageId,
                                ttsPlaybackState = state.ttsPlaybackState,
                                onSpeakMessage = { onSpeakMessage(message.id, message.text) },
                                onPauseTts = onPauseTts,
                                onResumeTts = onResumeTts,
                                onRetry = { onRetry(message.id) },
                                onRegenerate = { onRegenerate(message.id) },
                                onDelete = { messageToDelete = message.id },
                                onEditPrompt = {
                                    messageToEdit = message
                                },
                                onQuoteMessage = {
                                    quotedMessage = message
                                },
                                onSwitchBranch = { newIdx ->
                                    message.requestId?.let { reqId ->
                                        onSwitchBranch(reqId, newIdx)
                                    }
                                },
                            )

                        }
                    }
                }
            }

            // Smart "Scroll to Bottom" Floating Action Button
            AnimatedVisibility(
                visible = !isNearBottom && state.messages.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                if (state.isWorking) {
                    ExtendedFloatingActionButton(
                        onClick = { coroutineScope.launch { scrollToTail(animated = true) } },
                        icon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        text = { Text("新内容生成中…", style = MaterialTheme.typography.labelMedium) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(4.dp),
                    )
                } else {
                    SmallFloatingActionButton(
                        onClick = { coroutineScope.launch { scrollToTail(animated = true) } },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(3.dp),
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "回到底部")
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("清空聊天记录？") },
            text = { Text("这会删除当前会话中的消息和图片，且无法恢复。生成中的回复也会停止。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClear()
                    },
                ) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirmation = false }) { Text("取消") } },
        )
    }

    messageToDelete?.let { targetId ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("删除此条消息？") },
            text = { Text("删除后该条记录无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        messageToDelete = null
                        onDeleteMessage(targetId)
                    },
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { messageToDelete = null }) { Text("取消") } },
        )
    }

    messageToEdit?.let { targetMsg ->
        var editText by remember(targetMsg.id) { mutableStateOf(targetMsg.text) }
        AlertDialog(
            onDismissRequest = { messageToEdit = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 8,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val textToSend = editText.trim()
                        if (textToSend.isNotEmpty()) {
                            messageToEdit = null
                            onSend(textToSend)
                        }
                    },
                    enabled = editText.isNotBlank(),
                ) {
                    Text("作为新消息发送")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { messageToEdit = null }) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            draft = editText
                            messageToEdit = null
                        },
                        enabled = editText.isNotBlank(),
                    ) {
                        Text("填入输入框")
                    }
                }
            },
        )
    }

    if (showRenameDialog) {
        val currentTitle = state.selectedConversationTitle
        var titleInput by rememberSaveable(currentTitle) { mutableStateOf(currentTitle) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("会话标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = titleInput.trim()
                        if (trimmed.isNotEmpty()) {
                            state.selectedConversationId?.let { convId ->
                                onRenameConversation(convId, trimmed)
                            }
                            showRenameDialog = false
                        }
                    },
                    enabled = titleInput.isNotBlank(),
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showPersonaDialog) {
        AlertDialog(
            onDismissRequest = { showPersonaDialog = false },
            title = { Text("选择角色面具") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.activePersona == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetPersona(null)
                                    showPersonaDialog = false
                                }
                                .padding(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("🤖", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("默认助手", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("无预设面具，采用通用助手人设", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (state.activePersona == null) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    items(state.personas, key = { it.id }) { persona ->
                        val isSelected = state.activePersona?.id == persona.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetPersona(persona.id)
                                    showPersonaDialog = false
                                }
                                .padding(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(persona.avatar, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(persona.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                        ) {
                                            Text(
                                                text = persona.category,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = persona.description.ifBlank { persona.systemPrompt.take(60) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPersonaDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("切换服务商预设") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (state.activeProviderProfile == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetProviderProfile(null)
                                    showProfileDialog = false
                                }
                                .padding(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("全局默认配置", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("使用设置页中配置的端点与模型", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (state.activeProviderProfile == null) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    items(state.providerProfiles, key = { it.id }) { profile ->
                        val isSelected = state.activeProviderProfile?.id == profile.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetProviderProfile(profile.id)
                                    showProfileDialog = false
                                }
                                .padding(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(profile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                        ) {
                                            Text(
                                                text = profile.presetLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${profile.defaultModel} · ${profile.baseUrl.removePrefix("https://").removePrefix("http://")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }

    if (showContextWindowDialog) {
        val currentConv = state.conversations.firstOrNull { it.id == state.selectedConversationId }
        val currentLimit = currentConv?.contextWindowLimit ?: 8
        val windowOptions = listOf(
            4 to "4 条 (极速节约，轻量问答)",
            8 to "8 条 (推荐默认，兼顾上下文与消耗)",
            16 to "16 条 (适中记忆，连贯深入讨论)",
            24 to "24 条 (超长跨度，复杂分析与长代码)",
            32 to "32 条 (长篇讨论，保留丰富上下文)",
            0 to "不限制 (携带全部历史消息)",
        )
        AlertDialog(
            onDismissRequest = { showContextWindowDialog = false },
            title = { Text("上下文滑窗长度") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "控制发送给 AI 的历史消息轮数，超出部分自动截断，避免超出上下文窗口或消耗过多 Token。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    windowOptions.forEach { (limit, desc) ->
                        val isSelected = currentLimit == limit
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetContextWindowLimit(limit)
                                    showContextWindowDialog = false
                                }
                                .padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (limit == 0) "全部历史" else "$limit 条消息",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextWindowDialog = false }) {
                    Text("关闭")
                }
            },
        )
    }
}


private fun LazyListState.isNearBottom(totalItems: Int): Boolean {
    if (totalItems == 0) return true
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return true
    val lastVisible = visibleItems.maxByOrNull { it.index } ?: return true
    if (lastVisible.index < totalItems - 1) return false
    val remainingPixels = layoutInfo.viewportEndOffset - (lastVisible.offset + lastVisible.size)
    return remainingPixels <= 160
}

private fun LazyListState.isNearBottom(): Boolean = isNearBottom(layoutInfo.totalItemsCount)

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
        ) {
            AiAvatar(size = 76.dp)
        }
        Spacer(Modifier.height(18.dp))
        Text("和 AI 助手聊点什么", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("在设置中填入 API Key 后，就可以开始对话。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SlashCommandSuggestions(
    query: String,
    onSelectCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matchingTemplates = remember(query) {
        PROMPT_TEMPLATES.filter { item ->
            item.command.startsWith(query, ignoreCase = true) ||
                item.title.contains(query.removePrefix("/"), ignoreCase = true)
        }
    }
    if (matchingTemplates.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "快捷指令",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "点击直接套用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                )
            }
            matchingTemplates.take(4).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectCommand(item.template) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.command,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    files: List<com.example.aichat.data.attachment.DocumentAttachment>,
    importingFiles: Boolean,
    onPickDocument: () -> Unit,
    onRemoveDocument: (Int) -> Unit,
    selectedImages: List<String>,
    isWorking: Boolean,
    webSearchActive: Boolean,
    quotedMessage: ChatMessage?,
    lastUserMessage: ChatMessage?,
    onDraftChange: (String) -> Unit,
    onCancelQuote: () -> Unit,
    onEditLastMessage: () -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onToggleWebSearch: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onUsePrompt: (String) -> Unit,
) {
    var showTemplateSheet by remember { mutableStateOf(false) }

    if (showTemplateSheet) {
        PromptTemplateBottomSheet(
            onDismiss = { showTemplateSheet = false },
            onSelectTemplate = { template ->
                onDraftChange(template)
            },
        )
    }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (draft.startsWith("/")) {
                SlashCommandSuggestions(
                    query = draft,
                    onSelectCommand = { template ->
                        onDraftChange(template)
                    },
                )
            }

            AnimatedVisibility(
                visible = quotedMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                if (quotedMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Reply,
                                contentDescription = "引用回复",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (quotedMessage.role == MessageRole.USER) "引用 用户 的消息" else "引用 AI 助手的回复",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = quotedMessage.text.lineSequence().firstOrNull()?.take(80) ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = onCancelQuote,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "取消引用",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = draft.isBlank() && selectedImages.isEmpty() && files.isEmpty() && !isWorking && !importingFiles,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (lastUserMessage != null) {
                        AssistChip(
                            onClick = onEditLastMessage,
                            label = { Text("✏️ 编辑上一条") },
                        )
                    }
                    listOf("总结要点", "翻译成中文", "解释得简单一点").forEach { prompt ->
                        AssistChip(
                            onClick = { onUsePrompt(prompt) },
                            label = { Text(prompt) },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedImages.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedImages.forEach { path ->
                        Box {
                            AsyncImage(
                                model = chatImageModel(path),
                                contentDescription = "待发送图片",
                                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            IconButton(
                                onClick = { onRemoveImage(path) },
                                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "移除图片",
                                    tint = Color.White,
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                )
                            }
                        }
                    }
                }
            }

            if (importingFiles) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("正在解析文件…", style = MaterialTheme.typography.labelSmall)
            }
            if (files.isNotEmpty()) DocumentChips(files, onRemoveDocument)

            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "输入消息（输入 / 查看快捷指令）",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                minLines = 1,
                maxLines = 6,
                shape = RoundedCornerShape(16.dp),
                enabled = !isWorking,
                trailingIcon = {
                    if (draft.isNotBlank()) {
                        IconButton(onClick = { onDraftChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空输入", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isWorking && !importingFiles && (draft.isNotBlank() || selectedImages.isNotEmpty() || files.isNotEmpty())) onSend() }),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = onPickDocument, enabled = !isWorking && !importingFiles, modifier = Modifier.size(36.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Default.AttachFile, contentDescription = "添加文件", modifier = Modifier.size(22.dp))
                    }
                    IconButton(
                        onClick = onPickImage,
                        enabled = !isWorking,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "选择图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    IconButton(
                        onClick = { showTemplateSheet = true },
                        enabled = !isWorking,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = "提示词模板",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    val webSearchBg by animateColorAsState(
                        targetValue = if (webSearchActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "webSearchBg",
                    )
                    val webSearchFg by animateColorAsState(
                        targetValue = if (webSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "webSearchFg",
                    )

                    Surface(
                        onClick = onToggleWebSearch,
                        enabled = !isWorking,
                        shape = RoundedCornerShape(18.dp),
                        color = webSearchBg,
                        modifier = Modifier.height(34.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp),
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = webSearchFg,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = if (webSearchActive) "联网开启" else "联网搜索",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (webSearchActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = webSearchFg,
                            )
                        }
                    }
                }

                FilledIconButton(
                    onClick = if (isWorking) onStop else onSend,
                    enabled = isWorking || (!importingFiles && (draft.isNotBlank() || selectedImages.isNotEmpty() || files.isNotEmpty())),
                    modifier = Modifier.size(40.dp),
                ) {
                    AnimatedContent(
                        targetState = isWorking,
                        transitionSpec = {
                            (scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn()).togetherWith(
                                scaleOut(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
                            )
                        },
                        label = "sendStopMorph",
                    ) { working ->
                        Icon(
                            imageVector = if (working) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (working) "停止生成" else "发送",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isWorking: Boolean,
    isSearching: Boolean = false,
    autoCollapseThinking: Boolean,
    isHighlighted: Boolean = false,
    ttsPlaybackState: TtsPlaybackState? = null,
    onSpeakMessage: () -> Unit = {},
    onPauseTts: () -> Unit = {},
    onResumeTts: () -> Unit = {},
    onRetry: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onEditPrompt: (String) -> Unit,
    onQuoteMessage: () -> Unit,
    onSwitchBranch: (Int) -> Unit = {},
) {
    val isUser = message.role == MessageRole.USER
    val clipboard = LocalClipboardManager.current
    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Row(
            modifier = if (isUser) {
                Modifier.widthIn(max = 360.dp)
            } else {
                Modifier.fillMaxWidth().padding(end = 8.dp)
            },
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!isUser) {
                AiAvatar(size = 30.dp)
                Spacer(Modifier.size(8.dp))
            }
            val highlightBorder = if (isHighlighted) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else null
            Card(
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                border = highlightBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 6.dp else 1.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.imagePaths.isNotEmpty()) {
                        message.imagePaths.forEach { path ->
                            AsyncImage(
                                model = chatImageModel(path),
                                contentDescription = "消息附加图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }

                    if (isUser) {
                        DocumentMessageText(message.text)
                    } else {
                        // AI Response Body
                        if (!message.thinkingContent.isNullOrBlank()) {
                            ThinkingCard(
                                thinkingContent = message.thinkingContent,
                                isStreaming = message.status == MessageStatus.STREAMING && message.text.isEmpty(),
                                durationMs = message.thinkingDurationMs,
                                autoCollapse = autoCollapseThinking,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        if (message.webSearchResults != null) {
                            WebSearchResultsCard(
                                results = message.webSearchResults,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        if (message.status == MessageStatus.SENDING && message.text.isEmpty()) {
                            if (isSearching) {
                                WebSearchLoadingIndicator()
                            } else {
                                AiThinkingLoadingIndicator()
                            }
                        } else {
                            SelectionContainer {
                                MarkdownText(markdown = linkSearchCitations(message.text, message.webSearchResults.orEmpty()))
                            }
                            if (message.status == MessageStatus.STREAMING) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "正在生成…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isUser && message.totalTokens != null && message.totalTokens > 0) {
                            val durationSec = if (message.generationDurationMs != null && message.generationDurationMs > 0) {
                                message.generationDurationMs / 1000f
                            } else null
                            val speed = if (durationSec != null && durationSec > 0 && message.completionTokens != null && message.completionTokens > 0) {
                                String.format(java.util.Locale.US, "%.1f", message.completionTokens / durationSec)
                            } else null

                            Text(
                                text = buildString {
                                    append("⚡ ")
                                    if (durationSec != null) append("${String.format(java.util.Locale.US, "%.1f", durationSec)}s · ")
                                    append("${message.totalTokens} tokens")
                                    if (speed != null) append(" · $speed t/s")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                fontSize = 10.sp,
                            )
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }
                        Text(
                            text = formatMessageTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isUser) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        // Action Toolbar below message bubble
        if (isUser) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            ) {
                IconButton(
                    onClick = onQuoteMessage,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "引用",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                IconButton(
                    onClick = { onEditPrompt(message.text) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(message.text)) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        } else {
            if (message.status in setOf(MessageStatus.FAILED, MessageStatus.INTERRUPTED)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, start = 38.dp),
                ) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        message.errorMessage ?: "生成失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("重试")
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("删除")
                    }
                }
            } else if (message.status == MessageStatus.SENT && message.text.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp, start = 38.dp),
                ) {
                    if (message.totalBranches > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                        ) {
                            IconButton(
                                onClick = { onSwitchBranch(message.branchIndex - 1) },
                                enabled = message.branchIndex > 0 && !isWorking,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "上一版本",
                                    modifier = Modifier.size(13.dp),
                                    tint = if (message.branchIndex > 0 && !isWorking) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    },
                                )
                            }
                            Text(
                                text = "${message.branchIndex + 1}/${message.totalBranches}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 3.dp),
                            )
                            IconButton(
                                onClick = { onSwitchBranch(message.branchIndex + 1) },
                                enabled = message.branchIndex < message.totalBranches - 1 && !isWorking,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "下一版本",
                                    modifier = Modifier.size(13.dp),
                                    tint = if (message.branchIndex < message.totalBranches - 1 && !isWorking) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    },
                                )
                            }
                        }
                    }
                    val isCurrentTts = ttsPlaybackState?.currentMessageId == message.id
                    val isPlayingThis = isCurrentTts && ttsPlaybackState?.isPlaying == true
                    val isPausedThis = isCurrentTts && ttsPlaybackState?.isPaused == true

                    TextButton(
                        onClick = {
                            if (isPlayingThis) onPauseTts()
                            else if (isPausedThis) onResumeTts()
                            else onSpeakMessage()
                        },
                    ) {
                        Icon(
                            if (isPlayingThis) Icons.Default.Pause else Icons.Default.VolumeUp,
                            contentDescription = if (isPlayingThis) "暂停朗读" else "朗读",
                            modifier = Modifier.size(15.dp),
                            tint = if (isPlayingThis || isPausedThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(3.dp))
                        Text(
                            if (isPlayingThis) "暂停" else "朗读",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isPlayingThis || isPausedThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onQuoteMessage) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("引用", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onRegenerate, enabled = !isWorking) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("重新生成", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("复制", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(3.dp))
                        Text("删除", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
