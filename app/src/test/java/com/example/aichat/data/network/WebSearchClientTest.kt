package com.example.aichat.data.network

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class WebSearchClientTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-11T05:30:00Z"), ZoneId.of("Asia/Shanghai"))

    @Test fun `relative dates and quoted reply are resolved without losing the question`() {
        val plan = planSearch("> 去年北京天气\n\n明天上海天气怎么样", clock)
        assertEquals(LocalDate.of(2026, 9, 12), plan.targetDate)
        assertTrue(plan.query.contains("2026-09-12上海"))
        assertFalse(plan.query.contains("去年"))
        assertEquals(LocalDate.of(2025, 1, 2), planSearch("2025年1月2日北京天气", clock).targetDate)
    }

    @Test fun `old dated results are removed and sources deduplicated`() {
        val old = WebSearchResult("旧天气", "https://example.com/old", "2025年10月10日 北京晴")
        val fresh = WebSearchResult("今天", "https://example.com/new", "2026-09-11 北京晴")
        val undated = WebSearchResult("天气网站", "https://example.com/site", "天气预报")
        val result = rankSearchResults(listOf(old, undated, fresh, fresh), planSearch("今天北京天气", clock), 5)
        assertEquals(listOf(fresh, undated), result)
        assertTrue(rankSearchResults(listOf(old), planSearch("2025年10月10日北京天气", clock), 5).isNotEmpty())
    }

    @Test fun `weather city extraction covers word order and refuses implicit location`() {
        assertEquals("北京", extractWeatherPlace("今天北京天气怎么样"))
        assertEquals("上海", extractWeatherPlace("请问上海市明天天气如何？"))
        assertNull(extractWeatherPlace("今天天气怎么样"))
        assertNull(extractWeatherPlace("这里天气如何"))
    }

    @Test fun `weather request resolves city and uses dated structured values`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"results":[{"name":"北京","feature_code":"PPLC","latitude":39.9,"longitude":116.4,"timezone":"Asia/Shanghai","country":"中国"},{"name":"北京","feature_code":"PPL","latitude":30,"longitude":108,"timezone":"Asia/Shanghai"}]}"""))
            server.enqueue(MockResponse().setBody(forecast))
            val client = WeatherSearchClient(OkHttpClient(), clock, server.url("/geo").toString(), server.url("/forecast").toString())
            val result = client.search("今天北京天气怎么样")!!
            assertTrue(result.snippet.contains("2026-09-11T13:15"))
            assertTrue(result.snippet.contains("最高 30.7 °C"))
            assertTrue(result.snippet.contains("并非气象站实测"))
            assertEquals("北京", server.takeRequest().requestUrl!!.queryParameter("name"))
            assertEquals("Asia/Shanghai", server.takeRequest().requestUrl!!.queryParameter("timezone"))
        }
    }

    @Test fun `stale current values are omitted and missing forecast date is rejected`() {
        val client = WeatherSearchClient(OkHttpClient(), clock)
        val data = Json.parseToJsonElement(forecast.replace("2026-09-11T13:15", "2026-09-10T13:15")).jsonObject
        val location = Json.parseToJsonElement("""{"name":"北京"}""").jsonObject
        val result = client.parseForecast(data, LocalDate.of(2026,9,11), clock, location, "https://example.com")!!
        assertFalse(result.snippet.contains("当前模型估算"))
        assertNull(client.parseForecast(data, LocalDate.of(2026,9,12), clock, location, "https://example.com"))
    }

    @Test fun `both engines are queried even when first returns results`() = runBlocking {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setBody("""<li class="res-list"><h3><a href="https://one.example/a">Kotlin 结果</a></h3><p class="res-desc">来源内容</p></li><li class="b_algo"><h2><a href="https://two.example/a">Kotlin 结果二</a></h2><p>第二来源</p></li>""")) }
            val client = WebSearchClient(OkHttpClient(), clock, server.url("/so").toString(), server.url("/cn").toString(), server.url("/global").toString())
            assertEquals(2, client.search("Kotlin 教程").size)
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun `empty results keep explicit failure grounding`() {
        val prompt = buildSearchContext("今天北京天气", emptyList(), clock)
        assertTrue(prompt.contains("联网检索失败"))
        assertTrue(prompt.contains("不得伪造来源"))
        assertFalse(prompt.contains("互联网最新实时检索结果"))
    }

    @Test fun `source URLs decode Bing redirects and reject unsafe schemes`() {
        val client = WebSearchClient()
        val target = "https://example.com/weather?a=1&b=2"
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(target.toByteArray())
        assertEquals(target, client.normalizeResultUrl("https://www.bing.com/ck/a?u=a1$encoded"))
        assertNull(client.normalizeResultUrl("javascript:alert(1)"))
        assertNull(client.normalizeResultUrl("https://www.so.com/link?m=unresolved"))
    }

    @Test fun `360 direct source attributes and modern summary retain publication date`() {
        val html = """<li class="res-list referer-news-flow"><h3><a href="https://www.so.com/link?m=opaque" data-mdurl="https://example.com/a?x=1&amp;y=2">北京天气</a></h3><p></p><span class="g-c-gray">2025年10月10日</span><span class="res-list-summary">旧天气摘要</span></li>"""
        val result = WebSearchClient().parseSoHtml(html, 5).single()
        assertEquals("https://example.com/a?x=1&y=2", result.url)
        assertTrue(result.snippet.contains("2025年10月10日"))
        assertTrue(rankSearchResults(listOf(result), planSearch("今天北京天气", clock), 5).isEmpty())
    }

    @Test fun `cancelled search does not wait for unresponsive engines`() = runBlocking {
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE)) }
            val client = WebSearchClient(OkHttpClient(), clock, server.url("/so").toString(), server.url("/cn").toString(), server.url("/global").toString())
            val job = launch { client.search("Kotlin 教程") }
            delay(250)
            withTimeout(2000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }

    private val forecast = """{"current":{"time":"2026-09-11T13:15","temperature_2m":30.5,"weather_code":0},"daily":{"time":["2026-09-11"],"weather_code":[0],"temperature_2m_max":[30.7],"temperature_2m_min":[16.9],"precipitation_probability_max":[0]}}"""
}
