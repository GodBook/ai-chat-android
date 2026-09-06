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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import coil3.compose.AsyncImage
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    onClear: () -> Unit,
    onDraftRestored: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var shouldFollowTail by remember { mutableStateOf(true) }
    var automaticScrollDepth by remember { mutableIntStateOf(0) }
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
                        AiAvatar(size = 34.dp)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(state.selectedConversationTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (state.isWorking) "正在思考…" else "随时可以聊天",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            Composer(
                draft = draft,
                selectedImages = state.selectedImagePaths,
                isWorking = state.isWorking,
                onDraftChange = { draft = it },
                onPickImage = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveImage = onRemoveImage,
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.messages.isEmpty()) {
                EmptyConversation(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message = message, onRetry = { onRetry(message.id) })
                    }
                }
            }
            if (state.messages.isNotEmpty()) {
                TextButton(
                    onClick = { showClearConfirmation = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("清空聊天记录")
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
private fun Composer(
    draft: String,
    selectedImages: List<String>,
    isWorking: Boolean,
    onDraftChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onUsePrompt: (String) -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.imePadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onPickImage, enabled = !isWorking) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = "选择图片")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入消息") },
                    maxLines = 5,
                    shape = RoundedCornerShape(20.dp),
                    enabled = !isWorking,
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
private fun MessageBubble(message: ChatMessage, onRetry: () -> Unit) {
    val isUser = message.role == MessageRole.USER
    val clipboard = LocalClipboardManager.current
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else Color.White
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
                shape = RoundedCornerShape(8.dp),
                border = if (isUser) null else androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    message.imagePaths.forEach { path ->
                        AsyncImage(
                            model = File(path),
                            contentDescription = "聊天图片",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.25f).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        if (message.text.isNotBlank()) Spacer(Modifier.height(8.dp))
                    }
                    if (message.text.isNotBlank()) {
                        if (isUser) {
                            Text(message.text)
                        } else {
                            // SelectionContainer gives Android's native long-press text selection
                            // handles while preserving the rendered Markdown appearance.
                            SelectionContainer { MarkdownText(message.text) }
                        }
                    } else if (message.status == MessageStatus.STREAMING || message.status == MessageStatus.SENDING) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
        if (!isUser && message.status in setOf(MessageStatus.FAILED, MessageStatus.INTERRUPTED)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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
            }
        } else if (message.status == MessageStatus.SENT && message.text.isNotBlank()) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(3.dp))
                Text("复制", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
