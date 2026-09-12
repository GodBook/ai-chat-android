package com.example.aichat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aichat.data.model.MessageSearchFilter

@Composable
fun MessageSearchFilters(filter: MessageSearchFilter, onChange: (MessageSearchFilter) -> Unit) {
    var roleOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    val roles = listOf(null to "全部角色", "USER" to "用户", "ASSISTANT" to "AI")
    val ranges = listOf(0 to "全部时间", 7 to "近 7 天", 30 to "近 30 天", 90 to "近 90 天")
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            FilterChip(selected = filter.role != null, onClick = { roleOpen = true }, label = { Text("${roles.firstOrNull { it.first == filter.role }?.second ?: "全部角色"} ▾") })
            DropdownMenu(roleOpen, { roleOpen = false }) {
                roles.forEach { (role, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { roleOpen = false; onChange(filter.copy(role = role)) }) }
            }
        }
        Box {
            FilterChip(selected = filter.days != 0, onClick = { timeOpen = true }, label = { Text("${ranges.firstOrNull { it.first == filter.days }?.second ?: "全部时间"} ▾") })
            DropdownMenu(timeOpen, { timeOpen = false }) {
                ranges.forEach { (days, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { timeOpen = false; onChange(filter.copy(days = days)) }) }
            }
        }
    }
}

@Composable
fun ConversationSearchDialog(state: MainUiState, onQuery: (String) -> Unit,
    onFilter: (MessageSearchFilter) -> Unit, onMore: () -> Unit, onJump: (String, String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.9f), shape = MaterialTheme.shapes.large) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("搜索当前聊天", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                OutlinedTextField(value = state.deepSearchQuery, onValueChange = onQuery, singleLine = true,
                    placeholder = { Text("输入消息或文件资料中的关键词") }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                MessageSearchFilters(state.searchFilter, onFilter)
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.deepSearchQuery.isBlank()) item { Text("输入关键词开始搜索，支持中文及 %、_ 等字符。") }
                    items(state.deepSearchResults, key = { it.id }) { item ->
                        MessageSearchResultRow(item, state.deepSearchQuery.trim()) { onJump(item.conversationId, item.id); onDismiss() }
                    }
                    if (state.isDeepSearching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    if (state.searchError != null) item {
                        Text(state.searchError, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onQuery(state.deepSearchQuery) }) { Text("重试搜索") }
                    }
                    if (!state.isDeepSearching && state.searchError == null && state.deepSearchQuery.isNotBlank() && state.deepSearchResults.isEmpty()) item { Text("没有匹配的消息") }
                    if (state.searchHasMore) item {
                        TextButton(onClick = onMore, enabled = !state.isDeepSearching) { Text("加载更多消息（已显示 ${state.deepSearchResults.size} 条）") }
                    }
                }
            }
        }
    }
}
