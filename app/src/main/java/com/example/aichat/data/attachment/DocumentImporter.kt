package com.example.aichat.data.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.util.zip.ZipInputStream

class DocumentImporter(private val context: Context) {
    suspend fun import(uri: Uri): DocumentAttachment = withContext(Dispatchers.IO) {
        require(uri.scheme == "content") { "请通过系统文件选择器选择文件" }
        val resolver = context.contentResolver
        var filename = "未命名文件"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) filename = DocumentText.name(cursor.getString(nameIndex).orEmpty())
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) require(cursor.getLong(sizeIndex) <= DocumentText.MAX_BYTES) { "文件过大，单个文件最多 10 MB" }
            }
        }
        val extension = filename.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri).orEmpty()
        val pdf = extension == "pdf" || mime == "application/pdf"
        val docx = extension == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        require(pdf || docx || extension in DocumentText.extensions || mime.startsWith("text/")) {
            "暂不支持此格式，请选择 TXT、Markdown、代码、CSV、DOCX 或文字型 PDF"
        }
        val bytes = resolver.openInputStream(uri)?.use { DocumentText.readBounded(it) }
            ?: throw IllegalArgumentException("无法打开文件，请重新分享或选择")
        val extracted = when {
            pdf -> readPdf(bytes)
            docx -> readDocx(bytes) to "仅提取 DOCX 正文和表格文字，不含图片、批注及页眉页脚"
            else -> DocumentText.decode(bytes) to null
        }
        val text = extracted.first.trim()
        require(text.isNotBlank()) { if (pdf) "PDF 未提取到文字，可能是扫描件；请先 OCR，或把相关页面作为图片发送" else "文件中没有可读取的文字" }
        val notice = listOfNotNull(extracted.second, if (text.length > DocumentText.MAX_CHARS) "仅使用前 ${DocumentText.MAX_CHARS} 字符，后续内容未读取" else null).joinToString("；").ifBlank { null }
        DocumentAttachment(filename, text.take(DocumentText.MAX_CHARS), bytes.size, notice)
    }

    private fun readPdf(bytes: ByteArray): Pair<String, String?> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val loaded = try { PDDocument.load(bytes) }
        catch (failure: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw IllegalArgumentException("暂不支持加密 PDF，请先解密后再导入", failure)
        }
        loaded.use { document ->
            require(!document.isEncrypted) { "暂不支持加密 PDF，请先解密后再导入" }
            require(document.numberOfPages <= 200) { "PDF 超过 200 页，请拆分后导入" }
            val out = StringBuilder()
            val stripper = PDFTextStripper()
            var readPages = 0
            var emptyPages = 0
            for (page in 1..document.numberOfPages) {
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(document).trim()
                readPages = page
                if (text.isNotEmpty()) out.append("[第 $page 页]\n").append(text.take(DocumentText.MAX_CHARS + 1)).append('\n')
                else emptyPages++
                if (out.length > DocumentText.MAX_CHARS) break
            }
            val notice = listOfNotNull(
                if (readPages < document.numberOfPages) "已处理前 $readPages/${document.numberOfPages} 页" else null,
                if (emptyPages > 0) "$emptyPages 页未提取到文字（未执行 OCR）" else null,
            ).joinToString("；").ifBlank { null }
            return out.toString() to notice
        }
    }

    private fun readDocx(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            var expanded = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 2048) { "DOCX 包含过多条目" }
                if (entry.name == "word/document.xml") {
                    val xml = DocumentText.decode(DocumentText.readBounded(zip, 4 * 1024 * 1024))
                    require(!xml.contains("<!DOCTYPE", ignoreCase = true) && !xml.contains("<!ENTITY", ignoreCase = true)) { "不支持包含实体声明的文档" }
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    parser.setInput(StringReader(xml))
                    val out = StringBuilder()
                    var event = parser.eventType
                    var inText = false
                    while (event != XmlPullParser.END_DOCUMENT && out.length <= DocumentText.MAX_CHARS) {
                        when (event) {
                            XmlPullParser.START_TAG -> when (parser.name) {
                                "t" -> inText = true
                                "tab" -> out.append('\t')
                                "br" -> out.append('\n')
                            }
                            XmlPullParser.TEXT -> if (inText) out.append(parser.text.take(DocumentText.MAX_CHARS + 1))
                            XmlPullParser.END_TAG -> when (parser.name) {
                                "t" -> inText = false
                                "p", "tr" -> out.append('\n')
                                "tc" -> out.append('\t')
                            }
                        }
                        event = parser.next()
                    }
                    return out.toString()
                }
                val buffer = ByteArray(8192)
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    expanded += count
                    require(expanded <= 20L * 1024 * 1024) { "DOCX 解压内容过大，请另存为文本" }
                }
            }
        }
        throw IllegalArgumentException("DOCX 缺少正文，或文件已损坏")
    }
}
