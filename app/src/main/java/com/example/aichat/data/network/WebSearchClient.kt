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
    private val bingBlockPattern = Pattern.compile("<li class=\"b_algo\"[^>]*>(.*?)</li>", Pattern.DOTALL)
    private val bingLinkPattern = Pattern.compile("<h2[^>]*>\\s*<a\\s+[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL)
    private val bingCaptionPattern = Pattern.compile("<div class=\"b_caption\"[^>]*>.*?<p[^>]*>(.*?)</p>", Pattern.DOTALL)
    private val paragraphPattern = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL)

    private val soBlockPattern = Pattern.compile("<li[^>]*class=\"[^\"]*res-list[^\"]*\"[^>]*>(.*?)</li>", Pattern.DOTALL)
    private val soLinkPattern = Pattern.compile("<h3[^>]*>.*?<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL)
    private val soDescPattern = Pattern.compile("<p[^>]*class=\"res-desc\"[^>]*>(.*?)</p>", Pattern.DOTALL)

    internal fun cleanSearchQuery(rawQuery: String): String {
        var q = rawQuery.trim()
        q = q.replace(Regex("[？?！!。，,;；~]+$"), "").trim()
        val prefixPatterns = listOf(
            "^请问[，,\\s]*",
            "^请帮我(?:联网)?(?:查一下|查下|搜索一下|搜索下|检索一下|查询|搜索)[，,\\s]*",
            "^帮我(?:联网)?(?:查一下|查下|搜索一下|搜索下|检索一下|查询|搜索)[，,\\s]*",
            "^请(?:联网)?(?:查一下|查下|搜索一下|搜索下|检索一下|查询|搜索)[，,\\s]*",
            "^(?:联网)?(?:查一下|查下|搜索一下|搜索下|检索一下|查询|搜索)[，,\\s]*",
            "^我想知道[，,\\s]*",
            "^你知道[，,\\s]*",
        )
        for (pattern in prefixPatterns) {
            q = q.replace(Regex(pattern), "").trim()
        }
        if (q.startsWith("谁是") && q.length > 4) {
            q = q.removePrefix("谁是").trim()
        } else if (q.startsWith("什么是") && q.length > 5) {
            q = q.removePrefix("什么是").trim()
        }
        val suffixPatterns = listOf(
            "[，,\\s]*(?:请问|顺便介绍一下.*|详细说明一下.*|告诉我答案.*)$",
            "[，,\\s]*(?:是谁|是什么|是多少)$"
        )
        for (pattern in suffixPatterns) {
            q = q.replace(Regex(pattern), "").trim()
        }
        return q.ifBlank { rawQuery.trim() }
    }

    private fun isLowQualityDictionaryResult(title: String): Boolean {
        return title.contains("（汉语汉字）") ||
            title.contains("（汉语文字）") ||
            title.contains("的意思,周的解释") ||
            title.contains("的拼音,部首,笔画")
    }

    suspend fun search(query: String, maxResults: Int = 5): List<WebSearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = cleanSearchQuery(query)
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // 1. Try 360 Search (Fast, accurate Chinese news and fact search, no anti-crawler block)
        val soResults = runCatching { fetchSoResults(cleanQuery, maxResults) }.getOrNull()
        if (!soResults.isNullOrEmpty()) {
            return@withContext soResults
        }

        // 2. Try China Bing endpoint with cleaned query
        val cnResults = runCatching { fetchBingResults("https://cn.bing.com/search", cleanQuery, maxResults) }.getOrNull()
        if (!cnResults.isNullOrEmpty()) {
            return@withContext cnResults
        }

        // 3. Fallback to global Bing endpoint
        val globalResults = runCatching { fetchBingResults("https://www.bing.com/search", cleanQuery, maxResults) }.getOrNull()
        if (!globalResults.isNullOrEmpty()) {
            return@withContext globalResults
        }

        emptyList()
    }

    private fun fetchSoResults(query: String, maxResults: Int): List<WebSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.so.com/s?q=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val html = response.body?.string().orEmpty()
            return parseSoHtml(html, maxResults)
        }
    }

    internal fun parseSoHtml(html: String, maxResults: Int): List<WebSearchResult> {
        if (html.isBlank()) return emptyList()
        val results = mutableListOf<WebSearchResult>()
        val blockMatcher = soBlockPattern.matcher(html)

        while (blockMatcher.find() && results.size < maxResults) {
            val block = blockMatcher.group(1) ?: continue
            val linkMatcher = soLinkPattern.matcher(block)
            if (!linkMatcher.find()) continue

            val rawUrl = linkMatcher.group(1).orEmpty().trim()
            val rawTitle = linkMatcher.group(2).orEmpty()
            val cleanTitle = cleanHtmlText(rawTitle)

            if (rawUrl.isBlank() || cleanTitle.isBlank() || isLowQualityDictionaryResult(cleanTitle)) continue

            var snippet = ""
            val descMatcher = soDescPattern.matcher(block)
            if (descMatcher.find()) {
                snippet = cleanHtmlText(descMatcher.group(1).orEmpty())
            } else {
                val pMatcher = paragraphPattern.matcher(block)
                if (pMatcher.find()) {
                    snippet = cleanHtmlText(pMatcher.group(1).orEmpty())
                }
            }

            if (snippet.isNotBlank()) {
                results.add(
                    WebSearchResult(
                        title = cleanTitle,
                        url = rawUrl,
                        snippet = snippet,
                    )
                )
            }
        }
        return results
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
        val blockMatcher = bingBlockPattern.matcher(html)

        while (blockMatcher.find() && results.size < maxResults) {
            val block = blockMatcher.group(1) ?: continue
            val linkMatcher = bingLinkPattern.matcher(block)
            if (!linkMatcher.find()) continue

            val rawUrl = linkMatcher.group(1).orEmpty().trim()
            val rawTitle = linkMatcher.group(2).orEmpty()
            val cleanTitle = cleanHtmlText(rawTitle)

            if (rawUrl.isBlank() || !rawUrl.startsWith("http") || cleanTitle.isBlank() || isLowQualityDictionaryResult(cleanTitle)) continue

            var snippet = ""
            val captionMatcher = bingCaptionPattern.matcher(block)
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
