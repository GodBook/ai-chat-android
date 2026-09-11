package com.example.aichat.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import com.example.aichat.data.network.searchResponse
import java.util.concurrent.TimeUnit

@Serializable
data class ReleaseNote(val version: String, val publishedAt: String, val notes: String)

internal fun parseReleaseHistory(body: String): List<ReleaseNote> = Json.parseToJsonElement(body).jsonArray.mapNotNull { entry ->
    val item = entry.jsonObject
    if (item["draft"]?.jsonPrimitive?.booleanOrNull == true || item["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return@mapNotNull null
    val version = item["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v") ?: return@mapNotNull null
    if (!Regex("\\d+(\\.\\d+)+").matches(version)) return@mapNotNull null
    ReleaseNote(version, item["published_at"]?.jsonPrimitive?.contentOrNull.orEmpty(), item["body"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "此版本未提供发布说明。" })
}

internal fun mergeReleaseHistory(local: List<ReleaseNote>, remote: List<ReleaseNote>): List<ReleaseNote> =
    (remote + local).distinctBy { it.version }.sortedWith { a, b ->
        val av = a.version.split('.').map { it.toIntOrNull() ?: 0 }
        val bv = b.version.split('.').map { it.toIntOrNull() ?: 0 }
        (0 until maxOf(av.size, bv.size)).firstNotNullOfOrNull { i ->
            (bv.getOrElse(i) { 0 }).compareTo(av.getOrElse(i) { 0 }).takeIf { it != 0 }
        } ?: 0
    }

internal class ReleaseHistoryClient(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    private val releasesUrl: String = "https://api.github.com/repos/GodBook/ai-chat-android/releases",
    private val fallbackUrl: String = "https://raw.githubusercontent.com/GodBook/ai-chat-android/main/app/src/main/assets/release-history.json",
) {

    suspend fun fetch(): List<ReleaseNote> = withContext(Dispatchers.IO) {
        try { fetchPages() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            // The repository snapshot remains readable when GitHub's unauthenticated API is rate-limited.
            val request = Request.Builder().url(fallbackUrl).header("User-Agent", "AI-BOTOY-Android").build()
            client.searchResponse(request).use {
                if (!it.isSuccessful) throw failure
                Json.decodeFromString<List<ReleaseNote>>(it.body?.string().orEmpty()).also { entries ->
                    check(entries.isNotEmpty()) { "版本记录为空" }
                }
            }
        }
    }

    private suspend fun fetchPages(): List<ReleaseNote> {
        val result = mutableListOf<ReleaseNote>()
        // Fetch every page, so old versions remain visible as the release count grows.
        var page = 1
        do {
            val request = Request.Builder().url("$releasesUrl?per_page=100&page=$page")
                .header("Accept", "application/vnd.github+json").header("User-Agent", "AI-BOTOY-Android").build()
            val body = client.searchResponse(request).use {
                check(it.isSuccessful) { if (it.code == 403 || it.code == 429) "GitHub 请求频率受限，请稍后刷新" else "暂时无法获取版本记录（${it.code}）" }
                it.body?.string().orEmpty()
            }
            val count = Json.parseToJsonElement(body).jsonArray.size
            result += parseReleaseHistory(body)
            page++
        } while (count == 100)
        return result
    }
}
