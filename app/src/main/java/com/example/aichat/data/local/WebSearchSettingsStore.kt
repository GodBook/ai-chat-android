package com.example.aichat.data.local

import android.content.Context
import com.example.aichat.data.model.ProviderConfig
import com.example.aichat.data.network.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class WebSearchSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("web_search_config", Context.MODE_PRIVATE)
    private val keys = ApiKeyStore(context, "web_search")
    @Synchronized fun read(): WebSearchConfig = WebSearchConfig(
        endpoint = prefs.getString("endpoint", DEFAULT_SEARCH_ENDPOINT) ?: DEFAULT_SEARCH_ENDPOINT,
        model = prefs.getString("model", DEFAULT_SEARCH_MODEL) ?: DEFAULT_SEARCH_MODEL,
    )
    @Synchronized fun hasKey(): Boolean = keys.hasKey()
    @Synchronized fun resolve(chat: ProviderConfig): WebSearchConfig {
        val settings = read()
        val dedicatedKey = keys.read().takeIf { prefs.getString("key_endpoint", null) == settings.endpoint }
        return settings.copy(apiKey = dedicatedKey ?: chat.apiKey.takeIf { canReuseChatKey(chat.baseUrl, settings.endpoint) })
    }
    @Synchronized fun save(endpoint: String, model: String, newKey: String?, clearKey: Boolean = false) {
        val url = endpoint.trim().trimEnd('/').toHttpUrlOrNull()
        require(url != null && url.scheme == "https" && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "搜索地址必须是有效的 HTTPS 基址，且不能含账号或查询参数" }
        require(!url.encodedPath.endsWith("/chat/completions")) { "搜索使用 Anthropic Messages 接口，不能填写 chat/completions 地址" }
        require(model.isNotBlank()) { "请填写搜索模型" }
        val base = url.toString().trimEnd('/').removeSuffix("/messages")
        val editor = prefs.edit().putString("endpoint", base).putString("model", model.trim())
        if (clearKey || prefs.getString("key_endpoint", null)?.let { it != base } == true) { keys.clear(); editor.remove("key_endpoint") }
        if (!newKey.isNullOrBlank()) { keys.save(newKey.trim()); editor.putString("key_endpoint", base) }
        check(editor.commit()) { "搜索设置保存失败" }
    }
}
