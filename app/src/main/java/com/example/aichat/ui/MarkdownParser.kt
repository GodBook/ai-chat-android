package com.example.aichat.ui

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import java.net.URI

internal sealed interface MarkdownBlockModel {
    data class Paragraph(val spans: List<MarkdownSpanModel>) : MarkdownBlockModel
    data class Heading(val level: Int, val spans: List<MarkdownSpanModel>) : MarkdownBlockModel
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlockModel
    data class MathBlock(val formula: String) : MarkdownBlockModel
    data class Quote(val blocks: List<MarkdownBlockModel>) : MarkdownBlockModel
    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: List<List<MarkdownBlockModel>>,
    ) : MarkdownBlockModel

    data class Table(val rows: List<MarkdownTableRowModel>) : MarkdownBlockModel
    data object Divider : MarkdownBlockModel
}

internal data class MarkdownSpanModel(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val math: Boolean = false,
    val linkUrl: String? = null,
)

internal data class MarkdownTableRowModel(val cells: List<MarkdownTableCellModel>)

internal data class MarkdownTableCellModel(
    val spans: List<MarkdownSpanModel>,
    val header: Boolean,
    val alignment: MarkdownTableAlignment,
)

internal enum class MarkdownTableAlignment {
    START,
    CENTER,
    END,
}

internal object MarkdownDocumentParser {
    private val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    private val BLOCK_MATH_REGEX = Regex("""(?s)(?:\$\$|\\\[)([\s\S]+?)(?:\$\$|\\\])""")
    private val INLINE_MATH_REGEX = Regex("""(?<!\\)\$(?!\s)([^\$\n]+?)(?<!\s)\$|\\\(([\s\S]+?)\\\)""")
    private val CODE_FENCE_OR_INLINE_REGEX = Regex("""(```[\s\S]*?```|`[^`\n]+`)""")
    private val DISPLAY_MATH_BRACKETS_REGEX = Regex("""(?s)(?<!\\)\\\[([\s\S]+?)(?<!\\)\\\]""")
    private val INLINE_MATH_PARENS_REGEX = Regex("""(?<!\\)\\\(([\s\S]+?)(?<!\\)\\\)""")

    fun parse(markdown: String): List<MarkdownBlockModel> {
        val preprocessed = preprocessMathDelimiters(markdown)
        return parser.parse(preprocessed).children().flatMap(::toBlocks).toList()
    }

    private fun preprocessMathDelimiters(markdown: String): String {
        if (!markdown.contains("\\[") && !markdown.contains("\\(")) {
            return markdown
        }
        val parts = mutableListOf<String>()
        var lastIndex = 0
        for (match in CODE_FENCE_OR_INLINE_REGEX.findAll(markdown)) {
            if (match.range.first > lastIndex) {
                parts.add(normalizeMathSyntax(markdown.substring(lastIndex, match.range.first)))
            }
            parts.add(match.value)
            lastIndex = match.range.last + 1
        }
        if (lastIndex < markdown.length) {
            parts.add(normalizeMathSyntax(markdown.substring(lastIndex)))
        }
        return parts.joinToString("")
    }

