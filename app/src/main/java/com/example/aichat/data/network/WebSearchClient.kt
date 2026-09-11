package com.example.aichat.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Serializable
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

class WebSearchClient(
    httpClient: OkHttpClient? = null,
) {
    private val client = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val htmlTagPattern = Pattern.compile("<[^>]+>")
    private val blockPattern = Pattern.compile("<li class=\"b_algo\"[^>]*>(.*?)</li>", Pattern.DOTALL)
    private val linkPattern = Pattern.compile("<h2[^>]*>\\s*<a\\s+[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL)
    private val captionPattern = Pattern.compile("<div class=\"b_caption\"[^>]*>.*?<p[^>]*>(.*?)</p>", Pattern.DOTALL)
    private val paragraphPattern = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL)

    suspend fun search(query: String, maxResults: Int = 5): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // 1. Try China Bing endpoint (fast and accessible in mainland China & global)
        val cnResults = runCatching { fetchBingResults("https://cn.bing.com/search", cleanQuery, maxResults) }.getOrNull()
        if (!cnResults.isNullOrEmpty()) {
            return@withContext cnResults
        }

        // 2. Fallback to global Bing endpoint
        val globalResults = runCatching { fetchBingResults("https://www.bing.com/search", cleanQuery, maxResults) }.getOrNull()
        if (!globalResults.isNullOrEmpty()) {
            return@withContext globalResults
        }

        emptyList()
    }

    private fun fetchBingResults(baseUrl: String, query: String, maxResults: Int): List<WebSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl?q=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val html = response.body?.string().orEmpty()
            return parseBingHtml(html, maxResults)
        }
    }

    internal fun parseBingHtml(html: String, maxResults: Int): List<WebSearchResult> {
        if (html.isBlank()) return emptyList()
        val results = mutableListOf<WebSearchResult>()
        val blockMatcher = blockPattern.matcher(html)

        while (blockMatcher.find() && results.size < maxResults) {
            val block = blockMatcher.group(1) ?: continue
            val linkMatcher = linkPattern.matcher(block)
            if (!linkMatcher.find()) continue

            val rawUrl = linkMatcher.group(1).orEmpty().trim()
            val rawTitle = linkMatcher.group(2).orEmpty()

            if (rawUrl.isBlank() || !rawUrl.startsWith("http")) continue

            val cleanTitle = cleanHtmlText(rawTitle)
            if (cleanTitle.isBlank()) continue

            var snippet = ""
            val captionMatcher = captionPattern.matcher(block)
            if (captionMatcher.find()) {
                snippet = cleanHtmlText(captionMatcher.group(1).orEmpty())
            } else {
                val pMatcher = paragraphPattern.matcher(block)
                if (pMatcher.find()) {
                    snippet = cleanHtmlText(pMatcher.group(1).orEmpty())
                }
            }

            results.add(
                WebSearchResult(
                    title = cleanTitle,
                    url = rawUrl,
                    snippet = snippet,
                )
            )
        }

        return results
    }

    private fun cleanHtmlText(raw: String): String {
        val noTags = htmlTagPattern.matcher(raw).replaceAll(" ")
        return unescapeHtml(noTags).trim().replace(Regex("\\s+"), " ")
    }

    private fun unescapeHtml(text: String): String {
        var res = text
        res = res.replace("&amp;", "&")
        res = res.replace("&lt;", "<")
        res = res.replace("&gt;", ">")
        res = res.replace("&quot;", "\"")
        res = res.replace("&#39;", "'")
        res = res.replace("&apos;", "'")
        res = res.replace("&nbsp;", " ")
        res = res.replace("&ensp;", " ")
        res = res.replace("&emsp;", " ")
        res = res.replace("&#8216;", "‘")
        res = res.replace("&#8217;", "’")
        res = res.replace("&#8220;", "“")
        res = res.replace("&#8221;", "”")
        res = res.replace("&#8230;", "…")
        res = res.replace("&#183;", "·")
        res = res.replace("&#8212;", "—")
        return res
    }
}
