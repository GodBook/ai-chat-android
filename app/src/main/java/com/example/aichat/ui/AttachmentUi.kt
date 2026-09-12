package com.example.aichat.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.example.aichat.data.attachment.DocumentAttachment
import com.example.aichat.data.attachment.DocumentText
import com.example.aichat.data.attachment.SharePreview
import com.example.aichat.data.model.ChatConversation
import coil3.compose.AsyncImage

@Composable
fun DocumentChips(files: List<DocumentAttachment>, onRemove: ((Int) -> Unit)? = null) {
    var preview by remember(files) { mutableStateOf<DocumentAttachment?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        files.forEachIndexed { index, file ->
            InputChip(
                selected = false,
                onClick = { preview = file },
                label = {
                    Column(Modifier.widthIn(max = 190.dp).padding(vertical = 4.dp)) {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${file.text.length} 字符 · ${if (file.notice != null) "部分内容 / 点按查看" else "点按预览"}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                trailingIcon = if (onRemove == null) null else ({
                    IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, "移除文件 ${file.name}", Modifier.size(16.dp))
                    }
                }),
            )
        }
    }
    preview?.let { file ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(file.name) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    file.notice?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(12.dp)) }
                    Text("发送时仅使用以下提取文字，不包含原文件中的图片。", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(12.dp))
                    SelectionContainer { Text(file.text, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("关闭") } },
        )
    }
}

@Composable
fun DocumentMessageText(text: String) {
    val markerIndex = remember(text) { text.indexOf(DocumentText.MARKER) }
    if (markerIndex < 0) {
        SelectionContainer { MarkdownText(markdown = text) }
        return
    }
    var showPreview by remember(text) { mutableStateOf(false) }
    SelectionContainer { MarkdownText(markdown = text.substring(0, markerIndex)) }
    TextButton(onClick = { showPreview = true }) { Text("查看随消息保存的文件资料") }
    if (showPreview) AlertDialog(
        onDismissRequest = { showPreview = false },
        title = { Text("文件资料") },
        text = {
            SelectionContainer {
                Text(text.substring(markerIndex + DocumentText.MARKER.length),
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { showPreview = false }) { Text("关闭") } },
    )
}

@Composable
fun IncomingShareDialog(
    share: SharePreview,
    conversations: List<ChatConversation>,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onAccept: (String?) -> Unit,
) {
    var target by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!share.loading) onDismiss() },
        title = { Text("接收分享") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("先导入草稿，确认后手动发送给模型。链接会作为文字导入，不自动读取网页。")
                if (share.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在处理分享…")
                }
                if (share.text.isNotBlank()) Text(share.text.take(1000), maxLines = 8, overflow = TextOverflow.Ellipsis)
                if (share.images.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())) {
                    share.images.forEach { AsyncImage(model = chatImageModel(it), contentDescription = "分享图片", modifier = Modifier.size(72.dp).padding(4.dp)) }
                }
                DocumentChips(share.documents)
                share.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, enabled = !share.loading) {
                        Text(target?.let { id -> conversations.firstOrNull { it.id == id }?.title } ?: "新建分享问答")
                    }
                    DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 240.dp)) {
                        DropdownMenuItem(text = { Text("新建分享问答") }, onClick = { target = null; expanded = false })
                        conversations.forEach { conversation ->
                            DropdownMenuItem(text = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { target = conversation.id; expanded = false })
                        }
                    }
                }
                Text("导入会替换当前尚未发送的草稿和附件。", style = MaterialTheme.typography.labelSmall)
                if (isWorking) Text("请先取消此窗口并停止生成，再重新分享。", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAccept(target) }, enabled = !share.loading && !isWorking &&
                (share.text.isNotBlank() || share.images.isNotEmpty() || share.documents.isNotEmpty())) { Text("导入草稿") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !share.loading) { Text("取消") } },
    )
}
