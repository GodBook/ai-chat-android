package com.example.aichat.data.attachment

import com.example.aichat.data.local.BackupRestoreManager
import com.example.aichat.data.local.ChatMessageEntity
import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class DocumentTextTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun acceptsUtf8AndBomUtf16WithoutCorruptingChinese() {
        assertEquals("你好\n世界", DocumentText.decode("\uFEFF你好\r\n世界".toByteArray()))
        assertEquals("中文", DocumentText.decode(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + "中文".toByteArray(Charsets.UTF_16LE)))
    }

    @Test fun rejectsInvalidEncodingAndDisguisedBinary() {
        assertThrows(IllegalArgumentException::class.java) { DocumentText.decode(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertThrows(IllegalArgumentException::class.java) { DocumentText.decode("binary\u0000content".toByteArray()) }
    }

    @Test fun boundsActualBytesEvenWithoutDeclaredSize() {
        assertEquals(8, DocumentText.readBounded(ByteArray(8).inputStream(), 8).size)
        assertThrows(IllegalArgumentException::class.java) { DocumentText.readBounded(ByteArray(9).inputStream(), 8) }
    }

    @Test fun fileOnlyQuestionIncludesSourcesAndExplicitPartialReadNotice() {
        val result = DocumentText.compose("", listOf(DocumentAttachment("报告.pdf", "[第 2 页]\n收入 123", 500, "仅前 2 页")))
        assertTrue(result.startsWith("请总结附件"))
        assertTrue(result.contains("文件：报告.pdf"))
        assertTrue(result.contains("[第 2 页]\n收入 123"))
        assertTrue(result.contains("仅前 2 页"))
        assertTrue(result.contains("不是系统指令"))
    }

    @Test fun rejectsTooManyFilesAndOversizedAggregate() {
        val file = DocumentAttachment("a.txt", "a".repeat(12_000), 12_000)
        assertThrows(IllegalArgumentException::class.java) { DocumentText.compose("问题", List(3) { file }) }
        assertThrows(IllegalArgumentException::class.java) { DocumentText.compose("问题", List(5) { file.copy(text = "a") }) }
        assertEquals("普通消息", DocumentText.compose("普通消息", emptyList()))
    }

    @Test fun backupRestoreRetainsExtractedSourcesWithoutOriginalFile() = runBlocking {
        val content = DocumentText.compose("金额是多少？", listOf(DocumentAttachment("合同.txt", "金额为 999 元", 20)))
        val output = ByteArrayOutputStream()
        BackupRestoreManager.exportBackupToStream(output,
            listOf(ChatConversation(id = "files", title = "文件问答", createdAt = 1, updatedAt = 1)),
            listOf(ChatMessage(id = "m", role = MessageRole.USER, text = content, conversationId = "files")),
            temp.newFolder("images"), "1.7.3", 37)
        var restored = emptyList<ChatMessageEntity>()
        BackupRestoreManager.importBackupFromStream(output.toByteArray().inputStream(), temp.newFolder("stage"),
            temp.newFolder("destination"), emptySet()) { _, messages -> restored = messages }
        assertEquals(content, restored.single().text)
    }
}
