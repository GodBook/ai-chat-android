package com.example.aichat.ui

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.aichat.AiChatApplication
import com.example.aichat.MainActivity
import com.example.aichat.data.local.ChatDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TemporaryChatUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun temporaryChatNeverEntersDatabaseAndExitClearsDraftAndSecureWindow() {
        val app = compose.activity.application as AiChatApplication
        val db = ChatDatabase.getInstance(app)
        val key = app.container.apiKeyStore.read()
        app.container.apiKeyStore.clear() // Fail locally; never send a test question to a real service.
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("开启临时对话").fetchSemanticsNodes().isNotEmpty() }
            val messagesBefore = runBlocking { db.chatMessageDao().getAll() }
            val conversationsBefore = runBlocking { db.chatConversationDao().getAll() }
            compose.onNodeWithContentDescription("开启临时对话").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("临时对话").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) }
            compose.onNode(hasSetTextAction()).performTextInput("temporary-sensitive-question-169")
            compose.onNodeWithContentDescription("发送").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("temporary-sensitive-question-169", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("更多操作").performClick()
            compose.onNodeWithText("导出 Markdown (.md)").assertDoesNotExist()
            compose.onNodeWithText("重命名会话").assertDoesNotExist()
            compose.onNodeWithText("清空临时内容").performClick()
            compose.onNodeWithText("取消").performClick()
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).performTextInput("temporary-unsent-draft-169")
            compose.onNodeWithContentDescription("返回").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("开启临时对话").fetchSemanticsNodes().isNotEmpty() }
            compose.runOnIdle { assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) }
            assertEquals(messagesBefore, runBlocking { db.chatMessageDao().getAll() })
            assertEquals(conversationsBefore, runBlocking { db.chatConversationDao().getAll() })
            compose.onNodeWithContentDescription("开启临时对话").performClick()
            compose.onNodeWithText("temporary-unsent-draft-169").assertDoesNotExist()
            compose.onNodeWithText("temporary-sensitive-question-169").assertDoesNotExist()
            compose.activityRule.scenario.onActivity { activity ->
                val vm = ViewModelProvider(activity)[MainViewModel::class.java]
                assertTrue(vm.uiState.value.isTemporary)
                assertTrue(vm.uiState.value.messages.isEmpty())
                assertFalse(vm.uiState.value.webSearchActive)
                vm.closeTemporaryConversation()
            }
        } finally {
            if (key != null) app.container.apiKeyStore.save(key) else app.container.apiKeyStore.clear()
        }
    }
}
