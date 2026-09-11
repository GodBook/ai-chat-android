package com.example.aichat.ui

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
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
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<String?>(null) }
    var messageToEdit by remember { mutableStateOf<ChatMessage?>(null) }
    var quotedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var shouldFollowTail by remember { mutableStateOf(true) }
    var automaticScrollDepth by remember { mutableIntStateOf(0) }
    val isNearBottom by remember { derivedStateOf { listState.isNearBottom() } }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onImportImage) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showRenameDialog = true }
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
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "重命名会话",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
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
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清空记录", color = MaterialTheme.colorScheme.error) },
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
                if (fullText.isNotBlank() || state.selectedImagePaths.isNotEmpty()) {
                    onSend(fullText)
                    draft = ""
                    quotedMessage = null
                }
            }

            Composer(
                draft = draft,
                selectedImages = state.selectedImagePaths,
                isWorking = state.isWorking,
                webSearchActive = state.webSearchActive,
                quotedMessage = quotedMessage,
                lastUserMessage = lastUserMessage,
                onDraftChange = { draft = it },
                onCancelQuote = { quotedMessage = null },
                onEditLastMessage = {
                    lastUserMessage?.let { lastMsg ->
                        draft = lastMsg.text
                        quotedMessage = null
                    }
                },
                onPickImage = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveImage = onRemoveImage,
                onToggleWebSearch = onToggleWebSearch,
                onSend = handleSend,
                onStop = onStop,
                onUsePrompt = { draft = it },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
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
                        MessageBubble(
                            message = message,
                            isWorking = state.isWorking,
                            autoCollapseThinking = state.config.autoCollapseThinking,
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
            .imePadding(),
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

            if (draft.isBlank() && selectedImages.isEmpty() && !isWorking) {
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

            if (selectedImages.isNotEmpty()) {
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
                                model = File(path),
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
                keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank() || selectedImages.isNotEmpty()) onSend() }),
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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

                    Surface(
                        onClick = onToggleWebSearch,
                        enabled = !isWorking,
                        shape = RoundedCornerShape(18.dp),
                        color = if (webSearchActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
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
                                tint = if (webSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = if (webSearchActive) "联网开启" else "联网搜索",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (webSearchActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (webSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                FilledIconButton(
                    onClick = if (isWorking) onStop else onSend,
                    enabled = isWorking || draft.isNotBlank() || selectedImages.isNotEmpty(),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = if (isWorking) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isWorking) "停止生成" else "发送",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isWorking: Boolean,
    autoCollapseThinking: Boolean,
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
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!isUser) {
                AiAvatar(size = 30.dp)
                Spacer(Modifier.size(8.dp))
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.imagePaths.isNotEmpty()) {
                        message.imagePaths.forEach { path ->
                            AsyncImage(
                                model = File(path),
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
                        SelectionContainer {
                            MarkdownText(markdown = message.text)
                        }
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

                        if (!message.webSearchResults.isNullOrEmpty()) {
                            WebSearchResultsCard(
                                results = message.webSearchResults,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }

                        if (message.status == MessageStatus.SENDING && message.text.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("思考中…", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            SelectionContainer {
                                MarkdownText(markdown = message.text)
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
                    Text(
                        text = formatMessageTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End),
                    )
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
