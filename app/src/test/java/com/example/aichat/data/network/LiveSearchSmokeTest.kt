package com.example.aichat.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Optional external-service check. Deterministic regression tests live in WebSearchClientTest. */
class LiveSearchSmokeTest {
    @Test fun liveWeatherAndOrdinarySearch() = runBlocking {
        assumeTrue(System.getenv("AI_BOTOY_LIVE_SEARCH_TEST") == "1")
        val client = WebSearchClient()
        val direct = WeatherSearchClient(okhttp3.OkHttpClient(), java.time.Clock.systemDefaultZone()).search("今天北京天气怎么样")
        assertNotNull("Direct weather lookup returned no matching city/date", direct)
        for (query in listOf("今天北京天气怎么样", "上海明天天气", "weather in Beijing today")) {
            val results = client.search(query)
            assertTrue("No structured source for $query", results.any { it.url.startsWith("https://api.open-meteo.com/") })
            val date = LocalDate.now(ZoneId.of("Asia/Shanghai")).let { if (query.contains("明天")) it.plusDays(1) else it }
            assertTrue(results.first().snippet.contains(date.toString()))
            println("LIVE WEATHER: $query -> ${results.first().title}\n${results.first().snippet}")
        }
        val results = client.search("Kotlin 协程 官方文档")
        assertTrue("Search engines returned no usable results", results.isNotEmpty())
        println("LIVE SEARCH: ${results.joinToString { it.url }}")
    }
}
