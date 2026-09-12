package com.example.aichat.ui.export

import android.net.Uri
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.aichat.ui.MarkdownBlockModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

internal class PendingBlockExports : ViewModel() {
    val contents = mutableStateMapOf<String, String>()
}

internal suspend fun writeExport(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        ?: throw java.io.IOException("无法打开目标文件")
}

@Composable
internal fun SaveBlockButton(label: String, filename: String, content: String, mime: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Keep a snapshot: a streaming block may change while the system picker is open.
    val pendingStore: PendingBlockExports = viewModel()
    val exportId = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val pending = pendingStore.contents[exportId]
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { uri ->
        val snapshot = pendingStore.contents.remove(exportId)
        if (uri != null && snapshot != null) scope.launch(Dispatchers.Main.immediate) {
            busy = true
            try {
                writeExport(context, uri, snapshot)
                Toast.makeText(context, "文件已保存", Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { Toast.makeText(context, "保存失败：${failure.message ?: "请重新选择位置"}", Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
        else if (uri != null) Toast.makeText(context, "导出内容已失效，请重新导出", Toast.LENGTH_LONG).show()
    }
    TextButton(onClick = {
        pendingStore.contents[exportId] = content
        try { launcher.launch(filename) }
        catch (failure: Exception) { pendingStore.contents.remove(exportId); Toast.makeText(context, "无法打开文件保存器", Toast.LENGTH_LONG).show() }
    }, enabled = !busy && pending == null, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (busy) "保存中…" else label) }
}

@Composable
internal fun TableExportActions(table: MarkdownBlockModel.Table) {
    val rows = remember(table) { table.rows.map { row -> row.cells.map { cell -> cell.spans.joinToString("") { it.text } } } }
    val clipboard = LocalClipboardManager.current
    val csv = remember(rows) { BlockExportFormatter.csv(rows) }
    var copied by remember(table) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1500); copied = false } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { clipboard.setText(AnnotatedString(BlockExportFormatter.tsv(rows))); copied = true }) { Text(if (copied) "已复制表格" else "复制表格") }
        SaveBlockButton("导出 CSV", "table.csv", csv, "text/csv")
    }
}
