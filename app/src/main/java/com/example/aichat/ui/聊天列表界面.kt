package com.example.aichat.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageStatus

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ContactsScreen(
    conversations: List<ChatConversation>,
    previews: Map<String, ChatMessage>,
    selectedConversationId: String?,
    isAnyWorking: Boolean,
    onOpenChat: (String) -> Unit,
    onCreateConversation: (String, () -> Unit) -> Unit,
    onRenameConversation: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteConversations: (Set<String>) -> Unit,
    onExportConversation: (String) -> Unit,
    onExportConversations: (Set<String>) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatConversation?>(null) }
    var titleDraft by rememberSaveable { mutableStateOf("新聊天") }
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showBatchDeleteDialog by rememberSaveable { mutableStateOf(false) }
    val visibleConversations = filterConversations(conversations, previews, searchQuery)

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
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    if (searchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                            placeholder = { Text("搜索聊天名称或最近消息") },
                            singleLine = true,
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        searchQuery = ""
                                        searchOpen = false
                                    },
                                ) { Text("关闭") }
                            },
                        )
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
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("正在准备聊天列表…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (visibleConversations.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("没有匹配的聊天", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visibleConversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        preview = previews[conversation.id],
                        selected = conversation.id == selectedConversationId,
                        isSelectionMode = isSelectionMode,
                        isChecked = conversation.id in selectedIds,
                        onToggleCheck = {
                            selectedIds = if (conversation.id in selectedIds) {
                                selectedIds - conversation.id
                            } else {
                                selectedIds + conversation.id
                            }
                        },
                        onClick = { onOpenChat(conversation.id) },
                        onLongClick = {
                            isSelectionMode = true
                            selectedIds = setOf(conversation.id)
                        },
                        onRename = {
                            titleDraft = conversation.title
                            renameTarget = conversation
                        },
                        onDelete = { deleteTarget = conversation },
                        onExport = { onExportConversation(conversation.id) },
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
    onToggleCheck: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
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
                    AiAvatar(size = 50.dp)
                }
            },
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
