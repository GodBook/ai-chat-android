package com.example.aichat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aichat.BuildConfig
import com.example.aichat.data.update.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Composable
internal fun ReleaseHistoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(emptyList<ReleaseNote>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf(BuildConfig.VERSION_NAME) }
    val scope = rememberCoroutineScope()
    val client = remember { ReleaseHistoryClient() }
    LaunchedEffect(Unit) {
        try {
            entries = withContext(Dispatchers.IO) {
                context.assets.open("release-history.json").bufferedReader().use { Json.decodeFromString<List<ReleaseNote>>(it.readText()) }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "本地版本记录读取失败，请点击刷新重试" }
        finally { loading = false }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("历史更新") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("当前版本 ${BuildConfig.VERSION_NAME} · 点击版本查看更新内容", style = MaterialTheme.typography.bodySmall)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.version }) { release ->
                        Column {
                            Row(
                                Modifier.fillMaxWidth().clickable { expanded = if (expanded == release.version) "" else release.version }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("v${release.version}" + if (release.version == BuildConfig.VERSION_NAME) " · 当前版本" else "", fontWeight = FontWeight.SemiBold)
                                    Text(release.publishedAt.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(if (expanded == release.version) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded == release.version) "收起" else "展开")
                            }
                            if (expanded == release.version) {
                                Box(Modifier.padding(bottom = 12.dp)) { MarkdownText(release.notes) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        dismissButton = {
            TextButton(enabled = !loading, onClick = {
                scope.launch {
                    loading = true
                    error = null
                    try { entries = mergeReleaseHistory(entries, client.fetch()) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "刷新失败，已保留本地记录。请检查网络后重试。" }
                    finally { loading = false }
                }
            }) { Text("联网刷新") }
        },
    )
}
