package com.example.aichat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aichat.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkbenchState(
    val cards: List<KnowledgeCard> = emptyList(),
    val selectedCardIds: Set<String> = emptySet(),
    val excludedIds: Set<String> = emptySet(),
    val plan: ContextPlan? = null,
    val previewError: String? = null,
    val showContext: Boolean = false,
    val showCards: Boolean = false,
    val allCards: Boolean = false,
    val editor: KnowledgeCard? = null,
    val recordText: String? = null,
    val continuation: Boolean = false,
    val revision: Long = 0,
)

@Composable
private fun WorkbenchPage(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(vertical = 16.dp))
                    TextButton(onClick = close) { Text("关闭") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

@Composable
fun KnowledgeDialogs(state: MainUiState, viewModel: MainViewModel, onNavigateChat: () -> Unit) {
    if (state.isTemporary) return
    val workbench = state.workbench
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportSnapshot by remember { mutableStateOf<String?>(null) }
    var exportResult by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val snapshot = exportSnapshot
        exportSnapshot = null
        if (uri != null) {
            if (snapshot == null) exportResult = "导出内容已失效，请重新导出"
            else scope.launch {
                exportResult = try {
                    withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) } ?: error("无法写入文件") }
                    "已保存 Markdown"
                } catch (e: Exception) { e.message ?: "保存失败" }
            }
        }
    }
    if (workbench.showContext) WorkbenchPage("本次上下文", viewModel::closeWorkbench) {
        Text("发送前查看的是本地请求计划；开启联网时，搜索资料将在检索完成后加入。")
        workbench.previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val plan = workbench.plan
        if (plan == null && workbench.previewError == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (plan != null) {
            var visibleRounds by rememberSaveable { mutableStateOf(50) }
            Text("模型：${plan.model}", style = MaterialTheme.typography.titleMedium)
            Text("${plan.includedRounds} 轮历史 · ${plan.cards.size} 张结论卡 · ${plan.characterCount} 字符 · ${plan.imageCount} 张图片")
            Text("字符量为客户端统计，并非模型精确 Token 数。")
            ExpandableText("角色设定", plan.systemPrompt.ifBlank { "无" })
            plan.entries.asReversed().take(visibleRounds).forEach { entry ->
                val user = state.messages.firstOrNull { it.id == entry.userId }
                val assistant = state.messages.firstOrNull { it.id == entry.assistantId }
                HorizontalDivider()
                Text(user?.text?.take(140) ?: "历史问题", style = MaterialTheme.typography.titleSmall)
                Text(entry.reason ?: "已携带 · 当前完成版本", color = if (entry.reason == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                entry.note?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (entry.otherAssistantIds.isNotEmpty()) Text("另有 ${entry.otherAssistantIds.size} 个回答版本未携带", style = MaterialTheme.typography.labelSmall)
                Row {
                    Checkbox(checked = entry.userId !in workbench.excludedIds, enabled = !state.isAnyWorking, onCheckedChange = { viewModel.toggleExcluded(entry.userId) })
                    Text("允许携带此轮", modifier = Modifier.padding(top = 12.dp))
                }
                ExpandableText("查看本轮", "用户：${user?.text.orEmpty()}\n\nAI：${assistant?.text ?: "未完成或已删除"}")
            }
            if (plan.entries.size > visibleRounds) TextButton({ visibleRounds += 50 }) { Text("加载更早的历史轮次") }
            HorizontalDivider()
            ExpandableText("本次问题与附件资料", plan.messages.lastOrNull()?.text.orEmpty())
            if (plan.cards.isNotEmpty()) ExpandableText("选定结论卡", knowledgeMarkdown(plan.cards))
            Text("联网搜索：${if (state.webSearchActive) "开启，等待检索后补充" else "关闭"}")
        }
        Spacer(Modifier.height(12.dp))
    }
    if (workbench.showCards && workbench.editor == null && !workbench.continuation) {
        var query by rememberSaveable { mutableStateOf("") }
        var kind by rememberSaveable { mutableStateOf("全部") }
        var all by rememberSaveable(workbench.allCards) { mutableStateOf(workbench.allCards) }
        var visibleCards by rememberSaveable(query, kind, all) { mutableStateOf(50) }
        var delete by remember { mutableStateOf<KnowledgeCard?>(null) }
        WorkbenchPage("结论卡", viewModel::closeWorkbench) {
            Text("主动保存，按需发送。AI 回答摘录的出处不代表事实已经核验。")
            OutlinedTextField(query, { query = it }, label = { Text("搜索标题或正文") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row {
                FilterChip(all, { all = !all }, label = { Text(if (all) "全部聊天" else "当前聊天") })
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { viewModel.newCard() }) { Text("新建") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("全部", "结论", "约束", "待办").forEach { value -> FilterChip(kind == value, { kind = value }, label = { Text(value) }) }
            }
            val cards = workbench.cards.filter { (all || it.conversationId == state.selectedConversationId) && (kind == "全部" || it.kind == kind) && (query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true)) }
            if (cards.isEmpty()) Text("还没有匹配的结论卡。可以从 AI 回答的“更多”中保存，或手动新建。")
            cards.take(visibleCards).forEach { card ->
                HorizontalDivider()
                Text("${card.kind} · ${card.title}", style = MaterialTheme.typography.titleMedium)
                Text(card.body)
                Text("${card.sourceConversationTitle} · 修订 ${card.revision}${if (card.sourceMessageId != null && card.body != card.sourceExcerpt) " · 已编辑" else ""}", style = MaterialTheme.typography.labelMedium)
                if (card.sourceMessageId != null) {
                    TextButton(onClick = {
                        if (card.conversationId != null) { viewModel.closeWorkbench(); viewModel.jumpToMessage(card.conversationId, card.sourceMessageId); onNavigateChat() }
                        else exportResult = "原会话已删除；保存时摘录仍可查看"
                    }, enabled = !state.isAnyWorking) { Text("查看原文") }
                    ExpandableText("保存时摘录", card.sourceExcerpt)
                } else Text("用户记录", style = MaterialTheme.typography.labelSmall)
                Row {
                    TextButton({ viewModel.editCard(card) }) { Text("编辑") }
                    TextButton({ delete = card }) { Text("删除") }
                    TextButton({ exportSnapshot = knowledgeMarkdown(listOf(card)); exporter.launch("${card.title.replace(Regex("[^\\p{L}\\p{N}_-]"), "_")}.md") }) { Text("导出") }
                }
                FilterChip(card.id in workbench.selectedCardIds, { viewModel.toggleCard(card.id) }, enabled = !state.isAnyWorking,
                    label = { Text(if (card.id in workbench.selectedCardIds) "已用于下次提问 · 点击移除" else "用于下次提问") })
            }
            if (cards.size > visibleCards) TextButton({ visibleCards += 50 }) { Text("加载更多结论卡") }
            HorizontalDivider()
            Text("已选 ${workbench.selectedCardIds.size} 张，发送前可在上下文中核对。")
            Button(onClick = viewModel::startContinuation, enabled = workbench.selectedCardIds.isNotEmpty() && !state.isAnyWorking, modifier = Modifier.fillMaxWidth()) { Text("带到新聊天") }
            exportResult?.let { Text(it) }
            Spacer(Modifier.height(12.dp))
        }
        delete?.let { card -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("删除结论卡？") },
            text = { Text("将删除卡片及请求清单中的卡片快照。已发送的聊天正文、已导出的文件和备份仍保留。") },
            confirmButton = { TextButton({ viewModel.deleteCard(card.id); delete = null }) { Text("删除") } },
            dismissButton = { TextButton({ delete = null }) { Text("取消") } }) }
    }
    workbench.editor?.let { original ->
        var title by rememberSaveable(original.id) { mutableStateOf(original.title) }
        var body by rememberSaveable(original.id) { mutableStateOf(original.body) }
        var kind by rememberSaveable(original.id) { mutableStateOf(original.kind) }
        WorkbenchPage("保存结论卡", viewModel::closeWorkbench) {
            Text("保留需要的内容后保存；保存不会调用模型。")
            OutlinedTextField(title, { title = it }, label = { Text("标题 ${title.length}/40") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("结论", "约束", "待办").forEach { value -> FilterChip(kind == value, { kind = value }, label = { Text(value) }) } }
            OutlinedTextField(body, { body = it }, label = { Text("正文 ${body.length}/2,000") }, modifier = Modifier.fillMaxWidth(), minLines = 8,
                isError = body.length > 2_000, supportingText = { if (body.length > 2_000) Text("请缩短或选取需要的片段，原文不会被修改") })
            Button({ viewModel.saveCard(original.copy(title = title.trim(), body = body.trim(), kind = kind)) },
                enabled = title.isNotBlank() && title.length <= 40 && body.isNotBlank() && body.length <= 2_000,
                modifier = Modifier.fillMaxWidth()) { Text("保存") }
            Spacer(Modifier.height(12.dp))
        }
    }
    workbench.recordText?.let { text -> WorkbenchPage("当次请求清单", viewModel::closeWorkbench) {
        Text("历史消息被删除后，其正文不再显示。清单不保证原服务仍支持重放。")
        androidx.compose.foundation.text.selection.SelectionContainer { Text(text) }
        Spacer(Modifier.height(16.dp))
    } }
    if (workbench.continuation) {
        var goal by rememberSaveable { mutableStateOf("") }
        val cards = workbench.cards.filter { it.id in workbench.selectedCardIds }
        WorkbenchPage("带到新聊天", viewModel::closeWorkbench) {
            Text("将所选成果填入新聊天草稿，编辑后手动发送。")
            Text("服务：${state.activeProviderProfile?.name ?: "当前全局配置"} · 模型：${state.activePersona?.preferredModel ?: state.activeProviderProfile?.defaultModel ?: state.config.model}")
            Text("角色：${state.activePersona?.name ?: "无"}。需要更换时可返回聊天的服务商／角色设置。")
            OutlinedTextField(goal, { if (it.length <= 2_000) goal = it }, label = { Text("下一步目标") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Text(knowledgeMarkdown(cards))
            Button({ viewModel.createContinuation(goal, onNavigateChat) }, enabled = cards.isNotEmpty() && !state.isAnyWorking, modifier = Modifier.fillMaxWidth()) { Text("创建并填入草稿") }
            TextButton({ exportSnapshot = ContextPlanner.withCards(goal, cards); exporter.launch("续聊包.md") }) { Text("导出续聊包") }
            exportResult?.let { Text(it) }
        }
    }
}

@Composable
private fun ExpandableText(title: String, body: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    TextButton({ expanded = !expanded }) { Text(if (expanded) "收起$title" else title) }
    if (expanded) androidx.compose.foundation.text.selection.SelectionContainer { Text(body) }
}
