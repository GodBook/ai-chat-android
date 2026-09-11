package com.example.aichat.ui

import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle


internal fun filterConversations(
    conversations: List<ChatConversation>,
    previews: Map<String, ChatMessage>,
    query: String,
): List<ChatConversation> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return conversations
    return conversations.filter { conversation ->
        conversation.title.contains(normalizedQuery, ignoreCase = true) ||
            previews[conversation.id]?.text?.contains(normalizedQuery, ignoreCase = true) == true
    }
}

@Composable
internal fun HighlightedText(
    text: String,
    keyword: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (keyword.isBlank()) {
        Text(text = text, modifier = modifier, style = style, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        return
    }

    val matchIndex = text.indexOf(keyword, ignoreCase = true)
    if (matchIndex < 0) {
        Text(text = text, modifier = modifier, style = style, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        return
    }

    val startIndex = (matchIndex - 25).coerceAtLeast(0)
    val endIndex = (matchIndex + keyword.length + 35).coerceAtMost(text.length)
    val prefix = if (startIndex > 0) "…" else ""
    val suffix = if (endIndex < text.length) "…" else ""
    val snippet = prefix + text.substring(startIndex, endIndex) + suffix

    val annotated = buildAnnotatedString {
        var cursor = 0
        var foundIdx = snippet.indexOf(keyword, cursor, ignoreCase = true)
        while (foundIdx >= 0) {
            append(snippet.substring(cursor, foundIdx))
            withStyle(
                SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.Bold,
                    background = highlightColor.copy(alpha = 0.15f),
                ),
            ) {
                append(snippet.substring(foundIdx, foundIdx + keyword.length))
            }
            cursor = foundIdx + keyword.length
            foundIdx = snippet.indexOf(keyword, cursor, ignoreCase = true)
        }
        if (cursor < snippet.length) {
            append(snippet.substring(cursor))
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

