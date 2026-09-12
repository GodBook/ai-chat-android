package com.example.aichat.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import com.example.aichat.AiChatApplication
import com.example.aichat.MainActivity
import com.example.aichat.data.local.ChatDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class Version173UiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun share(text: String) = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun confirmShare() {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("导入草稿") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("导入草稿").performClick()
    }

    private fun capture(name: String) {
        val root = if (name.contains("share-preview")) compose.onNode(isDialog()) else compose.onRoot()
        val bitmap = root.captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun coldAndWarmSharesRequireConfirmationAndRestoreAcrossRotation() {
        val db = ChatDatabase.getInstance(context)
        val before = runBlocking { db.chatMessageDao().getAll().size }
        ActivityScenario.launch<MainActivity>(share("shared-draft-173")).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodesWithText("导入草稿").fetchSemanticsNodes().isNotEmpty() }
            scenario.recreate()
            compose.onNodeWithText("接收分享").assertExists()
            capture("173-share-preview.png")
            assertEquals(before, runBlocking { db.chatMessageDao().getAll().size })
            confirmShare()
            compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction() and hasText("shared-draft-173")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(before, runBlocking { db.chatMessageDao().getAll().size })
            val targetTitle = "173-target-${System.nanoTime()}"
            var targetId: String? = null
            scenario.onActivity {
                val vm = ViewModelProvider(it)[MainViewModel::class.java]
                targetId = vm.uiState.value.selectedConversationId
                vm.renameConversation(targetId!!, targetTitle)
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithText(targetTitle).fetchSemanticsNodes().isNotEmpty() }
            scenario.onActivity { it.startActivity(share("warm-share-173")) }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("接收分享").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(15_000) { compose.onAllNodes(hasText("新建分享问答") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("新建分享问答").performClick()
            compose.onAllNodesWithText(targetTitle).onLast().performClick()
            confirmShare()
            compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction() and hasText("warm-share-173")).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(before, runBlocking { db.chatMessageDao().getAll().size })
            scenario.onActivity { assertEquals(targetId, ViewModelProvider(it)[MainViewModel::class.java].uiState.value.selectedConversationId) }
        }
    }

    @Test fun documentPreviewAndMissingKeyFailureKeepUnsentFileAvailable() {
        val app = context.applicationContext as AiChatApplication
        val oldKey = app.container.apiKeyStore.read()
        app.container.apiKeyStore.clear()
        val input = File(context.cacheDir, "exports/test173-ui.txt").apply { parentFile!!.mkdirs(); writeText("文件中记录的金额为 173 元。") }
        try {
            ActivityScenario.launch<MainActivity>(share("文件问答")).use { scenario ->
                compose.waitUntil(15_000) { compose.onAllNodesWithText("导入草稿").fetchSemanticsNodes().isNotEmpty() }
                confirmShare()
                compose.waitUntil(15_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
                compose.onNode(hasSetTextAction()).performTextClearance()
                scenario.onActivity { activity ->
                    ViewModelProvider(activity)[MainViewModel::class.java].importDocuments(context,
                        listOf(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", input)))
                }
                compose.waitUntil(15_000) { compose.onAllNodesWithText("test173-ui.txt").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithContentDescription("添加文件").assertIsDisplayed()
                compose.onNodeWithContentDescription("发送").assertIsDisplayed()
                capture("173-file-composer.png")
                compose.onNodeWithText("test173-ui.txt").performClick()
                compose.onNodeWithText("文件中记录的金额为 173 元。").assertExists()
                compose.onNodeWithText("关闭").performClick()
                compose.onNodeWithContentDescription("发送").assertIsEnabled().performClick()
                compose.waitUntil(15_000) { compose.onAllNodesWithText("test173-ui.txt").fetchSemanticsNodes().isNotEmpty() }
                scenario.onActivity { activity ->
                    val vm = ViewModelProvider(activity)[MainViewModel::class.java]
                    assertEquals(1, vm.uiState.value.attachments.documents.size)
                }
            }
        } finally {
            input.delete()
            if (oldKey == null) app.container.apiKeyStore.clear() else app.container.apiKeyStore.save(oldKey)
        }
    }
}
