package com.example.aichat.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.aichat.data.network.WebSearchResult
import com.example.aichat.data.network.safeSourceUrl

internal fun openSearchSource(context: Context, url: String) {
    val safe = safeSourceUrl(url)
    if (safe == null) { Toast.makeText(context, "此来源链接无效", Toast.LENGTH_SHORT).show(); return }
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)).addCategory(Intent.CATEGORY_BROWSABLE)) }
    catch (_: Exception) { Toast.makeText(context, "没有可打开链接的浏览器，请安装浏览器后重试", Toast.LENGTH_LONG).show() }
}

/** Link only numbered citations present in the actual search result list. */
internal fun linkSearchCitations(markdown: String, sources: List<WebSearchResult>): String {
    var inFence = false
    return markdown.lineSequence().joinToString("\n") { line ->
        if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) { inFence = !inFence; line }
        else if (inFence) line else line.split('`').mapIndexed { index, segment ->
            if (index % 2 == 1) segment else Regex("\\[(?:来源\\s*)?(\\d+)\\](?![\\[(])").replace(segment) { match ->
                val source = sources.getOrNull((match.groupValues[1].toIntOrNull() ?: 0) - 1)
                val url = source?.url?.let(::safeSourceUrl)
                val destination = url?.replace("(", "%28")?.replace(")", "%29")
                if (destination == null) match.value else "[${match.value.removeSurrounding("[", "]")}]($destination)"
            }
        }.joinToString("`")
    }
}
