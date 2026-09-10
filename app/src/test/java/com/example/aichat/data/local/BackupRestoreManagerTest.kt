package com.example.aichat.data.local

import com.example.aichat.data.model.ChatConversation
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.MessageStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class BackupRestoreManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `export and import round trip restores conversations and avoids UUID conflict`() = runBlocking {
        val imageDir = tempFolder.newFolder("chat-images")
        val sampleImg = File(imageDir, "test_pic.jpg").apply { writeText("fake-image-bytes") }

        val conv1 = ChatConversation(
            id = "c1",
            title = "技术交流",
            groupName = "工作",
            createdAt = 1000L,
            updatedAt = 2000L,
        )

        val msg1 = ChatMessage(
            id = "m1",
            conversationId = "c1",
            role = MessageRole.USER,
            text = "看这张图",
            imagePaths = listOf(sampleImg.absolutePath),
        )

        val msg2 = ChatMessage(
            id = "m2",
            conversationId = "c1",
            role = MessageRole.ASSISTANT,
            text = "这是一张测试图",
            thinkingContent = "正在分析图片...",
            thinkingDurationMs = 1200L,
        )

        // 1. Export
        val outStream = ByteArrayOutputStream()
        val manifest = BackupRestoreManager.exportBackupToStream(
            outputStream = outStream,
            conversations = listOf(conv1),
            messages = listOf(msg1, msg2),
            imageDirectory = imageDir,
            appVersion = "1.6.4",
            versionCode = 26L,
        )

        assertEquals(1, manifest.conversationCount)
        assertEquals(2, manifest.messageCount)
        assertEquals(1, manifest.imageCount)

        // 2. Import on a new device where "c1" already exists locally
        val inStream = ByteArrayInputStream(outStream.toByteArray())
        val restoreTempDir = tempFolder.newFolder("import-temp")
        val destImageDir = tempFolder.newFolder("new-device-chat-images")

        val insertedConvs = mutableListOf<ChatConversationEntity>()
        val insertedMsgs = mutableListOf<ChatMessageEntity>()

        val summary = BackupRestoreManager.importBackupFromStream(
            inputStream = inStream,
            tempDir = restoreTempDir,
            imageDirectory = destImageDir,
            existingConversationIds = setOf("c1"), // Simulating ID conflict
            onInsertData = { convs, msgs ->
                insertedConvs.addAll(convs)
                insertedMsgs.addAll(msgs)
            },
        )

        assertEquals(1, summary.conversationCount)
        assertEquals(2, summary.messageCount)
        assertEquals(1, summary.imageCount)

        // Verifying conflict avoidance: new UUID generated for conversation
        val importedConv = insertedConvs.first()
        assertNotEquals("c1", importedConv.id)
        assertEquals("技术交流", importedConv.title)
        assertEquals("工作", importedConv.groupName)

        // Verifying message conversationId remapped
        assertEquals(2, insertedMsgs.size)
        assertEquals(importedConv.id, insertedMsgs[0].conversationId)
        assertEquals(importedConv.id, insertedMsgs[1].conversationId)
        assertEquals("正在分析图片...", insertedMsgs[1].thinkingContent)
        assertEquals(1200L, insertedMsgs[1].thinkingDurationMs)

        // Verifying image was extracted into destination directory
        val extractedFiles = destImageDir.listFiles()?.toList() ?: emptyList()
        assertEquals(1, extractedFiles.size)
        assertEquals("fake-image-bytes", extractedFiles[0].readText())
    }
}
