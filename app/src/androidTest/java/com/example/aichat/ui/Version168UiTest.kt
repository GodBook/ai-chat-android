package com.example.aichat.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Version168UiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun iconSelectionCanBeSavedAndReset() {
        var result: String? = "initial"
        compose.setContent { MaterialTheme { ConversationIconDialog("💻", {}, { result = it }) } }
        compose.onNodeWithText("🧠").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("🧠", result) }
        compose.onNodeWithText("恢复默认图标").performScrollTo().performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertNull(result) }
    }

    @Test fun historyShowsCurrentAndOldestBundledVersionsOffline() {
        compose.setContent { MaterialTheme { ReleaseHistoryDialog({}) } }
        val current = "v${com.example.aichat.BuildConfig.VERSION_NAME} · 当前版本"
        compose.waitUntil(15000) { compose.onAllNodesWithText(current).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(current).assertIsDisplayed()
        compose.onNodeWithText(current).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("v1.1"))
        compose.onNodeWithText("v1.1").assertIsDisplayed().performClick()
        compose.onNodeWithText("联网刷新").assertExists()
    }

    @Test fun startPageMenuEditsOnlyTheSelectedConversationIcon() {
        var savedId: String? = null
        var savedIcon: String? = null
        compose.setContent {
            MaterialTheme {
                ContactsScreen(
                    conversations = listOf(com.example.aichat.data.model.ChatConversation("one", "个人", 1, 1)),
                    previews = emptyMap(), selectedConversationId = null, isAnyWorking = false,
                    onOpenChat = {}, onCreateConversation = { _, _, _ -> }, onFastCreateConversation = {},
                    onRenameConversation = { _, _ -> }, onDeleteConversation = {}, onDeleteConversations = {},
                    onExportConversation = {}, onExportConversations = {}, onOpenSettings = {},
                    onSetConversationIcon = { id, icon -> savedId = id; savedIcon = icon },
                )
            }
        }
        compose.onNodeWithContentDescription("个人的聊天操作").performClick()
        compose.onNodeWithText("自定义图标").performClick()
        compose.onNodeWithText("🐱").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals("one", savedId); assertEquals("🐱", savedIcon) }
    }
}