    private fun normalizeMathSyntax(text: String): String {
        var result = text
        result = DISPLAY_MATH_BRACKETS_REGEX.replace(result) { match ->
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) "\n\n$$\n$inner\n$$\n\n" else match.value
        }
        result = INLINE_MATH_PARENS_REGEX.replace(result) { match ->
            val inner = match.groupValues[1].trim()
            if (inner.isNotEmpty()) "$$inner$" else match.value
        }
        return result
    }

    private fun toBlocks(node: Node): List<MarkdownBlockModel> = when (node) {
        is Paragraph -> {
            val spans = node.inlineSpans()
            val text = spans.joinToString("") { it.text }.trim()
            val singleBlockMath = Regex("""^(?:\$\$|\\\[)([\s\S]+?)(?:\$\$|\\\])$""").matchEntire(text)
            if (singleBlockMath != null) {
                listOf(MarkdownBlockModel.MathBlock(singleBlockMath.groupValues[1].trim()))
            } else if (BLOCK_MATH_REGEX.containsMatchIn(text) && spans.size == 1) {
                val result = mutableListOf<MarkdownBlockModel>()
                var pos = 0
                for (match in BLOCK_MATH_REGEX.findAll(text)) {
                    if (match.range.first > pos) {
                        val before = text.substring(pos, match.range.first).trim()
                        if (before.isNotEmpty()) {
                            result.add(MarkdownBlockModel.Paragraph(listOf(MarkdownSpanModel(before))))
                        }
                    }
                    val mathFormula = match.groupValues[1].trim()
                    if (mathFormula.isNotEmpty()) {
                        result.add(MarkdownBlockModel.MathBlock(mathFormula))
                    }
                    pos = match.range.last + 1
                }
                if (pos < text.length) {
                    val after = text.substring(pos).trim()
                    if (after.isNotEmpty()) {
                        result.add(MarkdownBlockModel.Paragraph(listOf(MarkdownSpanModel(after))))
                    }
                }
                result
            } else {
                listOf(MarkdownBlockModel.Paragraph(spans))
            }
        }
        is Heading -> listOf(MarkdownBlockModel.Heading(node.level, node.inlineSpans()))
        is FencedCodeBlock -> {
            val lang = node.info.trim().takeIf(String::isNotEmpty)?.substringBefore(' ')
            if (lang?.lowercase() in setOf("latex", "math", "tex")) {
                listOf(MarkdownBlockModel.MathBlock(node.literal.trim()))
            } else {
                listOf(MarkdownBlockModel.CodeBlock(code = node.literal, language = lang))
            }
        }
        is IndentedCodeBlock -> listOf(MarkdownBlockModel.CodeBlock(node.literal, null))
        is BlockQuote -> listOf(MarkdownBlockModel.Quote(node.children().flatMap(::toBlocks).toList()))
        is BulletList -> listOf(MarkdownBlockModel.ListBlock(
            ordered = false,
            startNumber = 1,
            items = node.listItems(),
        ))
        is OrderedList -> listOf(MarkdownBlockModel.ListBlock(
            ordered = true,
            startNumber = node.markerStartNumber ?: 1,
            items = node.listItems(),
        ))
        is TableBlock -> listOf(MarkdownBlockModel.Table(node.tableRows()))
        is ThematicBreak -> listOf(MarkdownBlockModel.Divider)
        is HtmlBlock -> listOf(MarkdownBlockModel.Paragraph(listOf(MarkdownSpanModel(node.literal))))
        else -> {
            val children = node.children().flatMap(::toBlocks).toList()
            when {
                children.isNotEmpty() -> listOf(MarkdownBlockModel.Quote(children))
                node.inlineSpans().isNotEmpty() -> listOf(MarkdownBlockModel.Paragraph(node.inlineSpans()))
                else -> emptyList()
            }
        }
    }

    private fun Node.listItems(): List<List<MarkdownBlockModel>> = children()
        .filterIsInstance<ListItem>()
        .map { item -> item.children().flatMap(::toBlocks).toList() }
        .toList()

    private fun TableBlock.tableRows(): List<MarkdownTableRowModel> = descendants()
        .filterIsInstance<TableRow>()
        .map { row ->
            MarkdownTableRowModel(
                row.children().filterIsInstance<TableCell>().map { cell ->
                    MarkdownTableCellModel(
                        spans = cell.inlineSpans(),
                        header = cell.isHeader,
                        alignment = when (cell.alignment) {
                            TableCell.Alignment.CENTER -> MarkdownTableAlignment.CENTER
                            TableCell.Alignment.RIGHT -> MarkdownTableAlignment.END
                            else -> MarkdownTableAlignment.START
                        },
                    )
                }.toList(),
            )
        }
        .filter { it.cells.isNotEmpty() }
        .toList()

    private fun Node.inlineSpans(): List<MarkdownSpanModel> = buildList {
        children().forEach { child -> collectInline(child, InlineStyle(), this) }
    }

    private fun collectInline(
        node: Node,
        style: InlineStyle,
        output: MutableList<MarkdownSpanModel>,
    ) {
        when (node) {
            is Text -> {
                val text = node.literal
                var currentIndex = 0
                val matches = INLINE_MATH_REGEX.findAll(text).toList()
                if (matches.isEmpty()) {
                    output.append(text, style)
                } else {
                    for (match in matches) {
                        if (match.range.first > currentIndex) {
                            output.append(text.substring(currentIndex, match.range.first), style)
                        }
                        val mathContent = match.groups[1]?.value ?: match.groups[2]?.value ?: match.value
                        output.append(mathContent, style.copy(math = true))
                        currentIndex = match.range.last + 1
                    }
                    if (currentIndex < text.length) {
                        output.append(text.substring(currentIndex), style)
                    }
                }
            }
            is Code -> output.append(node.literal, style.copy(code = true))
            is SoftLineBreak, is HardLineBreak -> output.append("\n", style)
            is StrongEmphasis -> node.children().forEach { collectInline(it, style.copy(bold = true), output) }
            is Emphasis -> node.children().forEach { collectInline(it, style.copy(italic = true), output) }
            is Link -> {
                val linkedStyle = style.copy(linkUrl = safeExternalUrl(node.destination))
                if (node.firstChild == null) {
                    output.append(node.destination, linkedStyle)
                } else {
                    node.children().forEach { collectInline(it, linkedStyle, output) }
                }
            }
            is Image -> {
                val alt = mutableListOf<MarkdownSpanModel>()
                node.children().forEach { collectInline(it, InlineStyle(), alt) }
                val label = alt.joinToString(separator = "") { it.text }.ifBlank { "图片" }
                output.append("[$label]", style.copy(linkUrl = safeExternalUrl(node.destination)))
            }
            is HtmlInline -> output.append(node.literal, style)
            else -> node.children().forEach { collectInline(it, style, output) }
        }
    }

    private fun MutableList<MarkdownSpanModel>.append(text: String, style: InlineStyle) {
        if (text.isEmpty()) return
        val span = MarkdownSpanModel(
            text = text,
            bold = style.bold,
            italic = style.italic,
            code = style.code,
            math = style.math,
            linkUrl = style.linkUrl,
        )
        val previous = lastOrNull()
        if (previous != null && previous.copy(text = "") == span.copy(text = "")) {
            this[lastIndex] = previous.copy(text = previous.text + text)
        } else {
            add(span)
        }
    }

    private fun safeExternalUrl(value: String): String? = runCatching {
        val uri = URI(value.trim())
        value.takeIf {
            uri.isAbsolute && uri.scheme.lowercase() in setOf("https", "http") && uri.host != null
        }
    }.getOrNull()

    private fun Node.children(): Sequence<Node> = sequence {
        var child = firstChild
        while (child != null) {
            val current = child
            yield(current)
            child = current.next
        }
    }

    private fun Node.descendants(): Sequence<Node> = sequence {
        children().forEach { child ->
            yield(child)
            yieldAll(child.descendants())
        }
    }

    private data class InlineStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val math: Boolean = false,
        val linkUrl: String? = null,
    )
}
