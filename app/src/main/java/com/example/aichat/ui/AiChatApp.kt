package com.example.aichat.ui

import android.content.ActivityNotFoundException
import android.app.Activity
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import com.example.aichat.background.BackgroundScreenshotManager
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.DEFAULT_SCREENSHOT_PROMPT
import com.example.aichat.data.model.MAX_SCREENSHOT_PROMPT_LENGTH
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import com.example.aichat.data.model.OVERLAY_COLOR_PRESETS
import com.example.aichat.data.update.InstallPreparation
import kotlinx.coroutines.launch
import java.io.File

private object Routes {
    const val CONTACTS = "contacts"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
}

@Composable
fun AiChatApp(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (state.config.backgroundCaptureEnabled && result.resultCode == Activity.RESULT_OK && data != null) {
            BackgroundScreenshotManager.start(context, result.resultCode, data)
        }
    }

    // Re-assert the user-selected service state whenever the activity is recreated or returns
    // from a system permission screen. The switch itself remains the source of truth; there is
    // deliberately no matching automatic stop here.
    LaunchedEffect(state.config.backgroundCaptureEnabled) {
        if (state.config.backgroundCaptureEnabled) {
            runCatching { BackgroundScreenshotManager.start(context) }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.navigationBars,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CONTACTS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.CONTACTS) {
                ContactsScreen(
                    conversations = state.conversations,
                    previews = state.conversationPreviews,
                    selectedConversationId = state.selectedConversationId,
                    isAnyWorking = state.isAnyWorking,
                    onOpenChat = { id ->
                        if (viewModel.selectConversation(id)) navController.navigate(Routes.CHAT)
                    },
                    onCreateConversation = { title, onCreated ->
                        viewModel.createConversation(title) {
                            onCreated()
                            navController.navigate(Routes.CHAT)
                        }
                    },
                    onRenameConversation = viewModel::renameConversation,
                    onDeleteConversation = viewModel::deleteConversation,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CHAT) {
                ChatScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onImportImage = viewModel::importImage,
                    onRemoveImage = viewModel::removeSelectedImage,
                    onSend = viewModel::send,
                    onStop = viewModel::stop,
                    onRetry = viewModel::retry,
                    onClear = viewModel::clearConversation,
                    onDraftRestored = viewModel::clearDraftRestore,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onSave = {
                            baseUrl,
                            model,
                            apiKey,
                            visionEnabled,
                            updateUrl,
                            backgroundEnabled,
                            screenshotPrompt,
                            overlayBackgroundColor,
                            overlayGlassEnabled,
                            shortAnswerModeEnabled,
                        ->
                        viewModel.saveConfig(
                            baseUrl = baseUrl,
                            model = model,
                            apiKey = apiKey,
                            visionEnabled = visionEnabled,
                            updateManifestUrl = updateUrl,
                            backgroundCaptureEnabled = backgroundEnabled,
                            screenshotPrompt = screenshotPrompt,
                            overlayBackgroundColor = overlayBackgroundColor,
                            overlayGlassEnabled = overlayGlassEnabled,
                            shortAnswerModeEnabled = shortAnswerModeEnabled,
                        ).also { result ->
                            if (result.isSuccess) {
                                if (backgroundEnabled) {
                                    runCatching { BackgroundScreenshotManager.start(context) }
                                } else {
                                    BackgroundScreenshotManager.stop(context)
                                }
                            }
                        }
                    },
                    onBackgroundCaptureChanged = { enabled ->
                        viewModel.setBackgroundCaptureEnabled(enabled).also { result ->
                            if (result.isSuccess) {
                                if (enabled) {
                                    // Start the worker immediately after the preference is
                                    // persisted. The projection grant can then be supplied from
                                    // the permission button without a startup race.
                                    runCatching { BackgroundScreenshotManager.start(context) }
                                } else {
                                    BackgroundScreenshotManager.stop(context)
                                }
                            }
                        }
                    },
                    onOverlayAppearanceChanged = viewModel::setOverlayAppearance,
                    onShortAnswerModeChanged = viewModel::setShortAnswerModeEnabled,
                    onDeleteKey = viewModel::deleteApiKey,
                    onCheckUpdate = viewModel::checkForUpdate,
                    onDownloadUpdate = viewModel::downloadUpdate,
                    onPrepareUpdate = viewModel::prepareUpdateInstall,
                    onDismissUpdate = viewModel::dismissUpdate,
                    onRequestProjection = {
                        projectionLauncher.launch(BackgroundScreenshotManager.projectionPermissionIntent(context))
                    },
                    onCaptureNow = {
                        val accepted = runCatching { BackgroundScreenshotManager.captureNow(context) }
                            .getOrDefault(false)
                        if (!accepted) {
                            Toast.makeText(
                                context,
                                "请确认音量监听已开启，且当前没有正在处理的截图",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onOpenOverlaySettings = { BackgroundScreenshotManager.openOverlaySettings(context) },
                    onOpenAccessibilitySettings = { BackgroundScreenshotManager.openAccessibilitySettings(context) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ContactsScreen(
    conversations: List<ChatConversation>,
    previews: Map<String, ChatMessage>,
    selectedConversationId: String?,
    isAnyWorking: Boolean,
    onOpenChat: (String) -> Unit,
    onCreateConversation: (String, () -> Unit) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var titleDraft by rememberSaveable { mutableStateOf("新聊天") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("聊天", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(
                        onClick = {
                            titleDraft = "新聊天"
                            showCreateDialog = true
                        },
                        enabled = !isAnyWorking,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新建聊天")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("正在准备聊天列表…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        preview = previews[conversation.id],
                        selected = conversation.id == selectedConversationId,
                        onClick = { onOpenChat(conversation.id) },
                        onRename = {
                            titleDraft = conversation.title
                            renameTarget = conversation
                        },
                        onDelete = { deleteTarget = conversation },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ChatTitleDialog(
            title = "新建聊天",
            value = titleDraft,
            confirmLabel = "创建",
            onValueChange = { titleDraft = it },
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                onCreateConversation(titleDraft.trim()) { showCreateDialog = false }
            },
        )
    }
    renameTarget?.let { target ->
        ChatTitleDialog(
            title = "重命名聊天",
            value = titleDraft,
            confirmLabel = "保存",
            onValueChange = { titleDraft = it },
            onDismiss = { renameTarget = null },
            onConfirm = {
                onRenameConversation(target.id, titleDraft.trim())
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除聊天？") },
            text = { Text("将删除“${target.title}”中的消息和图片，且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        onDeleteConversation(target.id)
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ChatConversation,
    preview: ChatMessage?,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = selected },
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = "打开${conversation.title}",
                    role = Role.Button,
                    onLongClickLabel = "聊天操作",
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = 8.dp),
            colors = ListItemDefaults.colors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                } else {
                    MaterialTheme.colorScheme.background
                },
            ),
            leadingContent = { AiAvatar(size = 52.dp) },
            headlineContent = {
                Text(conversation.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    text = preview?.previewText() ?: "开始一段新的对话",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        preview?.let {
                            Text(
                                DateUtils.getRelativeTimeSpanString(it.createdAt).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "${conversation.title}的聊天操作",
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ChatTitleDialog(
    title: String,
    value: String,
    confirmLabel: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("聊天名称") },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = value.trim().isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun ChatMessage.previewText(): String = when {
    text.isNotBlank() -> text
    imagePaths.isNotEmpty() -> "图片"
    status == MessageStatus.SENDING || status == MessageStatus.STREAMING -> "正在生成…"
    status == MessageStatus.FAILED || status == MessageStatus.INTERRUPTED -> "回复失败，点击查看"
    else -> "开始一段新的对话"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
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
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(onImportImage) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.selectedConversationId) {
        draft = ""
        if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.lastIndex)
    }
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.lastIndex) }
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

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AiAvatar(size = 76.dp)
        Spacer(Modifier.height(18.dp))
        Text("和 AI 助手聊点什么", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
) {
    Surface(tonalElevation = 2.dp, shadowElevation = 0.dp, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                    shape = RoundedCornerShape(18.dp),
                    enabled = !isWorking,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (draft.isNotBlank() || selectedImages.isNotEmpty()) onSend() }),
                )
                IconButton(
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
        } else if (!isUser && message.status == MessageStatus.SENT && message.text.isNotBlank()) {
            TextButton(onClick = { clipboard.setText(AnnotatedString(message.text)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(3.dp))
                Text("复制", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AiAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MarkdownText(markdown: String) {
    val blocks = remember(markdown) { MarkdownDocumentParser.parse(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> MarkdownBlock(block) }
    }
}

@Composable
private fun MarkdownBlock(block: MarkdownBlockModel) {
    when (block) {
        is MarkdownBlockModel.Paragraph -> MarkdownInlineText(block.spans)
        is MarkdownBlockModel.Heading -> Text(
            text = markdownAnnotatedString(block.spans),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.SemiBold,
        )
        is MarkdownBlockModel.CodeBlock -> MarkdownCodeBlock(block)
        is MarkdownBlockModel.Quote -> Column(
            modifier = Modifier
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.blocks.forEach { nested -> MarkdownBlock(nested) }
        }
        is MarkdownBlockModel.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = if (block.ordered) "${block.startNumber + index}." else "•",
                        modifier = Modifier.width(28.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        item.forEach { nested -> MarkdownBlock(nested) }
                    }
                }
            }
        }
        is MarkdownBlockModel.Table -> MarkdownTable(block)
        MarkdownBlockModel.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MarkdownInlineText(spans: List<MarkdownSpanModel>) {
    Text(
        text = markdownAnnotatedString(spans),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun markdownAnnotatedString(spans: List<MarkdownSpanModel>): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        spans.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
            )
            val appendStyledText: AnnotatedString.Builder.() -> Unit = {
                withStyle(style) { append(span.text) }
            }
            val link = span.linkUrl
            if (link == null) {
                appendStyledText()
            } else {
                withLink(
                    LinkAnnotation.Url(
                        url = link,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                    block = appendStyledText,
                )
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlockModel.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.language ?: "代码",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(block.code.trimEnd())) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制代码", modifier = Modifier.size(17.dp))
                }
            }
            Text(
                block.code.trimEnd(),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 10.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlockModel.Table) {
    val columnCount = table.rows.maxOfOrNull { it.cells.size } ?: return
    val cellWidth = if (columnCount <= 2) 142.dp else 126.dp
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        table.rows.forEach { row ->
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                repeat(columnCount) { columnIndex ->
                    val cell = row.cells.getOrNull(columnIndex)
                    val header = cell?.header == true
                    Surface(
                        color = if (header) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .width(cellWidth)
                            .fillMaxHeight()
                            .heightIn(min = 44.dp)
                            .border(0.5.dp, borderColor),
                    ) {
                        Text(
                            text = markdownAnnotatedString(cell?.spans.orEmpty()),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (header) FontWeight.SemiBold else null,
                            textAlign = when (cell?.alignment) {
                                MarkdownTableAlignment.CENTER -> TextAlign.Center
                                MarkdownTableAlignment.END -> TextAlign.End
                                else -> TextAlign.Start
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayAppearanceSettings(
    backgroundColor: String,
    glassEnabled: Boolean,
    enabled: Boolean,
    onBackgroundColorChanged: (String) -> Unit,
    onGlassChanged: (Boolean) -> Unit,
) {
    val previewColor = Color(android.graphics.Color.parseColor(backgroundColor))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "悬浮回答外观",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OVERLAY_COLOR_PRESETS.forEach { preset ->
                val selected = backgroundColor == preset.colorHex
                Column(
                    modifier = Modifier
                        .width(58.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onBackgroundColorChanged(preset.colorHex) },
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(preset.colorHex)))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF153C47),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(preset.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = previewColor.copy(alpha = if (glassEnabled) 0.72f else 0.97f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF0F766E),
                    modifier = Modifier.size(22.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI 截屏回答", fontWeight = FontWeight.SemiBold, color = Color(0xFF193D49))
                    Text("回答外观预览", style = MaterialTheme.typography.bodySmall, color = Color(0xFF365B67))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("半透明毛玻璃", fontWeight = FontWeight.Medium)
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "半透明背景与系统模糊"
                    } else {
                        "当前系统使用半透明效果"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = glassEnabled,
                enabled = enabled,
                onCheckedChange = onGlassChanged,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSave: suspend (String, String, String, Boolean, String, Boolean, String, String, Boolean, Boolean) -> Result<Unit>,
    onBackgroundCaptureChanged: suspend (Boolean) -> Result<Unit>,
    onOverlayAppearanceChanged: suspend (String, Boolean) -> Result<Unit>,
    onShortAnswerModeChanged: suspend (Boolean) -> Result<Unit>,
    onDeleteKey: () -> Unit,
    onCheckUpdate: (String?) -> Unit,
    onDownloadUpdate: (com.example.aichat.data.update.AppUpdateInfo) -> Unit,
    onPrepareUpdate: () -> InstallPreparation?,
    onDismissUpdate: () -> Unit,
    onRequestProjection: () -> Unit,
    onCaptureNow: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    var baseUrl by rememberSaveable(state.config.baseUrl) { mutableStateOf(state.config.baseUrl) }
    var model by rememberSaveable(state.config.model) { mutableStateOf(state.config.model) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var visionEnabled by rememberSaveable(state.config.visionEnabled) { mutableStateOf(state.config.visionEnabled) }
    var backgroundCaptureEnabled by rememberSaveable(state.config.backgroundCaptureEnabled) {
        mutableStateOf(state.config.backgroundCaptureEnabled)
    }
    var screenshotPrompt by rememberSaveable(state.config.screenshotPrompt) {
        mutableStateOf(state.config.screenshotPrompt)
    }
    var overlayBackgroundColor by rememberSaveable(state.config.overlayBackgroundColor) {
        mutableStateOf(state.config.overlayBackgroundColor)
    }
    var overlayGlassEnabled by rememberSaveable(state.config.overlayGlassEnabled) {
        mutableStateOf(state.config.overlayGlassEnabled)
    }
    var shortAnswerModeEnabled by rememberSaveable(state.config.shortAnswerModeEnabled) {
        mutableStateOf(state.config.shortAnswerModeEnabled)
    }
    var updateManifestUrl by rememberSaveable(state.updateManifestUrl) { mutableStateOf(state.updateManifestUrl) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var updatingBackgroundCapture by remember { mutableStateOf(false) }
    var updatingOverlayAppearance by remember { mutableStateOf(false) }
    var updatingShortAnswerMode by remember { mutableStateOf(false) }
    var showDeleteKeyConfirmation by rememberSaveable { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val overlayPermissionGranted = remember(permissionRefresh) {
        BackgroundScreenshotManager.canDrawOverlays(context)
    }
    val accessibilityPermissionGranted = remember(permissionRefresh) {
        BackgroundScreenshotManager.isAccessibilityServiceEnabled(context)
    }
    val usesAccessibilityScreenshot = BackgroundScreenshotManager.usesAccessibilityScreenshot
    val screenshotPermissionGranted = if (usesAccessibilityScreenshot) {
        accessibilityPermissionGranted
    } else {
        BackgroundScreenshotManager.hasActiveProjection
    }
    val backgroundPermissionsReady = screenshotPermissionGranted &&
        overlayPermissionGranted && accessibilityPermissionGranted

    val launchInstallIntent: (android.content.Intent) -> Unit = { intent ->
        try {
            context.startActivity(intent)
            onDismissUpdate()
        } catch (_: ActivityNotFoundException) {
            installError = "系统没有可用的安装程序"
        } catch (failure: SecurityException) {
            installError = failure.message ?: "无法打开安装程序"
        }
    }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        when (val preparation = onPrepareUpdate()) {
            is InstallPreparation.Ready -> launchInstallIntent(preparation.intent)
            is InstallPreparation.PermissionRequired -> installError = "尚未允许安装未知应用"
            null -> Unit
        }
    }

    fun launchPreparedInstall() {
        val preparation = onPrepareUpdate()
        if (preparation == null) return
        try {
            when (preparation) {
                is InstallPreparation.Ready -> launchInstallIntent(preparation.intent)
                is InstallPreparation.PermissionRequired -> unknownSourcesLauncher.launch(preparation.settingsIntent)
            }
        } catch (_: ActivityNotFoundException) {
            installError = "系统没有可用的安装程序"
        } catch (failure: SecurityException) {
            installError = failure.message ?: "无法打开安装设置"
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("连接设置", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 20.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("接口地址") },
                supportingText = { Text("例如：https://api.openai.com/v1") },
                singleLine = true,
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名称") },
                singleLine = true,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                placeholder = { Text(if (state.hasApiKey) "已保存，输入新值可替换" else "粘贴你的 API Key") },
                singleLine = true,
                visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (showKey) "隐藏密钥" else "显示密钥")
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("支持图片", fontWeight = FontWeight.Medium)
                    Text("当前模型需要支持视觉输入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = visionEnabled, onCheckedChange = { visionEnabled = it; saved = false })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("音量下键后台截图问答", fontWeight = FontWeight.Medium)
                    Text(
                        "开启后，应用在后台运行时按下音量下键会截取屏幕并发送给 AI；需要同时开启支持图片",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = backgroundCaptureEnabled,
                    enabled = !saving && !updatingBackgroundCapture,
                    onCheckedChange = { requested ->
                        val previous = backgroundCaptureEnabled
                        backgroundCaptureEnabled = requested
                        saved = false
                        updatingBackgroundCapture = true
                        scope.launch {
                            try {
                                onBackgroundCaptureChanged(requested)
                                    .onSuccess {
                                        error = null
                                        saved = true
                                    }
                                    .onFailure {
                                        backgroundCaptureEnabled = previous
                                        error = it.message ?: "后台截图设置保存失败"
                                    }
                            } finally {
                                updatingBackgroundCapture = false
                            }
                        }
                    },
                )
            }
            OverlayAppearanceSettings(
                backgroundColor = overlayBackgroundColor,
                glassEnabled = overlayGlassEnabled,
                enabled = !saving && !updatingOverlayAppearance,
                onBackgroundColorChanged = { requestedColor ->
                    val previousColor = overlayBackgroundColor
                    overlayBackgroundColor = requestedColor
                    saved = false
                    updatingOverlayAppearance = true
                    scope.launch {
                        try {
                            onOverlayAppearanceChanged(requestedColor, overlayGlassEnabled)
                                .onSuccess {
                                    error = null
                                    saved = true
                                }
                                .onFailure {
                                    overlayBackgroundColor = previousColor
                                    error = it.message ?: "悬浮回答外观保存失败"
                                }
                        } finally {
                            updatingOverlayAppearance = false
                        }
                    }
                },
                onGlassChanged = { requestedGlass ->
                    val previousGlass = overlayGlassEnabled
                    overlayGlassEnabled = requestedGlass
                    saved = false
                    updatingOverlayAppearance = true
                    scope.launch {
                        try {
                            onOverlayAppearanceChanged(overlayBackgroundColor, requestedGlass)
                                .onSuccess {
                                    error = null
                                    saved = true
                                }
                                .onFailure {
                                    overlayGlassEnabled = previousGlass
                                    error = it.message ?: "悬浮回答外观保存失败"
                                }
                        } finally {
                            updatingOverlayAppearance = false
                        }
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("选择/判断题简版回答模式", fontWeight = FontWeight.Medium)
                    Text(
                        "识别到选择题显示 A-D 方块，判断题左边为正确、右边为错误；只显示约 1 秒，不弹出文字回答",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = shortAnswerModeEnabled,
                    enabled = !saving && !updatingShortAnswerMode,
                    onCheckedChange = { requested ->
                        val previous = shortAnswerModeEnabled
                        shortAnswerModeEnabled = requested
                        saved = false
                        updatingShortAnswerMode = true
                        scope.launch {
                            try {
                                onShortAnswerModeChanged(requested)
                                    .onSuccess {
                                        error = null
                                        saved = true
                                    }
                                    .onFailure {
                                        shortAnswerModeEnabled = previous
                                        error = it.message ?: "简版回答设置保存失败"
                                    }
                            } finally {
                                updatingShortAnswerMode = false
                            }
                        }
                    },
                )
            }
            if (backgroundCaptureEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = screenshotPrompt,
                        onValueChange = {
                            screenshotPrompt = it.take(MAX_SCREENSHOT_PROMPT_LENGTH)
                            saved = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("截图后发送给 AI 的提示词") },
                        supportingText = {
                            Text("${screenshotPrompt.length}/$MAX_SCREENSHOT_PROMPT_LENGTH")
                        },
                        isError = screenshotPrompt.isBlank(),
                        minLines = 3,
                        maxLines = 6,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                screenshotPrompt = DEFAULT_SCREENSHOT_PROMPT
                                saved = false
                            },
                            enabled = screenshotPrompt != DEFAULT_SCREENSHOT_PROMPT,
                        ) {
                            Text("恢复默认提示词")
                        }
                    }
                    Text(
                        if (usesAccessibilityScreenshot) {
                            "开关会立即保存，只会由你手动关闭。请开启悬浮窗和音量监听，并在系统无障碍设置中选择“AI BOTOY”。Android 11 及以上由无障碍服务直接截图，不需要单独授权屏幕录制。"
                        } else {
                            "开关会立即保存，只会由你手动关闭。Android 10 还需授权屏幕捕获、悬浮窗和音量监听。屏幕捕获授权在应用进程被系统结束后需要重新授予。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (usesAccessibilityScreenshot) {
                            "系统截图与音量监听：${if (accessibilityPermissionGranted) "已开启" else "未开启"}；悬浮窗：${if (overlayPermissionGranted) "已开启" else "未开启"}"
                        } else {
                            "屏幕捕获：${if (screenshotPermissionGranted) "已授权" else "未授权"}；悬浮窗：${if (overlayPermissionGranted) "已开启" else "未开启"}；音量监听：${if (accessibilityPermissionGranted) "已开启" else "未开启"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backgroundPermissionsReady) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onRequestProjection,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("授权屏幕捕获")
                            }
                            OutlinedButton(
                                onClick = onOpenOverlaySettings,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("开启悬浮窗")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onOpenOverlaySettings,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("开启悬浮窗")
                        }
                    }
                    OutlinedButton(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("开启音量监听")
                    }
                    OutlinedButton(
                        onClick = onCaptureNow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("立即测试截图")
                    }
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Text("API Key 仅加密保存在本机，并会直接发送给你填写的模型服务。请不要在不可信设备上分享应用或调试日志。")
                }
            }
            Text("在线更新", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = updateManifestUrl,
                onValueChange = { updateManifestUrl = it; saved = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("更新清单地址") },
                supportingText = { Text("HTTPS JSON，例如 https://你的域名/latest.json") },
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { onCheckUpdate(updateManifestUrl) },
                    enabled = state.updateState !is UpdateUiState.Checking && state.updateState !is UpdateUiState.Downloading,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("检查更新")
                }
                when (val update = state.updateState) {
                    is UpdateUiState.Checking -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    is UpdateUiState.Downloading -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    else -> Unit
                }
            }
            when (val update = state.updateState) {
                is UpdateUiState.UpToDate -> Text("当前已是最新版本（${update.latestVersionName}）", color = MaterialTheme.colorScheme.primary)
                is UpdateUiState.Error -> Text(update.message, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved) {
                AssistChip(onClick = {}, label = { Text("已保存") }, leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                onSave(
                                    baseUrl,
                                    model,
                                    apiKey,
                                    visionEnabled,
                                    updateManifestUrl,
                                    backgroundCaptureEnabled,
                                    screenshotPrompt,
                                    overlayBackgroundColor,
                                    overlayGlassEnabled,
                                    shortAnswerModeEnabled,
                                )
                                    .onSuccess { error = null; saved = true; apiKey = "" }
                                    .onFailure { error = it.message ?: "保存失败"; saved = false }
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving && !updatingOverlayAppearance,
                    modifier = Modifier.weight(1f),
                ) {
                    if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("保存设置")
                }
                if (state.hasApiKey) {
                    OutlinedButton(onClick = { showDeleteKeyConfirmation = true }, enabled = !saving) { Text("删除密钥") }
                }
            }
            Text(
                "聊天记录和设置保存在本机。通过同一签名安装新版 APK 时，Android 会保留原有数据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    when (val update = state.updateState) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text("发现新版本 ${update.info.versionName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("版本号：${update.info.versionCode}")
                    if (update.info.releaseNotes.isNotBlank()) Text(update.info.releaseNotes)
                    Text("下载后将调用系统安装程序，应用数据会保留。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { onDownloadUpdate(update.info) }) { Text("下载更新") }
            },
            dismissButton = { TextButton(onClick = onDismissUpdate) { Text("稍后") } },
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载更新") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fraction = update.progress?.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text("${(fraction * 100).toInt()}%")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在下载…")
                    }
                }
            },
            confirmButton = {},
        )
        is UpdateUiState.Ready -> AlertDialog(
            onDismissRequest = onDismissUpdate,
            title = { Text("更新已下载") },
            text = { Text("准备交给系统安装程序。若系统要求许可，请开启“允许安装未知应用”；返回后会继续安装。") },
            confirmButton = { TextButton(onClick = { launchPreparedInstall() }) { Text("安装") } },
            dismissButton = { TextButton(onClick = onDismissUpdate) { Text("稍后") } },
        )
        else -> Unit
    }
    installError?.let { message ->
        AlertDialog(
            onDismissRequest = { installError = null },
            title = { Text("无法安装更新") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { installError = null }) { Text("知道了") } },
        )
    }
    if (showDeleteKeyConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteKeyConfirmation = false },
            title = { Text("删除 API Key？") },
            text = { Text("删除后将无法发送新消息，聊天记录不会受影响。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteKeyConfirmation = false
                        onDeleteKey()
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteKeyConfirmation = false }) { Text("取消") } },
        )
    }
}
