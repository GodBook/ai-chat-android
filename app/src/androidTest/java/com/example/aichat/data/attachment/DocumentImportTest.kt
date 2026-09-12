package com.example.aichat.data.attachment

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun <T> withFile(name: String, bytes: ByteArray, block: (Uri) -> T): T {
        val file = File(context.cacheDir, "exports/test173-$name")
        file.parentFile!!.mkdirs()
        file.writeBytes(bytes)
        try { return block(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) }
        finally { file.delete() }
    }

    @Test fun importsChineseTextAndReportsTruncation() = runBlocking {
        withFile("中文.txt", "你好世界".repeat(4000).toByteArray()) { uri ->
            val result = runBlocking { DocumentImporter(context).import(uri) }
            assertEquals(DocumentText.MAX_CHARS, result.text.length)
            assertTrue(result.notice!!.contains("后续内容未读取"))
            assertTrue(result.name.endsWith("中文.txt"))
        }
    }

    private fun docx(xml: String): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml")); zip.write(xml.toByteArray()); zip.closeEntry()
        }
    }.toByteArray()

    @Test fun importsDocxParagraphsAndTableCells() {
        val bytes = docx("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>合同金额</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>123元</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>""")
        withFile("合同.docx", bytes) { uri ->
            val result = runBlocking { DocumentImporter(context).import(uri) }
            assertTrue(result.text.contains("合同金额")); assertTrue(result.text.contains("123元"))
        }
    }

    @Test fun rejectsDocxEntityDeclarations() {
        val bytes = docx("""<!DOCTYPE x [<!ENTITY secret SYSTEM "file:///data/local/tmp/secret">]><x>&secret;</x>""")
        withFile("entity.docx", bytes) { uri ->
            assertThrows(IllegalArgumentException::class.java) { runBlocking { DocumentImporter(context).import(uri) } }
        }
    }

    @Test fun pdfExtractsPageReferencesAndRejectsEmptyScans() {
        PDFBoxResourceLoader.init(context)
        val out = ByteArrayOutputStream()
        PDDocument().use { pdf ->
            val page = PDPage(); pdf.addPage(page)
            PDPageContentStream(pdf, page).use { stream ->
                stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(40f, 700f); stream.showText("Invoice total: 173 dollars"); stream.endText()
            }
            pdf.save(out)
        }
        withFile("invoice.pdf", out.toByteArray()) { uri ->
            val result = runBlocking { DocumentImporter(context).import(uri) }
            assertTrue(result.text.contains("[第 1 页]")); assertTrue(result.text.contains("173 dollars"))
        }
        out.reset()
        PDDocument().use { pdf -> pdf.addPage(PDPage()); pdf.save(out) }
        withFile("scan.pdf", out.toByteArray()) { uri ->
            val error = assertThrows(IllegalArgumentException::class.java) { runBlocking { DocumentImporter(context).import(uri) } }
            assertTrue(error.message!!.contains("OCR"))
        }
    }

    @Test fun shareSupportsColdTextMultipleStreamsAndClipDataDeduplication() {
        val text = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.org/article")
        assertEquals("https://example.org/article", IncomingShare.from(text, context.packageName)!!.text)
        val uri = Uri.parse("content://external.provider/document/a")
        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/pdf")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri, uri))
        multiple.clipData = ClipData.newUri(context.contentResolver, "file", uri)
        assertEquals(listOf(uri), IncomingShare.from(multiple, context.packageName)!!.uris)
    }

    @Test fun shareRejectsPrivateUrisAndExcessivePayloads() {
        for (uri in listOf("file:///data/data/com.example.aichat/files/private", "content://${context.packageName}.fileprovider/updates/private")) {
            val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            assertThrows(IllegalArgumentException::class.java) { IncomingShare.from(intent, context.packageName) }
        }
        val large = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "x".repeat(24_001))
        assertThrows(IllegalArgumentException::class.java) { IncomingShare.from(large, context.packageName) }
        assertNull(IncomingShare.from(Intent(Intent.ACTION_MAIN), context.packageName))
    }
}
