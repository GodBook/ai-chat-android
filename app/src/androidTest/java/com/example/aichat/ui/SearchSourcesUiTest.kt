package com.example.aichat.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.aichat.data.network.WebSearchResult
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SearchSourcesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sourceCardAndInlineCitationBothOpenBrowser() {
        val base: Context = ApplicationProvider.getApplicationContext()
        val launches = mutableListOf<Intent>()
        val context = object : ContextWrapper(base) { override fun startActivity(intent: Intent) { launches.add(intent) } }
        val sources = listOf(WebSearchResult("真实来源", "https://example.org/report", "摘录", "2026-09-11"))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme { Column {
                    MarkdownText(linkSearchCitations("[来源 1]", sources))
                    WebSearchResultsCard(sources)
                } }
            }
        }
        compose.onNodeWithText("来源 1").performClick()
        compose.runOnIdle { assertEquals("https://example.org/report", launches.single().dataString) }
        compose.onNodeWithText("已获取 1 条网络参考资料").performClick()
        compose.onNodeWithText("查看来源 1 ↗").performClick()
        compose.runOnIdle {
            assertEquals(2, launches.size)
            assertTrue(launches.all { it.action == Intent.ACTION_VIEW && it.dataString == "https://example.org/report" })
        }
    }
}
