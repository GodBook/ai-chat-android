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

    fun parse(markdown: String): List<MarkdownBlockModel> =
        parser.parse(markdown).children().mapNotNull(::toBlock).toList()

    private fun toBlock(node: Node): MarkdownBlockModel? = when (node) {
        is Paragraph -> MarkdownBlockModel.Paragraph(node.inlineSpans())
        is Heading -> MarkdownBlockModel.Heading(node.level, node.inlineSpans())
        is FencedCodeBlock -> MarkdownBlockModel.CodeBlock(
            code = node.literal,
            language = node.info.trim().takeIf(String::isNotEmpty)?.substringBefore(' '),
        )
        is IndentedCodeBlock -> MarkdownBlockModel.CodeBlock(node.literal, null)
        is BlockQuote -> MarkdownBlockModel.Quote(node.children().mapNotNull(::toBlock).toList())
        is BulletList -> MarkdownBlockModel.ListBlock(
            ordered = false,
            startNumber = 1,
            items = node.listItems(),
        )
        is OrderedList -> MarkdownBlockModel.ListBlock(
            ordered = true,
            startNumber = node.markerStartNumber ?: 1,
            items = node.listItems(),
        )
        is TableBlock -> MarkdownBlockModel.Table(node.tableRows())
        is ThematicBreak -> MarkdownBlockModel.Divider
        is HtmlBlock -> MarkdownBlockModel.Paragraph(listOf(MarkdownSpanModel(node.literal)))
        else -> node.children().mapNotNull(::toBlock).toList().let { children ->
            when {
                children.isNotEmpty() -> MarkdownBlockModel.Quote(children)
                node.inlineSpans().isNotEmpty() -> MarkdownBlockModel.Paragraph(node.inlineSpans())
                else -> null
            }
        }
    }

    private fun Node.listItems(): List<List<MarkdownBlockModel>> = children()
        .filterIsInstance<ListItem>()
        .map { item -> item.children().mapNotNull(::toBlock).toList() }
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
            is Text -> output.append(node.literal, style)
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
        val linkUrl: String? = null,
    )
}
