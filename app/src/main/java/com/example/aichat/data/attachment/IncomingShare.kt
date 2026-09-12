package com.example.aichat.data.attachment

import android.content.Intent
import android.net.Uri

data class IncomingShare(val text: String, val uris: List<Uri>) {
    companion object {
        @Suppress("DEPRECATION")
        fun from(intent: Intent, ownPackage: String): IncomingShare? {
            if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return null
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
            require(text.length <= 24_000) { "分享文字过长，请作为文件分享" }
            val streams = if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            } else listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            val clip = intent.clipData
            require((clip?.itemCount ?: 0) <= 8 && streams.size <= 8) { "一次最多分享 8 个项目" }
            val uris = (streams + (0 until (clip?.itemCount ?: 0)).mapNotNull { clip?.getItemAt(it)?.uri }).distinct()
            require(uris.size <= 8) { "一次最多分享 8 个项目" }
            require(uris.all { it.scheme == "content" && it.authority?.startsWith(ownPackage) != true }) { "分享来源无效，请通过系统文件选择器导入" }
            require(text.isNotBlank() || uris.isNotEmpty()) { "没有收到可用的分享内容" }
            return IncomingShare(text, uris)
        }
    }
}

data class SharePreview(
    val text: String,
    val images: List<String> = emptyList(),
    val documents: List<DocumentAttachment> = emptyList(),
    val errors: List<String> = emptyList(),
    val loading: Boolean = false,
)

data class AttachmentComposerState(
    val documents: List<DocumentAttachment> = emptyList(),
    val importing: Boolean = false,
)

data class SharedDraft(val conversationId: String, val text: String, val id: String = java.util.UUID.randomUUID().toString())
