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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
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

    val infiniteTransition = rememberInfiniteTransition(label = "thinkingCardPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sparkleAlpha",
    )

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
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                if (isStreaming) alpha = pulseAlpha
                            },
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
            AnimatedVisibility(
                visible = expanded && thinkingContent.isNotBlank(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
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
}

@Composable
internal fun WebSearchLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "searchIndicator")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "globeRotation",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f * pulseAlpha),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = rotation },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "正在联网搜索…",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "正在检索全网最新参考资料与数据",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun AiThinkingLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinkingLoading")
    val dotScale1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 0, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val dotScale2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val dotScale3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(vertical = 4.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "思考中",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer { scaleX = dotScale1; scaleY = dotScale1 }
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer { scaleX = dotScale2; scaleY = dotScale2 }
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Box(
                Modifier
                    .size(5.dp)
                    .graphicsLayer { scaleX = dotScale3; scaleY = dotScale3 }
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

@Composable
internal fun WebSearchResultsCard(
    results: List<WebSearchResult>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val sourceContext = androidx.compose.ui.platform.LocalContext.current

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
                                    openSearchSource(sourceContext, item.url)
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
                                Text(
                                    text = item.url + (item.publishedAt?.let { "\n发布信息：$it" } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                androidx.compose.material3.TextButton(onClick = { openSearchSource(sourceContext, item.url) }) {
                                    Text("查看来源 ${index + 1} ↗")
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
        is MarkdownBlockModel.Heading -> {
            val topSpace = when (block.level) {
                1 -> 12.dp
                2 -> 10.dp
                3 -> 7.dp
                else -> 5.dp
            }
            Column(modifier = Modifier.padding(top = topSpace, bottom = 2.dp)) {
                Text(
                    text = markdownAnnotatedString(block.spans),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, lineHeight = 25.sp)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp, lineHeight = 22.sp)
                        3 -> MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp)
                        else -> MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (block.level == 1) {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    )
                }
            }
        }
        is MarkdownBlockModel.CodeBlock -> MarkdownCodeBlock(block)
        is MarkdownBlockModel.MathBlock -> MarkdownMathBlock(block)
        is MarkdownBlockModel.Quote -> {
            val quoteBorderColor = MaterialTheme.colorScheme.primary
            val quoteBgColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(quoteBgColor)
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .fillMaxHeight()
                        .background(quoteBorderColor, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp, top = 3.dp, bottom = 3.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    block.blocks.forEach { nested -> MarkdownBlock(nested) }
                }
            }
        }
        is MarkdownBlockModel.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item ->
                val firstParagraph = item.firstOrNull() as? MarkdownBlockModel.Paragraph
                val firstSpan = firstParagraph?.spans?.firstOrNull()
                val taskMatch = if (!block.ordered && firstSpan != null) {
                    Regex("""^\[([ xX])\]\s+(.*)$""").find(firstSpan.text)
                } else null

                if (taskMatch != null && firstParagraph != null && firstSpan != null) {
                    val isChecked = taskMatch.groupValues[1].lowercase() == "x"
                    val remainingText = taskMatch.groupValues[2]
                    val updatedSpans = listOf(firstSpan.copy(text = remainingText)) + firstParagraph.spans.drop(1)
                    val updatedItem = listOf(MarkdownBlockModel.Paragraph(updatedSpans)) + item.drop(1)

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 1.dp),
                    ) {
                        Icon(
                            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (isChecked) "已完成" else "未完成",
                            tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(17.dp).padding(top = 2.5.dp),
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            updatedItem.forEach { nested -> MarkdownBlock(nested) }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (block.ordered) {
                            Text(
                                text = "${block.startNumber + index}.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier.widthIn(min = 20.dp),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 9.dp, start = 4.dp, end = 2.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item.forEach { nested -> MarkdownBlock(nested) }
                        }
                    }
                }
            }
        }
        is MarkdownBlockModel.Table -> MarkdownTable(block)
        MarkdownBlockModel.Divider -> HorizontalDivider(
            thickness = 0.8.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun MarkdownInlineText(spans: List<MarkdownSpanModel>) {
    Text(
        text = markdownAnnotatedString(spans),
        style = MaterialTheme.typography.bodyMedium.copy(
            lineHeight = 22.5.sp,
            letterSpacing = 0.2.sp,
            fontSize = 14.5.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun markdownAnnotatedString(spans: List<MarkdownSpanModel>): AnnotatedString {
    val context = androidx.compose.ui.platform.LocalContext.current
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val linkColor = MaterialTheme.colorScheme.primary
    val isDark = isSystemInDarkTheme()
    return buildAnnotatedString {
        spans.forEach { span ->
            val textDecoration = when {
                span.strikethrough -> TextDecoration.LineThrough
                else -> null
            }
            val style = when {
                span.math -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    textDecoration = textDecoration,
                )
                span.code -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xFF93C5FD) else Color(0xFF1D4ED8),
                    background = codeBackground,
                    textDecoration = textDecoration,
                )
                else -> SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = textDecoration,
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
                        linkInteractionListener = { openSearchSource(context, link) },
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium,
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
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                    onClick = {
                        clipboard.setText(AnnotatedString(block.formula.trim()))
                        copied = true
                        scope.launch {
                            delay(1500)
                            copied = false
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    if (copied) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已复制",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制公式",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
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
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private object CodeSyntaxHighlighter {
    private val KEYWORDS = setOf(
        // Kotlin / Java
        "fun", "val", "var", "class", "interface", "object", "enum", "sealed", "data", "open", "override",
        "private", "public", "protected", "internal", "import", "package", "return", "if", "else", "when",
        "for", "while", "do", "try", "catch", "finally", "throw", "super", "this", "new", "void", "int",
        "boolean", "float", "double", "long", "short", "byte", "char", "abstract", "final", "native",
        "synchronized", "volatile", "transient", "extends", "implements", "instanceof", "suspend",
        // Python
        "def", "from", "as", "elif", "in", "is", "not", "and", "or", "lambda", "pass", "raise", "with",
        "yield", "global", "nonlocal", "assert",
        // JS / TS
        "function", "const", "let", "export", "type", "async", "await", "typeof", "of",
        // Go / Rust / C / C++
        "func", "struct", "impl", "trait", "mut", "pub", "fn", "match", "use", "mod", "auto", "constexpr",
        "include", "typedef", "template", "typename", "namespace", "using", "goto", "sizeof",
        // SQL
        "select", "from", "where", "insert", "into", "values", "update", "set", "delete", "join",
        "left", "right", "inner", "outer", "group", "by", "order", "having", "limit", "create", "table",
        "drop", "alter", "primary", "key", "distinct", "union",
        // Bash
        "echo", "then", "fi", "esac", "case"
    )

    private val LITERAL_BOOLEAN_NULL = setOf(
        "true", "false", "null", "nil", "None", "True", "False", "undefined", "NaN"
    )

    private val TOKEN_REGEX = Regex(
        """(//.*?$|#.*?$|/\*[\s\S]*?\*/)|(""" +
        """"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)|(""" +
        """\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?[fFLl]?\b)|(""" +
        """@[a-zA-Z0-9_.]+)|(""" +
        """\b[a-zA-Z_][a-zA-Z0-9_]*\b)|(""" +
        """[{}()\[\].,;+\-*/%=<>!&|^~?:]+)""",
        setOf(RegexOption.MULTILINE)
    )

    fun highlight(code: String, isDark: Boolean): AnnotatedString {
        val commentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        val stringColor = if (isDark) Color(0xFF86EFAC) else Color(0xFF16A34A)
        val numberColor = if (isDark) Color(0xFF67E8F9) else Color(0xFF0284C7)
        val keywordColor = if (isDark) Color(0xFFC084FC) else Color(0xFF7C3AED)
        val literalColor = if (isDark) Color(0xFFF472B6) else Color(0xFFDB2777)
        val annotationColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
        val typeColor = if (isDark) Color(0xFF93C5FD) else Color(0xFF2563EB)
        val defaultColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)

        return buildAnnotatedString {
            var lastIndex = 0
            for (match in TOKEN_REGEX.findAll(code)) {
                val start = match.range.first
                val end = match.range.last + 1
                if (start > lastIndex) {
                    append(code.substring(lastIndex, start))
                }
                val commentGroup = match.groups[1]
                val stringGroup = match.groups[2]
                val numberGroup = match.groups[3]
                val annotationGroup = match.groups[4]
                val wordGroup = match.groups[5]

                when {
                    commentGroup != null -> {
                        withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                            append(match.value)
                        }
                    }
                    stringGroup != null -> {
                        withStyle(SpanStyle(color = stringColor)) {
                            append(match.value)
                        }
                    }
                    numberGroup != null -> {
                        withStyle(SpanStyle(color = numberColor, fontWeight = FontWeight.Medium)) {
                            append(match.value)
                        }
                    }
                    annotationGroup != null -> {
                        withStyle(SpanStyle(color = annotationColor, fontWeight = FontWeight.Medium)) {
                            append(match.value)
                        }
                    }
                    wordGroup != null -> {
                        val word = match.value
                        val lowerWord = word.lowercase()
                        when {
                            lowerWord in KEYWORDS -> {
                                withStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold)) {
                                    append(word)
                                }
                            }
                            word in LITERAL_BOOLEAN_NULL -> {
                                withStyle(SpanStyle(color = literalColor, fontWeight = FontWeight.Bold)) {
                                    append(word)
                                }
                            }
                            word.length > 1 && word[0].isUpperCase() && word.any { it.isLowerCase() } -> {
                                withStyle(SpanStyle(color = typeColor, fontWeight = FontWeight.Medium)) {
                                    append(word)
                                }
                            }
                            else -> {
                                withStyle(SpanStyle(color = defaultColor)) {
                                    append(word)
                                }
                            }
                        }
                    }
                    else -> {
                        withStyle(SpanStyle(color = defaultColor)) {
                            append(match.value)
                        }
                    }
                }
                lastIndex = end
            }
            if (lastIndex < code.length) {
                append(code.substring(lastIndex))
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlockModel.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    val lines = remember(block.code) { block.code.trimEnd().lines() }
    val isLongCode = lines.size > 25
    var isExpanded by remember(block.code) { mutableStateOf(!isLongCode) }
    var showLineNumbers by rememberSaveable { mutableStateOf(false) }
    var softWrap by rememberSaveable { mutableStateOf(false) }

    val displayLines = if (isExpanded || !isLongCode) lines else lines.take(25)
    val isDark = isSystemInDarkTheme()
    val highlightedCode = remember(displayLines, isDark) {
        CodeSyntaxHighlighter.highlight(displayLines.joinToString("\n"), isDark)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                // Language badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = (block.language?.ifBlank { "TEXT" } ?: "CODE").uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${lines.size} 行",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )

                Spacer(Modifier.weight(1f))

                // Line numbers toggle
                IconButton(
                    onClick = { showLineNumbers = !showLineNumbers },
                    modifier = Modifier.size(28.dp),
                ) {
                    Text(
                        text = "#",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (showLineNumbers) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Soft wrap toggle
                IconButton(
                    onClick = { softWrap = !softWrap },
                    modifier = Modifier.size(28.dp),
                ) {
                    Text(
                        text = if (softWrap) "↵" else "⇄",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (softWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Copy code with feedback
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(block.code.trimEnd()))
                        copied = true
                        scope.launch {
                            delay(1500)
                            copied = false
                        }
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    if (copied) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已复制",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "复制代码",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // Share code
                IconButton(
                    onClick = {
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, block.code.trimEnd())
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, "分享代码"))
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "分享代码",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                com.example.aichat.ui.export.SaveBlockButton("另存为代码文件",
                    com.example.aichat.ui.export.BlockExportFormatter.codeFilename(block.language), block.code, "text/plain")
            }
            // Divider under header
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )

            // Code Content
            SelectionContainer {
                val codeScrollState = rememberScrollState()
                val contentModifier = if (softWrap) {
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
                } else {
                    Modifier.fillMaxWidth().horizontalScroll(codeScrollState).padding(horizontal = 10.dp, vertical = 8.dp)
                }

                Row(modifier = contentModifier) {
                    if (showLineNumbers) {
                        Column(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .widthIn(min = 20.dp),
                        ) {
                            displayLines.indices.forEach { idx ->
                                Text(
                                    text = "${idx + 1}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        // Gutter line
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        )
                        Spacer(Modifier.width(10.dp))
                    }

                    Text(
                        text = highlightedCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.5.sp,
                    )
                }
            }

            // Expand/Collapse Bar for > 25 lines with gradient fade
            if (isLongCode) {
                if (!isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    )
                                )
                            )
                    )
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                        .padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isExpanded) "▲ 收起代码" else "▼ 展开余下 ${lines.size - 25} 行代码 (共 ${lines.size} 行)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTable(table: MarkdownBlockModel.Table) {
    val columnCount = table.rows.maxOfOrNull { it.cells.size } ?: return
    if (columnCount == 0) return

    val columnWeights = remember(table) {
        (0 until columnCount).map { colIdx ->
            var maxWeight = 4
            for (row in table.rows) {
                val cell = row.cells.getOrNull(colIdx) ?: continue
                val text = cell.spans.joinToString("") { it.text }
                val weight = text.fold(0) { acc, c ->
                    acc + if (c.code > 127) 2 else 1
                }
                if (weight > maxWeight) maxWeight = weight
            }
            maxWeight.coerceIn(4, 38)
        }
    }

    Column {
    com.example.aichat.ui.export.TableExportActions(table)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val totalWeight = columnWeights.sum().toFloat().coerceAtLeast(1f)
        val availableWidth = maxWidth
        val estimatedWidths = columnWeights.map { (it * 7.5f + 28f).dp.coerceIn(72.dp, 240.dp) }
        val totalEstimated = estimatedWidths.fold(0.dp) { acc, d -> acc + d }

        val (finalWidths, needScroll) = if (totalEstimated <= availableWidth) {
            val remainingWidth = availableWidth - totalEstimated
            val expanded = estimatedWidths.mapIndexed { idx, est ->
                est + remainingWidth * (columnWeights[idx] / totalWeight)
            }
            Pair(expanded, false)
        } else {
            Pair(estimatedWidths, true)
        }

        val scrollModifier = if (needScroll) {
            Modifier.horizontalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }

        SelectionContainer {
            Surface(
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(scrollModifier),
            ) {
                Column(modifier = if (needScroll) Modifier.width(IntrinsicSize.Max) else Modifier.fillMaxWidth()) {
                    table.rows.forEachIndexed { rowIndex, row ->
                        val isHeaderRow = row.cells.any { it.header } || rowIndex == 0
                        val rowBg = when {
                            isHeaderRow -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                            rowIndex % 2 == 1 -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.22f)
                            else -> Color.Transparent
                        }

                        Row(
                            modifier = Modifier
                                .background(rowBg)
                                .height(IntrinsicSize.Min)
                                .then(if (!needScroll) Modifier.fillMaxWidth() else Modifier),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(columnCount) { colIndex ->
                                val cell = row.cells.getOrNull(colIndex)
                                val isHeader = cell?.header == true || isHeaderRow
                                val cellWidth = finalWidths.getOrElse(colIndex) { 100.dp }

                                Box(
                                    modifier = Modifier
                                        .width(cellWidth)
                                        .fillMaxHeight()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = when (cell?.alignment) {
                                        MarkdownTableAlignment.CENTER -> Alignment.Center
                                        MarkdownTableAlignment.END -> Alignment.CenterEnd
                                        else -> Alignment.CenterStart
                                    },
                                ) {
                                    Text(
                                        text = markdownAnnotatedString(cell?.spans.orEmpty()),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 13.sp,
                                            lineHeight = 18.5.sp,
                                            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isHeader) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        ),
                                        textAlign = when (cell?.alignment) {
                                            MarkdownTableAlignment.CENTER -> TextAlign.Center
                                            MarkdownTableAlignment.END -> TextAlign.End
                                            else -> TextAlign.Start
                                        },
                                    )
                                }

                                if (colIndex < columnCount - 1) {
                                    Box(
                                        modifier = Modifier
                                            .width(0.5.dp)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    )
                                }
                            }
                        }

                        if (rowIndex < table.rows.size - 1) {
                            HorizontalDivider(
                                thickness = if (isHeaderRow) 1.5.dp else 0.5.dp,
                                color = if (isHeaderRow) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
