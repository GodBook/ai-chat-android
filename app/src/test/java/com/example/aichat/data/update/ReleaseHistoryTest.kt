package com.example.aichat.data.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseHistoryTest {
    @Test fun `rate limited API falls back to repository history snapshot`() = kotlinx.coroutines.runBlocking {
        okhttp3.mockwebserver.MockWebServer().use { server ->
            server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(403))
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody("""[{"version":"1.6.8","publishedAt":"2026-09-11","notes":"更新内容"}]"""))
            val client = ReleaseHistoryClient(okhttp3.OkHttpClient(), server.url("/releases").toString(), server.url("/history").toString())
            assertEquals("更新内容", client.fetch().single().notes)
            assertEquals("/history", server.takeRequest().let { server.takeRequest().path })
        }
    }

    @Test fun `history excludes drafts and previews and handles missing notes`() {
        val history = parseReleaseHistory("""[{"tag_name":"v1.6.8","body":"修复搜索","published_at":"2026-09-11"},{"tag_name":"v1.7.0","draft":true},{"tag_name":"v1.6.9","prerelease":true},{"tag_name":"v1.1","body":null}]""")
        assertEquals(listOf("1.6.8", "1.1"), history.map { it.version })
        assertEquals("此版本未提供发布说明。", history.last().notes)
    }

    @Test fun `history retains bundled current release and sorts numerically`() {
        val local = listOf(ReleaseNote("1.6.8", "", "local"), ReleaseNote("1.6.7", "", "old"))
        val remote = listOf(ReleaseNote("1.6.10", "", "new"), ReleaseNote("1.6.7", "", "updated"))
        val result = mergeReleaseHistory(local, remote)
        assertEquals(listOf("1.6.10", "1.6.8", "1.6.7"), result.map { it.version })
        assertEquals("updated", result.last().notes)
    }
}
