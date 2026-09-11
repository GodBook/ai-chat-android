package com.example.aichat.data.network

import com.example.aichat.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Clock
import java.util.concurrent.TimeUnit

@Serializable
data class WebSearchResult(val title: String, val url: String, val snippet: String, val publishedAt: String? = null)

/** Protocol follows deepseek-harness/packages/web/web-search-deepseek.
 * Only native tool-result blocks are evidence; generated prose never becomes a source.
 */
class WebSearchClient(
    httpClient: OkHttpClient? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val resolveConfig: (ProviderConfig) -> WebSearchConfig = ::defaultSearchConfig,
    private val allowInsecureHttp: Boolean = false,
) {
    private val client = (httpClient ?: OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build())
        .newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun search(query: String, config: ProviderConfig, maxResults: Int = 8, previousQuery: String? = null): List<WebSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || maxResults <= 0) return@withContext emptyList()
        val options = resolveConfig(config)
        val key = options.apiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw ChatClientException(ChatErrorKind.MISSING_CONFIG, "请在设置 → 联网搜索中配置搜索密钥；仅当聊天与搜索使用同一 HTTPS 主机时才会复用聊天密钥")
        val base = options.endpoint.trim().trimEnd('/').removeSuffix("/messages").toHttpUrlOrNull()
        require(base != null && (base.scheme == "https" || allowInsecureHttp) && base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null) { "请填写有效的 HTTPS 搜索地址" }
        val endpoint = base.newBuilder().addPathSegment("messages").build()
        val planned = planSearch(query, clock)
        val text = buildString {
            append("Perform a web search for the query: ").append(planned.query)
            append("\nCurrent date and timezone: ").append(java.time.ZonedDateTime.now(clock))
            if (!previousQuery.isNullOrBlank()) append("\nPrevious user question (context for resolving references only): ").append(previousQuery.take(1200))
        }
        val body = buildJsonObject {
            put("model", options.model); put("max_tokens", 4096)
            putJsonArray("messages") { add(buildJsonObject {
                put("role", "user")
                putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", text) }) }
            }) }
            putJsonArray("tools") { add(buildJsonObject {
                put("type", "web_search_20250305"); put("name", "web_search"); put("max_uses", 5)
            }) }
        }
        val request = Request.Builder().url(endpoint).header("x-api-key", key)
            .header("Authorization", "Bearer $key").header("anthropic-version", "2023-06-01")
            .header("Accept", "application/json").header("User-Agent", "AI-BOTOY/1.6.9")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        try {
            client.searchResponse(request).use { response ->
                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        401, 403 -> "搜索密钥无效或没有原生搜索权限"
                        404 -> "搜索接口不存在，请核对 Anthropic Messages 基址"
                        429 -> "搜索服务限流，请稍后再试"
                        in 300..399 -> "搜索服务返回重定向，已停止以保护密钥；请填写最终搜索基址"
                        else -> "搜索服务请求失败（HTTP ${response.code}）"
                    }
                    throw ChatClientException(ChatErrorKind.PROVIDER, "$message。可到设置 → 联网搜索调整；未使用模型记忆替代搜索。", response.code)
                }
                parseNativeSearchResults(response.body?.string().orEmpty(), maxResults)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: ChatClientException) { throw error }
        catch (_: Exception) { throw ChatClientException(ChatErrorKind.NETWORK, "联网搜索连接或响应异常，请检查网络及独立搜索配置后重试") }
    }
}

internal fun safeSourceUrl(raw: String): String? = raw.toHttpUrlOrNull()?.takeIf { it.username.isEmpty() && it.password.isEmpty() }?.toString()

internal fun parseNativeSearchResults(body: String, maxResults: Int = 8): List<WebSearchResult> {
    val envelope = Json.parseToJsonElement(body).jsonObject
    val blocks = envelope["content"] as? JsonArray ?: throw ChatClientException(ChatErrorKind.PROVIDER, "搜索响应缺少结构化来源，请确认接口支持 DeepSeek 原生 web_search")
    val resultBlocks = blocks.mapNotNull { it as? JsonObject }.filter { it["type"]?.jsonPrimitive?.content == "web_search_tool_result" }
    if (resultBlocks.isEmpty()) throw ChatClientException(ChatErrorKind.PROVIDER, "服务未执行原生搜索（缺少 web_search_tool_result）；请检查搜索模型及接口，不会把生成的文字当作搜索结果")
    val citations = linkedMapOf<String, MutableSet<String>>()
    for (block in blocks.mapNotNull { it as? JsonObject }) {
        if (block["type"]?.jsonPrimitive?.content != "text") continue
        for (item in (block["citations"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }) {
            val url = item["url"]?.jsonPrimitive?.contentOrNull?.let(::safeSourceUrl) ?: continue
            val excerpt = item["cited_text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            citations.getOrPut(url) { linkedSetOf() }.add(excerpt)
        }
    }
    val results = linkedMapOf<String, WebSearchResult>()
    for (block in resultBlocks) {
        val content = block["content"]
        if (content is JsonObject && content["type"]?.jsonPrimitive?.content == "web_search_tool_result_error") throw ChatClientException(ChatErrorKind.PROVIDER, "原生搜索工具执行失败，请稍后重试；没有返回可验证的来源")
        for (item in (content as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }) {
            if (item["type"]?.jsonPrimitive?.content != "web_search_result") continue
            val url = item["url"]?.jsonPrimitive?.contentOrNull?.let(::safeSourceUrl) ?: continue
            if (url in results) continue
            results[url] = WebSearchResult(
                title = item["title"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: url.toHttpUrlOrNull()!!.host,
                url = url, snippet = citations[url]?.joinToString("\n")?.take(6000).orEmpty(),
                publishedAt = item["page_age"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }
    return results.values.take(maxResults.coerceAtLeast(0))
}
