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
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<String?>(null) }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AiAvatar(size = 36.dp)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(
                                text = state.selectedConversationTitle,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
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
            Composer(
                draft = draft,
                selectedImages = state.selectedImagePaths,
                isWorking = state.isWorking,
                webSearchActive = state.webSearchActive,
                onDraftChange = { draft = it },
                onPickImage = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveImage = onRemoveImage,
                onToggleWebSearch = onToggleWebSearch,
                onSend = {
                    onSend(draft)
                    draft = ""
                },
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
                            onEditPrompt = { prompt ->
                                draft = prompt
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
    onDraftChange: (String) -> Unit,
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
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.imePadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (draft.startsWith("/")) {
                SlashCommandSuggestions(
                    query = draft,
                    onSelectCommand = { template ->
                        onDraftChange(template)
                    },
                )
            }
            if (draft.isBlank() && selectedImages.isEmpty() && !isWorking) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
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
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onPickImage, enabled = !isWorking) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "选择图片")
                }
                IconButton(
                    onClick = { showTemplateSheet = true },
                    enabled = !isWorking,
                ) {
                    Icon(
                        Icons.Default.FlashOn,
                        contentDescription = "提示词模板",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = onToggleWebSearch,
                    enabled = !isWorking,
                    modifier = if (webSearchActive) {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    } else {
                        Modifier
                    },
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = if (webSearchActive) "已开启联网搜索（点击关闭）" else "已关闭联网搜索（点击开启）",
                        tint = if (webSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息（输入 / 查看快捷指令）") },
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
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
                FilledIconButton(
                    onClick = if (isWorking) onStop else onSend,
                    enabled = isWorking || draft.isNotBlank() || selectedImages.isNotEmpty(),
                ) {
                    Icon(
                        if (isWorking) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (isWorking) "停止生成" else "发送",
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
                border = if (isUser) null else androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Thinking process card (for DeepSeek Reasoner / R1)
                    if (!isUser && (!message.thinkingContent.isNullOrBlank() || (message.status == MessageStatus.STREAMING && message.text.isBlank()))) {
                        ThinkingCard(
                            thinkingContent = message.thinkingContent.orEmpty(),
                            isStreaming = message.status == MessageStatus.STREAMING && message.text.isBlank(),
                            durationMs = message.thinkingDurationMs,
                            autoCollapse = autoCollapseThinking,
                        )
                        if (message.text.isNotBlank() || !message.webSearchResults.isNullOrEmpty()) Spacer(Modifier.height(10.dp))
                    }

                    // Web search results card
                    if (!isUser && !message.webSearchResults.isNullOrEmpty()) {
                        WebSearchResultsCard(results = message.webSearchResults)
                        if (message.text.isNotBlank()) Spacer(Modifier.height(10.dp))
                    }

                    message.imagePaths.forEach { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "聊天图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.25f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }

                    if (message.text.isNotBlank()) {
                        if (isUser) {
                            SelectionContainer {
                                Text(
                                    message.text,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else {
                            SelectionContainer { MarkdownText(message.text) }
                        }
                    } else if (message.status in setOf(MessageStatus.STREAMING, MessageStatus.SENDING) && message.thinkingContent.isNullOrBlank()) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }

                    // Message Timestamp
                    if (message.text.isNotBlank() || !message.thinkingContent.isNullOrBlank()) {
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
        }

        // Action Toolbar below message bubble
        if (isUser) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp, end = 4.dp),
            ) {
                IconButton(
                    onClick = { onEditPrompt(message.text) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "编辑重发",
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
