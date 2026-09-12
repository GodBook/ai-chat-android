package com.example.aichat.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.aichat.MainActivity
import com.example.aichat.data.local.ChatDatabase
import com.example.aichat.data.local.ChatConversationEntity
import com.example.aichat.data.local.ChatMessageEntity
import com.example.aichat.data.model.MessageSearchFilter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class Version174SearchUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun viewModelDebouncesClearsLoadingFiltersAndLoadsPast100() {
        val db = ChatDatabase.getInstance(compose.activity)
        val id = "search174-${System.nanoTime()}"
        val now = System.currentTimeMillis()
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        runBlocking {
            db.chatConversationDao().insert(ChatConversationEntity(id, "搜索测试", now, now))
            db.chatMessageDao().insertAll((0..120).map { ChatMessageEntity("$id-$it", if (it % 2 == 0) "USER" else "ASSISTANT",
                "$id 100%_中文 $it", status = "SENT", createdAt = now, conversationId = id) })
        }
        try {
            compose.runOnIdle { vm.setSearchFilter(MessageSearchFilter(conversationId = id)); vm.setDeepSearchQuery("no-result"); vm.setDeepSearchQuery("100%_中文") }
            compose.waitUntil(10_000) { !vm.uiState.value.isDeepSearching && vm.uiState.value.deepSearchResults.size == 50 }
            assertTrue(vm.uiState.value.searchHasMore)
            compose.runOnIdle { vm.loadMoreSearch() }
            compose.waitUntil(10_000) { !vm.uiState.value.isDeepSearching && vm.uiState.value.deepSearchResults.size == 100 }
            compose.runOnIdle { vm.loadMoreSearch() }
            compose.waitUntil(10_000) { !vm.uiState.value.isDeepSearching && vm.uiState.value.deepSearchResults.size == 121 }
            assertFalse(vm.uiState.value.searchHasMore)
            compose.runOnIdle { vm.setSearchFilter(MessageSearchFilter(id, "ASSISTANT", 7)) }
            compose.waitUntil(10_000) { !vm.uiState.value.isDeepSearching && vm.uiState.value.deepSearchResults.size == 50 && vm.uiState.value.deepSearchResults.all { it.role == "ASSISTANT" } }
            compose.runOnIdle { vm.setDeepSearchQuery("") }
            compose.waitUntil(10_000) { !vm.uiState.value.isDeepSearching && vm.uiState.value.deepSearchResults.isEmpty() }
        } finally { runBlocking { db.chatMessageDao().deleteForConversation(id); db.chatConversationDao().deleteById(id) } }
    }

    @Test fun conversationSearchFindsOldAnswerBranchAndDisplaysFilters() {
        val db = ChatDatabase.getInstance(compose.activity)
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val id = "branch174-${System.nanoTime()}"
        val now = System.currentTimeMillis()
        runBlocking {
            db.chatConversationDao().insert(ChatConversationEntity(id, id, now, now))
            db.chatMessageDao().insertAll(listOf(
                ChatMessageEntity("$id-user", "USER", "问题", status = "SENT", requestId = id, createdAt = now, conversationId = id),
                ChatMessageEntity("$id-old", "ASSISTANT", "旧分支独有关键词174", status = "SENT", requestId = id, createdAt = now + 1, conversationId = id),
                ChatMessageEntity("$id-new", "ASSISTANT", "新分支内容174", status = "SENT", requestId = id, createdAt = now + 2, conversationId = id),
            ))
        }
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(id).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(id).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("新分支内容174").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("更多操作").performClick()
            compose.onNodeWithText("搜索当前聊天").performClick()
            compose.onNodeWithText("全部时间 ▾").performClick()
            compose.onNodeWithText("近 7 天").performClick()
            compose.onNodeWithText("全部角色 ▾").performClick()
            compose.onNodeWithText("AI").performClick()
            compose.onAllNodes(hasSetTextAction()).onLast().performTextInput("旧分支独有关键词174")
            compose.waitUntil(10_000) { vm.uiState.value.deepSearchResults.size == 1 && !vm.uiState.value.isDeepSearching }
            File(compose.activity.getExternalFilesDir(null), "174-search.png").outputStream().use {
                compose.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.onNodeWithTag("search-result-$id-old").performClick()
            compose.waitUntil(10_000) { vm.uiState.value.messages.any { it.id == "$id-old" } }
            assertEquals(0, vm.uiState.value.messages.last().branchIndex)
        } finally { runBlocking { db.chatMessageDao().deleteForConversation(id); db.chatConversationDao().deleteById(id) } }
    }
}
