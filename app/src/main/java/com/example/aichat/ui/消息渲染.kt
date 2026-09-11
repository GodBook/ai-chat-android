package com.example.aichat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import com.example.aichat.data.network.WebSearchResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AiAvatar(size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun ThinkingCard(
    thinkingContent: String,
    isStreaming: Boolean,
    durationMs: Long?,
    autoCollapse: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(isStreaming, autoCollapse) {
        mutableStateOf(if (isStreaming) true else !autoCollapse)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = when {
                            isStreaming -> "正在深度思考…"
                            durationMs != null -> {
                                val seconds = (durationMs / 1000).coerceAtLeast(1)
                                "已深度思考 (${seconds}秒)"
                            }
                            else -> "已深度思考"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (expanded && thinkingContent.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))
                SelectionContainer {
                    Text(
                        text = thinkingContent.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun WebSearchResultsCard(
    results: List<WebSearchResult>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (results.isEmpty()) "未找到可核验的网络资料" else "已获取 ${results.size} 条网络参考资料",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起检索结果" else "展开检索结果",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    results.forEachIndexed { index, item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching { uriHandler.openUri(item.url) }
                                },
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        modifier = Modifier.size(18.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (item.snippet.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatMessageTime(createdAt: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(createdAt))
}

@Composable
internal fun MarkdownText(markdown: String) {
    val blocks = remember(markdown) { MarkdownDocumentParser.parse(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> MarkdownBlock(block) }
    }
}

@Composable
private fun MarkdownBlock(block: MarkdownBlockModel) {
    when (block) {
        is MarkdownBlockModel.Paragraph -> MarkdownInlineText(block.spans)
        is MarkdownBlockModel.Heading -> Text(
            text = markdownAnnotatedString(block.spans),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.SemiBold,
        )
        is MarkdownBlockModel.CodeBlock -> MarkdownCodeBlock(block)
        is MarkdownBlockModel.MathBlock -> MarkdownMathBlock(block)
        is MarkdownBlockModel.Quote -> {
            val quoteBorderColor = MaterialTheme.colorScheme.primary
            val quoteBgColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(quoteBgColor)
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(quoteBorderColor, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    block.blocks.forEach { nested -> MarkdownBlock(nested) }
                }
            }
        }
        is MarkdownBlockModel.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = if (block.ordered) "${block.startNumber + index}." else "•",
                        modifier = Modifier.width(28.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        item.forEach { nested -> MarkdownBlock(nested) }
                    }
                }
            }
        }
        is MarkdownBlockModel.Table -> MarkdownTable(block)
        MarkdownBlockModel.Divider -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MarkdownInlineText(spans: List<MarkdownSpanModel>) {
    Text(
        text = markdownAnnotatedString(spans),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun markdownAnnotatedString(spans: List<MarkdownSpanModel>): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        spans.forEach { span ->
            val style = when {
                span.math -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                )
                else -> SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    background = if (span.code) codeBackground else Color.Unspecified,
                )
            }
            val appendStyledText: AnnotatedString.Builder.() -> Unit = {
                withStyle(style) { append(span.text) }
            }
            val link = span.linkUrl
            if (link == null) {
                appendStyledText()
            } else {
                withLink(
                    LinkAnnotation.Url(
                        url = link,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ),
                    ),
                    block = appendStyledText,
                )
            }
        }
    }
}

@Composable
private fun MarkdownMathBlock(block: MarkdownBlockModel.MathBlock) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "∑ 公式",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(block.formula.trim())) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "复制公式",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    text = block.formula.trim(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlockModel.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(block.code) { mutableStateOf(block.code.length < 4_000) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.language ?: "代码",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(block.code.trimEnd())) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制代码", modifier = Modifier.size(17.dp))
                }
                if (block.code.length >= 4_000) {
                    androidx.compose.material3.TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "收起" else "展开")
                    }
                }
            }
            Text(
                if (expanded) block.code.trimEnd() else block.code.trimEnd().take(1_800) + "\n…",
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(end = 10.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlockModel.Table) {
    val columnCount = table.rows.maxOfOrNull { it.cells.size } ?: return
    val cellWidth = if (columnCount <= 2) 142.dp else 126.dp
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        table.rows.forEach { row ->
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                repeat(columnCount) { columnIndex ->
                    val cell = row.cells.getOrNull(columnIndex)
                    val header = cell?.header == true
                    Surface(
                        color = if (header) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            Color.Transparent
                        },
                        modifier = Modifier
                            .width(cellWidth)
                            .fillMaxHeight()
                            .heightIn(min = 44.dp)
                            .border(0.5.dp, borderColor),
                    ) {
                        Text(
                            text = markdownAnnotatedString(cell?.spans.orEmpty()),
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (header) FontWeight.SemiBold else null,
                            textAlign = when (cell?.alignment) {
                                MarkdownTableAlignment.CENTER -> TextAlign.Center
                                MarkdownTableAlignment.END -> TextAlign.End
                                else -> TextAlign.Start
                            },
                        )
                    }
                }
            }
        }
    }
}
