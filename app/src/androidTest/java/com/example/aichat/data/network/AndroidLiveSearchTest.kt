package com.example.aichat.data.network

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Clock

class AndroidLiveSearchTest {
    @Test fun weatherAndSearchUseAndroidNetworkStack() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSearch") == "true")
        val weather = WeatherSearchClient(OkHttpClient(), Clock.systemDefaultZone()).search("今天北京天气怎么样")
        assertNotNull(weather)
        assertTrue(weather!!.snippet.contains("逐日预报"))
        assertTrue(weather.snippet.contains("北京"))
        android.util.Log.i("LiveSearchTest", weather.snippet)
        val english = WebSearchClient().search("weather in Beijing today")
        assertTrue(english.any { it.url.startsWith("https://api.open-meteo.com/") })
        val ordinary = WebSearchClient().search("Kotlin 协程 官方文档")
        assertTrue(ordinary.isNotEmpty())
        android.util.Log.i("LiveSearchTest", ordinary.joinToString { it.url })
        Unit
    }
}
