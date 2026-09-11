package com.example.aichat.data.network

import com.example.aichat.data.model.ProviderConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val DEFAULT_SEARCH_ENDPOINT = "https://api.deepseek.com/anthropic/v1"
const val DEFAULT_SEARCH_MODEL = "deepseek-v4-flash"
data class WebSearchConfig(val endpoint: String = DEFAULT_SEARCH_ENDPOINT, val model: String = DEFAULT_SEARCH_MODEL, val apiKey: String? = null)

/** Never forward a chat gateway's secret to a different search host. */
internal fun canReuseChatKey(chatEndpoint: String, searchEndpoint: String): Boolean {
    val chat = chatEndpoint.toHttpUrlOrNull() ?: return false
    val search = searchEndpoint.toHttpUrlOrNull() ?: return false
    return chat.scheme == "https" && search.scheme == "https" && chat.host == search.host && chat.port == search.port
}
internal fun defaultSearchConfig(chat: ProviderConfig) = WebSearchConfig(apiKey = chat.apiKey.takeIf { canReuseChatKey(chat.baseUrl, DEFAULT_SEARCH_ENDPOINT) })
