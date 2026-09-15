package com.example.aichat.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import com.example.aichat.MainActivity
import com.example.aichat.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import android.graphics.Bitmap

class Version175UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun saveSelectPreviewAndContinueWithoutAutomaticallySending() {
        val db = ChatDatabase.getInstance(compose.activity)
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val id = "ui175-${System.nanoTime()}"
        val now = System.currentTimeMillis()
        runBlocking {
            db.chatConversationDao().insert(ChatConversationEntity(id, id, now, now))
            db.chatMessageDao().insertAll(listOf(
                ChatMessageEntity("$id-u", "USER", "请设计离线编辑", status = "SENT", requestId = id, createdAt = now, conversationId = id),
                ChatMessageEntity("$id-a", "ASSISTANT", "断网可编辑，恢复网络后同步。", status = "SENT", requestId = id, createdAt = now + 1, conversationId = id)))
        }
        var continuation: String? = null
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(id).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(id).performClick()
            compose.waitUntil(10_000) { vm.uiState.value.messages.any { it.id == "$id-a" } }
            compose.runOnIdle { vm.newCard(vm.uiState.value.messages.last()) }
            compose.onNodeWithText("保存").performScrollTo().performClick()
            compose.waitUntil(10_000) { vm.uiState.value.workbench.cards.any { it.sourceMessageId == "$id-a" } && vm.uiState.value.workbench.editor == null }
            compose.runOnIdle { vm.openCards() }
            compose.onNodeWithText("用于下次提问").performScrollTo().performClick()
            compose.waitUntil(10_000) { vm.uiState.value.workbench.selectedCardIds.size == 1 }
            compose.onNodeWithText("关闭").performClick()
            compose.runOnIdle { vm.refreshContext("继续方案"); vm.openContext() }
            compose.waitUntil(10_000) { vm.uiState.value.workbench.plan?.cards?.size == 1 }
            assertEquals(1, vm.uiState.value.workbench.plan!!.includedRounds)
            File(compose.activity.getExternalFilesDir(null), "175-context.png").outputStream().use {
                compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithText("允许携带此轮").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("关闭").performClick()
            compose.runOnIdle { vm.openCards(); vm.startContinuation() }
            compose.onNodeWithText("下一步目标").performTextInput("补充冲突处理")
            compose.onNodeWithText("创建并填入草稿").performScrollTo().performClick()
            compose.waitUntil(10_000) { vm.uiState.value.selectedConversationId != id && vm.uiState.value.sharedDraft?.text?.contains("补充冲突处理") == true }
            continuation = vm.uiState.value.selectedConversationId
            assertTrue(vm.uiState.value.messages.isEmpty())
            assertTrue(vm.uiState.value.sharedDraft!!.text.contains("断网可编辑"))
            compose.activityRule.scenario.recreate()
            val restoredVm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
            compose.waitUntil(10_000) { restoredVm.uiState.value.sharedDraft?.text?.contains("补充冲突处理") == true }
            assertTrue(restoredVm.uiState.value.messages.isEmpty())
        } finally {
            runBlocking {
                KnowledgeStore(db).clearConversation(id, true)
                db.chatMessageDao().deleteForConversation(id); db.chatConversationDao().deleteById(id)
                continuation?.let { ConfigStore(compose.activity).saveContinuationDraft(it, null); db.chatConversationDao().deleteById(it) }
            }
        }
    }
}
