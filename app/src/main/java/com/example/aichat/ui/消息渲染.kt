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
        is MarkdownBlockModel.Quote -> Column(
            modifier = Modifier
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            block.blocks.forEach { nested -> MarkdownBlock(nested) }
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
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
            )
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
private fun MarkdownCodeBlock(block: MarkdownBlockModel.CodeBlock) {
    val clipboard = LocalClipboardManager.current
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
            }
            Text(
                block.code.trimEnd(),
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
