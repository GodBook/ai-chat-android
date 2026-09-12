package com.example.aichat.ui
import androidx.compose.ui.platform.testTag

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.example.aichat.data.local.MessageSearchResultItem

import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.util.Locale
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.DEFAULT_GROUP_NAME
import com.example.aichat.data.model.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ContactsScreen(
    conversations: List<ChatConversation>,
    previews: Map<String, ChatMessage>,
    selectedConversationId: String?,
    isAnyWorking: Boolean,
    collapsedGroups: Set<String> = emptySet(),
    onOpenChat: (String) -> Unit,
    onCreateConversation: (String, String?, () -> Unit) -> Unit,
    onFastCreateConversation: () -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteConversations: (Set<String>) -> Unit,
    onExportConversation: (String) -> Unit,
    onExportConversations: (Set<String>) -> Unit,
    onSetConversationGroup: (String, String?) -> Unit = { _, _ -> },
    onSetConversationsGroup: (Set<String>, String?) -> Unit = { _, _ -> },
    onRenameGroup: (String, String) -> Unit = { _, _ -> },
    onToggleGroupCollapsed: (String) -> Unit = {},
    onTogglePinConversation: (String) -> Unit = {},
    onSetConversationIcon: (String, String?) -> Unit = { _, _ -> },
    onReorderConversationsInGroup: (List<String>) -> Unit = {},
    onTemporaryConversation: () -> Unit = {},
    onOpenSettings: () -> Unit,
    deepSearchResults: List<MessageSearchResultItem> = emptyList(),
    isDeepSearching: Boolean = false,
    onDeepSearchQueryChange: (String) -> Unit = {},
    onJumpToMessage: (conversationId: String, messageId: String) -> Unit = { _, _ -> },
    searchFilter: com.example.aichat.data.model.MessageSearchFilter = com.example.aichat.data.model.MessageSearchFilter(),
    onSearchFilter: (com.example.aichat.data.model.MessageSearchFilter) -> Unit = {},
    searchHasMore: Boolean = false,
    searchError: String? = null,
    onLoadMoreSearch: () -> Unit = {},
    searchQuery: String = "",
) {

    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    var renameTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var iconTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var setGroupTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var renameGroupTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var titleDraft by rememberSaveable { mutableStateOf("新聊天") }
    var groupDraft by rememberSaveable { mutableStateOf("") }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showBatchDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showBatchGroupDialog by rememberSaveable { mutableStateOf(false) }

    var pendingDeleteConversation by remember { mutableStateOf<ChatConversation?>(null) }
    var undoRemainingMillis by remember { mutableLongStateOf(2000L) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var localGroupOrders by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    val haptic = LocalHapticFeedback.current

    val commitPendingDelete: () -> Unit = {
        pendingDeleteConversation?.let { pending ->
            onDeleteConversation(pending.id)
            pendingDeleteConversation = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            commitPendingDelete()
        }
    }

    LaunchedEffect(pendingDeleteConversation) {
        val pending = pendingDeleteConversation ?: return@LaunchedEffect
        val startTime = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = 2000L - elapsed
            if (remaining <= 0) {
                undoRemainingMillis = 0L
                onDeleteConversation(pending.id)
                pendingDeleteConversation = null
                break
            }
            undoRemainingMillis = remaining
            delay(50L)
        }
    }

    val onQuickDelete: (ChatConversation) -> Unit = { target ->
        commitPendingDelete()
        pendingDeleteConversation = target
        undoRemainingMillis = 2000L
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val visibleConversations = remember(conversations, previews, searchQuery, pendingDeleteConversation) {
        filterConversations(conversations, previews, searchQuery)
            .filter { it.id != pendingDeleteConversation?.id }
    }
    val existingGroups = remember(conversations) {
        conversations.mapNotNull { it.groupName?.trim()?.takeIf { g -> g.isNotEmpty() } }.distinct()
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    Scaffold(
        topBar = {
            Column {
                if (isSelectionMode) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "退出多选")
                            }
                        },
                        title = {
                            Column {
                                Text("批量管理", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (selectedIds.isEmpty()) "点击会话以勾选" else "已选择 ${selectedIds.size} 个会话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            val allSelected = visibleConversations.isNotEmpty() &&
                                visibleConversations.all { it.id in selectedIds }
                            TextButton(
                                onClick = {
                                    selectedIds = if (allSelected) {
                                        emptySet()
                                    } else {
                                        visibleConversations.map { it.id }.toSet()
                                    }
                                },
                            ) {
                                Text(if (allSelected) "取消全选" else "全选")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                } else {
                    TopAppBar(
                        title = {
                            Column {
                                Text("聊天", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (conversations.isEmpty()) "准备你的下一段对话" else "${conversations.size} 个会话",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { searchOpen = !searchOpen }) {
                                Icon(Icons.Default.Search, contentDescription = "搜索聊天")
                            }
                            if (conversations.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchOpen = false
                                    isSelectionMode = true
                                }) {
                                    Icon(Icons.Default.Checklist, contentDescription = "批量管理")
                                }
                            }
                            IconButton(onClick = onTemporaryConversation, enabled = !isAnyWorking) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = "开启临时对话")
                            }
                            NewChatActionButton(
                                enabled = !isAnyWorking,
                                onFastCreate = onFastCreateConversation,
                                onLongPressCreate = {
                                    titleDraft = "新聊天"
                                    groupDraft = ""
                                    showCreateDialog = true
                                },
                            )
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "设置")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    if (searchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {

                                onDeepSearchQueryChange(it.take(200))
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            placeholder = { Text("搜索聊天或跨会话全文检索…") },
                            singleLine = true,
                            trailingIcon = {
                                TextButton(
                                    onClick = {

                                        onDeepSearchQueryChange("")
                                        searchOpen = false
                                    },
                                ) { Text("关闭") }
                            },
                        )
                        MessageSearchFilters(searchFilter, onSearchFilter)
                    }

                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                BottomAppBar(
                    actions = {
                        TextButton(
                            onClick = {
                                val targets = selectedIds
                                isSelectionMode = false
                                selectedIds = emptySet()
                                onExportConversations(targets)
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("批量导出 (${selectedIds.size})")
                        }
                        TextButton(
                            onClick = { showBatchGroupDialog = true },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("批量分组 (${selectedIds.size})")
                        }
                    },
                    floatingActionButton = {
                        Button(
                            onClick = { showBatchDeleteDialog = true },
                            enabled = selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("批量删除 (${selectedIds.size})")
                        }
                    },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("正在准备聊天列表…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (visibleConversations.isEmpty() && deepSearchResults.isEmpty() && !isDeepSearching && searchError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (searchQuery.isNotBlank()) "没有匹配的聊天或消息" else "没有匹配的聊天", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val groupedConversations = remember(visibleConversations) {
                    val namedGroups = visibleConversations
                        .mapNotNull { it.groupName?.trim()?.takeIf { g -> g.isNotEmpty() } }
                        .distinct()
                    val result = mutableListOf<Pair<String, List<ChatConversation>>>()
                    namedGroups.forEach { name ->
                        result.add(name to visibleConversations.filter { it.groupName?.trim() == name })
                    }
                    val ungrouped = visibleConversations.filter { it.groupName.isNullOrBlank() }
                    if (ungrouped.isNotEmpty()) {
                        result.add(DEFAULT_GROUP_NAME to ungrouped)
                    }
                    result
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    groupedConversations.forEach { (groupName, convList) ->
                        val isCollapsed = groupName in collapsedGroups && searchQuery.isBlank()
                        val isDefaultGroup = groupName == DEFAULT_GROUP_NAME

                        item(key = "header_$groupName") {
                            ConversationGroupHeader(
                                groupName = groupName,
                                count = convList.size,
                                isCollapsed = isCollapsed,
                                onToggleCollapse = { onToggleGroupCollapsed(groupName) },
                                onRenameGroup = if (!isDefaultGroup) {
                                    { renameGroupTarget = groupName }
                                } else null,
                                onDissolveGroup = if (!isDefaultGroup) {
                                    { onSetConversationsGroup(convList.map { it.id }.toSet(), null) }
                                } else null,
                            )
                        }

                        if (!isCollapsed) {
                            val orderedConvList = run {
                                val customOrder = localGroupOrders[groupName]
                                if (customOrder != null) {
                                    val map = convList.associateBy { it.id }
                                    customOrder.mapNotNull { map[it] } + convList.filter { it.id !in customOrder }
                                } else {
                                    convList
                                }
                            }

                            items(orderedConvList, key = { it.id }) { conversation ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            onQuickDelete(conversation)
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                )

                                val isDragging = draggingId == conversation.id
                                val canDrag = orderedConvList.size > 1 && !isSelectionMode && searchQuery.isBlank()
                                val dragHandleModifier = if (canDrag) {
                                    Modifier.pointerInput(conversation.id, groupName) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggingId = conversation.id
                                                dragOffset = 0f
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragEnd = {
                                                val currentOrder = localGroupOrders[groupName] ?: orderedConvList.map { it.id }
                                                onReorderConversationsInGroup(currentOrder)
                                                draggingId = null
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                dragOffset = 0f
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                                val currentList = (localGroupOrders[groupName] ?: orderedConvList.map { it.id }).toMutableList()
                                                val currentIndex = currentList.indexOf(conversation.id)
                                                if (currentIndex >= 0) {
                                                    val itemHeightPx = 76.dp.toPx()
                                                    val targetIndex = (currentIndex + (dragOffset / itemHeightPx).toInt()).coerceIn(0, currentList.lastIndex)
                                                    if (targetIndex != currentIndex) {
                                                        currentList.removeAt(currentIndex)
                                                        currentList.add(targetIndex, conversation.id)
                                                        localGroupOrders = localGroupOrders + (groupName to currentList)
                                                        dragOffset -= (targetIndex - currentIndex) * itemHeightPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                } else Modifier

                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .zIndex(if (isDragging) 10f else 1f)
                                        .graphicsLayer {
                                            if (isDragging) {
                                                translationY = dragOffset
                                                scaleX = 1.02f
                                                scaleY = 1.02f
                                                shadowElevation = 8.dp.toPx()
                                            }
                                        },
                                ) {
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        enableDismissFromStartToEnd = false,
                                        enableDismissFromEndToStart = !isSelectionMode && draggingId == null,
                                        backgroundContent = {
                                            val color by animateColorAsState(
                                                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                                label = "swipeBg",
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(horizontal = 12.dp)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(color)
                                                    .padding(end = 20.dp),
                                                contentAlignment = Alignment.CenterEnd,
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "快速删除",
                                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        },
                                    ) {
                                        ConversationRow(
                                            conversation = conversation,
                                            preview = previews[conversation.id],
                                            selected = conversation.id == selectedConversationId,
                                            isSelectionMode = isSelectionMode,
                                            isChecked = conversation.id in selectedIds,
                                            canDrag = canDrag,
                                            dragHandleModifier = dragHandleModifier,
                                            onToggleCheck = {
                                                selectedIds = if (conversation.id in selectedIds) {
                                                    selectedIds - conversation.id
                                                } else {
                                                    selectedIds + conversation.id
                                                }
                                            },
                                            onClick = {
                                                commitPendingDelete()
                                                onOpenChat(conversation.id)
                                            },
                                            onLongClick = {
                                                commitPendingDelete()
                                                isSelectionMode = true
                                                selectedIds = setOf(conversation.id)
                                            },
                                            onRename = {
                                                commitPendingDelete()
                                                titleDraft = conversation.title
                                                renameTarget = conversation
                                            },
                                            onSetGroup = {
                                                commitPendingDelete()
                                                setGroupTarget = conversation
                                            },
                                            onDelete = {
                                                commitPendingDelete()
                                                deleteTarget = conversation
                                            },
                                            onSetIcon = {
                                                commitPendingDelete()
                                                iconTarget = conversation
                                            },
                                            onExport = {
                                                commitPendingDelete()
                                                onExportConversation(conversation.id)
                                            },
                                            onTogglePin = {
                                                commitPendingDelete()
                                                onTogglePinConversation(conversation.id)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (searchQuery.isNotBlank()) {
                        if (searchError != null) item(key = "search_error") {
                            Text(searchError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                            TextButton(onClick = { onDeepSearchQueryChange(searchQuery) }) { Text("重试搜索") }
                        }
                        if (isDeepSearching) {
                            item(key = "deep_search_loading") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "🔍 正在进行跨会话全文检索…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        if (deepSearchResults.isNotEmpty()) {
                            item(key = "deep_search_header") {
                                Text(
                                    text = "消息全文匹配（已显示 ${deepSearchResults.size} 条）",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            items(deepSearchResults, key = { "msg_search_${it.id}" }) { resultItem ->
                                MessageSearchResultRow(
                                    item = resultItem,
                                    keyword = searchQuery.trim(),
                                    onClick = {
                                        commitPendingDelete()

                                        searchOpen = false
                                        onDeepSearchQueryChange("")
                                        onJumpToMessage(resultItem.conversationId, resultItem.id)
                                    },
                                )
                            }
                            if (searchHasMore) item(key = "search_more") {
                                TextButton(onClick = onLoadMoreSearch, enabled = !isDeepSearching, modifier = Modifier.fillMaxWidth()) { Text("加载更多消息") }
                            }
                        }
                    }
                }
            }

            // 2s Undo deletion popup in bottom-right corner!
            AnimatedVisibility(
                visible = pendingDeleteConversation != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (isSelectionMode) 88.dp else 18.dp)
                    .navigationBarsPadding(),
            ) {
                pendingDeleteConversation?.let {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tonalElevation = 8.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "已删除 (${String.format(Locale.US, "%.1f", undoRemainingMillis / 1000f)}s)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Button(
                                onClick = {
                                    pendingDeleteConversation = null
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Text("撤回", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }



    iconTarget?.let { target ->
        ConversationIconDialog(
            currentIcon = target.icon,
            onDismiss = { iconTarget = null },
            onConfirm = { icon ->
                onSetConversationIcon(target.id, icon)
                iconTarget = null
            },
        )
    }

    if (showCreateDialog) {
        ChatTitleDialog(
            title = "新建聊天",
            value = titleDraft,
            confirmLabel = "创建",
            groupName = groupDraft,
            existingGroups = existingGroups,
            allowGroup = true,
            onValueChange = { titleDraft = it },
            onGroupChange = { groupDraft = it },
            onDismiss = { showCreateDialog = false },
            onConfirm = {
                val cleanGroup = groupDraft.trim().takeIf { it.isNotEmpty() }
                onCreateConversation(titleDraft.trim(), cleanGroup) { showCreateDialog = false }
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
    setGroupTarget?.let { target ->
        SetGroupDialog(
            currentGroup = target.groupName,
            existingGroups = existingGroups,
            onDismiss = { setGroupTarget = null },
            onConfirm = { newGroup ->
                onSetConversationGroup(target.id, newGroup)
                setGroupTarget = null
            },
        )
    }
    if (showBatchGroupDialog) {
        SetGroupDialog(
            currentGroup = null,
            existingGroups = existingGroups,
            onDismiss = { showBatchGroupDialog = false },
            onConfirm = { newGroup ->
                val targets = selectedIds
                showBatchGroupDialog = false
                isSelectionMode = false
                selectedIds = emptySet()
                onSetConversationsGroup(targets, newGroup)
            },
        )
    }
    renameGroupTarget?.let { oldGroup ->
        RenameGroupDialog(
            oldName = oldGroup,
            onDismiss = { renameGroupTarget = null },
            onConfirm = { newName ->
                onRenameGroup(oldGroup, newName)
                renameGroupTarget = null
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
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除 ${selectedIds.size} 个会话？") },
            text = { Text("将删除所选 ${selectedIds.size} 个会话中的全部消息和图片，且无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets = selectedIds
                        showBatchDeleteDialog = false
                        isSelectionMode = false
                        selectedIds = emptySet()
                        onDeleteConversations(targets)
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: ChatConversation,
    preview: ChatMessage?,
    selected: Boolean,
    isSelectionMode: Boolean,
    isChecked: Boolean,
    canDrag: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    onToggleCheck: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onSetGroup: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onTogglePin: () -> Unit,
    onSetIcon: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isRowHighlighted = (isSelectionMode && isChecked) || (!isSelectionMode && selected)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.selected = isRowHighlighted },
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = if (isSelectionMode) "勾选${conversation.title}" else "打开${conversation.title}",
                    role = if (isSelectionMode) Role.Checkbox else Role.Button,
                    onLongClickLabel = if (isSelectionMode) null else "聊天操作",
                    onClick = {
                        if (isSelectionMode) {
                            onToggleCheck()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = {
                        if (isSelectionMode) {
                            onToggleCheck()
                        } else {
                            onLongClick()
                        }
                    },
                )
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isRowHighlighted) 1.dp else 0.dp,
                    color = if (isRowHighlighted) {
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isSelectionMode) 0.5f else 0.28f)
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(16.dp),
                ),
            colors = ListItemDefaults.colors(
                containerColor = when {
                    isSelectionMode && isChecked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    !isSelectionMode && selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                    conversation.isPinned -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.background
                },
            ),
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { onToggleCheck() },
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Box(Modifier.clip(CircleShape).clickable(enabled = !isSelectionMode, onClickLabel = "自定义聊天图标", onClick = onSetIcon)) {
                        ConversationAvatar(icon = conversation.icon, size = 50.dp)
                    }
                }
            },
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (conversation.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "已置顶",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = conversation.title,
                        fontWeight = if (conversation.isPinned) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!conversation.groupName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        ) {
                            Text(
                                text = conversation.groupName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
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
                if (isSelectionMode) {
                    preview?.let {
                        Text(
                            DateUtils.getRelativeTimeSpanString(it.createdAt).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
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
                            if (canDrag) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "按住拖动排序",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    modifier = dragHandleModifier
                                        .size(28.dp)
                                        .padding(horizontal = 2.dp, vertical = 4.dp),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("自定义图标") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { menuExpanded = false; onSetIcon() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (conversation.isPinned) "取消置顶" else "置顶聊天") },
                                leadingIcon = {
                                    Icon(
                                        if (conversation.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onTogglePin()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("重命名") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("设置分组") },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onSetGroup()
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
                            DropdownMenuItem(
                                text = { Text("导出聊天") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onExport()
                                },
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun NewChatActionButton(
    enabled: Boolean,
    onFastCreate: () -> Unit,
    onLongPressCreate: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .indication(interactionSource, ripple(bounded = false, radius = 24.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = PressInteraction.Press(down.position)
                    scope.launch { interactionSource.emit(press) }
                    val up = try {
                        withTimeout(1000L) {
                            waitForUpOrCancellation()
                        }
                    } catch (e: PointerEventTimeoutCancellationException) {
                        scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPressCreate()
                        waitForUpOrCancellation()
                        null
                    }
                    if (up != null) {
                        scope.launch { interactionSource.emit(PressInteraction.Release(press)) }
                        onFastCreate()
                    } else {
                        scope.launch { interactionSource.emit(PressInteraction.Cancel(press)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "新建聊天",
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun ConversationGroupHeader(
    groupName: String,
    count: Int,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRenameGroup: (() -> Unit)?,
    onDissolveGroup: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCollapse)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isCollapsed) "展开分组" else "折叠分组",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = groupName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (onRenameGroup != null || onDissolveGroup != null) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "分组操作",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (onRenameGroup != null) {
                        DropdownMenuItem(
                            text = { Text("重命名分组") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRenameGroup()
                            },
                        )
                    }
                    if (onDissolveGroup != null) {
                        DropdownMenuItem(
                            text = { Text("解散分组") },
                            leadingIcon = { Icon(Icons.Default.FolderOff, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDissolveGroup()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetGroupDialog(
    currentGroup: String?,
    existingGroups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var inputGroup by remember { mutableStateOf(currentGroup ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置分组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = inputGroup,
                    onValueChange = { inputGroup = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("分组名称") },
                    placeholder = { Text("输入新分组或选择已有分组") },
                )

                if (existingGroups.isNotEmpty()) {
                    Text(
                        "已有分组：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        existingGroups.forEach { group ->
                            FilterChip(
                                selected = inputGroup == group,
                                onClick = { inputGroup = group },
                                label = { Text(group) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = inputGroup.trim().takeIf { it.isNotEmpty() }
                    onConfirm(clean)
                },
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            Row {
                if (!currentGroup.isNullOrBlank()) {
                    TextButton(
                        onClick = { onConfirm(null) },
                    ) {
                        Text("移出分组", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
private fun RenameGroupDialog(
    oldName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newName by remember { mutableStateOf(oldName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名分组") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("新分组名称") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName.trim()) },
                enabled = newName.trim().isNotEmpty() && newName.trim() != oldName,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun ChatTitleDialog(
    title: String,
    value: String,
    confirmLabel: String,
    groupName: String = "",
    existingGroups: List<String> = emptyList(),
    allowGroup: Boolean = false,
    onValueChange: (String) -> Unit,
    onGroupChange: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("聊天名称") },
                )
                if (allowGroup) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = onGroupChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("分组（可选）") },
                        placeholder = { Text("输入或选择分组") },
                    )
                    if (existingGroups.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            existingGroups.forEach { group ->
                                FilterChip(
                                    selected = groupName == group,
                                    onClick = { onGroupChange(group) },
                                    label = { Text(group) },
                                )
                            }
                        }
                    }
                }
            }
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

@Composable
internal fun MessageSearchResultRow(
    item: MessageSearchResultItem,
    keyword: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .testTag("search-result-${item.id}")
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = item.conversationIcon ?: "💬",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = item.conversationTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = (if (item.role == "USER") "用户" else "AI") + " · " + java.time.Instant.ofEpochMilli(item.createdAt)
                        .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            HighlightedText(
                text = item.text,
                keyword = keyword,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
