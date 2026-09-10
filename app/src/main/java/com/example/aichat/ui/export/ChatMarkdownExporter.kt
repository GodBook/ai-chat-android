package com.example.aichat.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChatMarkdownExporter {

    fun formatMarkdown(
        conversationTitle: String,
        messages: List<ChatMessage>,
        modelName: String,
        exportTimeMs: Long = System.currentTimeMillis(),
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(exportTimeMs))

        val sb = StringBuilder()
        sb.append("# ").append(conversationTitle).append("\n\n")
        sb.append("> 导出时间：").append(formattedDate).append("  \n")
        sb.append("> 所用模型：").append(modelName).append("\n\n")
        sb.append("---\n\n")

        for (message in messages) {
            when (message.role) {
                MessageRole.USER -> {
                    sb.append("### 👤 用户\n\n")
                    if (message.text.isNotBlank()) {
                        sb.append(message.text.trim()).append("\n\n")
                    }
                    if (message.imagePaths.isNotEmpty()) {
                        sb.append("*[附带图片 ").append(message.imagePaths.size).append(" 张]*\n\n")
                    }
                }
                MessageRole.ASSISTANT -> {
                    sb.append("### 🤖 AI (").append(modelName).append(")\n\n")
                    if (!message.thinkingContent.isNullOrBlank()) {
                        val durationSeconds = (message.thinkingDurationMs ?: 0L) / 1000L
                        val durationText = if (durationSeconds > 0) "（用时 ${durationSeconds} 秒）" else ""
                        sb.append("<details>\n")
                        sb.append("<summary>🧠 思考过程").append(durationText).append("</summary>\n\n")
                        sb.append(message.thinkingContent.trim()).append("\n\n")
                        sb.append("</details>\n\n")
                    }
                    if (message.text.isNotBlank()) {
                        sb.append(message.text.trim()).append("\n\n")
                    }
                }
            }
            sb.append("---\n\n")
        }

        sb.append("*— 对话由 AI BOTOY 导出 —*\n")
        return sb.toString()
    }

    suspend fun exportAndShareMarkdown(
        context: Context,
        conversationTitle: String,
        messages: List<ChatMessage>,
        modelName: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val mdContent = formatMarkdown(conversationTitle, messages, modelName)
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTitle = conversationTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(30)
            val file = File(exportsDir, "${safeTitle}_${System.currentTimeMillis()}.md")
            file.writeText(mdContent, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$conversationTitle - Markdown")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "导出 Markdown 对话")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            uri
        }
    }
}
