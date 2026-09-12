package com.example.aichat.ui

import com.example.aichat.ui.export.BlockExportFormatter
import com.example.aichat.data.model.literalSearchPattern
import org.junit.Assert.*
import org.junit.Test

class BlockExportFormatterTest {
    @Test fun codeNamesUseSafeExtensionsAndPreserveUnknownLanguagesAsText() {
        assertEquals("code.py", BlockExportFormatter.codeFilename("Python"))
        assertEquals("code.kt", BlockExportFormatter.codeFilename("kotlin title=Example"))
        assertEquals("code.cpp", BlockExportFormatter.codeFilename("c++"))
        assertEquals("code.txt", BlockExportFormatter.codeFilename("../../secret"))
        assertEquals("code.txt", BlockExportFormatter.codeFilename(null))
    }
    @Test fun csvPreservesChineseQuotesCommasNewlinesAndPadsRaggedRows() {
        val csv = BlockExportFormatter.csv(listOf(listOf("姓名", "备注"), listOf("张三", "他说\"你好\",\n第二行"), listOf("李四")))
        assertEquals("\uFEFF姓名,备注\r\n张三,\"他说\"\"你好\"\",\n第二行\"\r\n李四,\r\n", csv)
    }
    @Test fun tsvKeepsColumnsAndQuotesMultilineCells() {
        assertEquals("甲\t乙\r\n\"含\t制表符\"\t\"两\n行\"\r\n", BlockExportFormatter.tsv(listOf(listOf("甲", "乙"), listOf("含\t制表符", "两\n行"))))
    }
    @Test fun spreadsheetExpressionsAreLiteralButNegativeNumbersRemainNumeric() {
        val tsv = BlockExportFormatter.tsv(listOf(listOf("=1+1", "@SUM(A1)", "+cmd", "-cmd", "-12.5", "+1.2e3")))
        assertEquals("'=1+1\t'@SUM(A1)\t'+cmd\t'-cmd\t-12.5\t+1.2e3\r\n", tsv)
    }
    @Test fun parsedTableExportsVisibleTextWithoutMarkdownMarkup() {
        val table = MarkdownDocumentParser.parse("| 项目 | 链接 |\n| --- | --- |\n| **金额** | [来源](https://example.org) |").filterIsInstance<MarkdownBlockModel.Table>().single()
        val rows = table.rows.map { row -> row.cells.map { cell -> cell.spans.joinToString("") { it.text } } }
        assertEquals("\uFEFF项目,链接\r\n金额,来源\r\n", BlockExportFormatter.csv(rows))
    }
    @Test fun searchTreatsWildcardsAndBackslashesAsLiteralCharacters() {
        assertEquals("%100\\%\\_\\\\中文%", literalSearchPattern(" 100%_\\中文 "))
        assertEquals("%关键字%", literalSearchPattern("关键字"))
    }
}
