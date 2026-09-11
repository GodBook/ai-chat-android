package com.example.aichat.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.time.Clock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

class WebSearchClient(
    httpClient: OkHttpClient? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val soEndpoint: String = "https://www.so.com/s",
    private val bingEndpoint: String = "https://cn.bing.com/search",
    private val globalBingEndpoint: String = "https://www.bing.com/search",
    private val geocodingEndpoint: String = "https://geocoding-api.open-meteo.com/v1/search",
    private val forecastEndpoint: String = "https://api.open-meteo.com/v1/forecast",
) {
    private val client = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
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

    suspend fun search(query: String, maxResults: Int = 5, previousQuery: String? = null): List<WebSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || maxResults <= 0) return@withContext emptyList()
        val effectiveQuery = if (query.length <= 24 && Regex("^(那|那么)?(明天|后天|今天|现在|最新).{0,8}$").matches(query.trim()) && previousQuery != null) {
            val place = extractWeatherPlace(previousQuery)
            if (place != null && Regex("天气|气温|下雨|下雪").containsMatchIn(previousQuery)) "$place 天气 $query" else query
        } else query
        val weather = safely { WeatherSearchClient(client, clock, geocodingEndpoint, forecastEndpoint).search(effectiveQuery, previousQuery) }
        if (weather != null) return@withContext listOf(weather)
        val plan = planSearch(cleanSearchQuery(effectiveQuery), clock)
        val all = coroutineScope {
            listOf(
                async { safely { fetchSoResults(plan.query, 10) }.orEmpty() },
                async { safely { fetchBingResults(bingEndpoint, plan.query, 10, plan.timeSensitive) }.orEmpty() },
                async { safely { fetchBingResults(globalBingEndpoint, plan.query, 10, plan.timeSensitive) }.orEmpty() },
            ).awaitAll().flatten()
        }
        rankSearchResults(all, plan, maxResults)
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try { block() } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { null }

    private suspend fun fetchSoResults(query: String, maxResults: Int): List<WebSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$soEndpoint?q=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        client.searchResponse(request).use { response ->
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

            val directUrl = Regex("data-mdurl=[\"']([^\"']+)[\"']").find(linkMatcher.group())?.groupValues?.get(1)
            val rawUrl = normalizeResultUrl(directUrl ?: linkMatcher.group(1).orEmpty().trim()) ?: continue
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

            if (snippet.isBlank()) {
                snippet = Regex("<span[^>]*class=[\"'][^\"']*res-list-summary[^\"']*[\"'][^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                    .find(block)?.groupValues?.get(1)?.let(::cleanHtmlText).orEmpty()
            }
            val dateText = Regex("<span[^>]*class=[\"'][^\"']*g-c-gray[^\"']*[\"'][^>]*>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
                .find(block)?.groupValues?.get(1)?.let(::cleanHtmlText).orEmpty()
            if (dateText.isNotBlank() && !snippet.contains(dateText)) snippet = "$dateText $snippet"
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

    private suspend fun fetchBingResults(baseUrl: String, query: String, maxResults: Int, recent: Boolean): List<WebSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl?q=$encodedQuery" + if (recent) "&filters=ex1%3A%22ez1%22" else ""
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        client.searchResponse(request).use { response ->
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

            val rawUrl = normalizeResultUrl(linkMatcher.group(1).orEmpty().trim()) ?: continue
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
        val blocksSeparated = raw.replace(Regex("</(?:p|div|li)>|<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        val noTags = htmlTagPattern.matcher(blocksSeparated).replaceAll("")
        return unescapeHtml(noTags).trim().replace(Regex("\\s+"), " ")
    }

    internal fun normalizeResultUrl(raw: String): String? {
        val url = unescapeHtml(raw).toHttpUrlOrNull() ?: return null
        if (url.host.endsWith("bing.com") && url.encodedPath == "/ck/a") {
            val encoded = url.queryParameter("u")?.removePrefix("a1") ?: return null
            return runCatching { String(java.util.Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8).toHttpUrlOrNull()?.toString() }.getOrNull()
        }
        if (url.host.endsWith("so.com") && url.encodedPath == "/link") {
            return url.queryParameter("url")?.toHttpUrlOrNull()?.toString()
        }
        return url.toString()
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
        return res.replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) { match ->
            runCatching {
                val value = match.groupValues[1]
                val code = if (value.startsWith("x")) value.drop(1).toInt(16) else value.toInt()
                String(Character.toChars(code))
            }.getOrDefault(match.value)
        }
    }
}
