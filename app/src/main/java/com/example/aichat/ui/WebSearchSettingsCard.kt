package com.example.aichat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.aichat.AiChatApplication

@Composable
internal fun WebSearchSettingsCard() {
    val context = LocalContext.current
    val store = remember { (context.applicationContext as AiChatApplication).container.webSearchSettingsStore }
    val config = remember { store.read() }
    var endpoint by remember { mutableStateOf(config.endpoint) }
    var model by remember { mutableStateOf(config.model) }
    var key by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(store.hasKey()) }
    var notice by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("联网搜索", style = MaterialTheme.typography.titleMedium)
            Text("采用 DeepSeek Harness 原生搜索协议，来源取自服务器搜索结果。每次联网提问会额外调用搜索模型并产生服务端费用。", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(endpoint, { endpoint = it }, label = { Text("搜索接口基址（HTTPS）") }, supportingText = { Text("独立于聊天地址，使用 Anthropic /v1/messages 接口") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(model, { model = it }, label = { Text("搜索模型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(key, { key = it }, label = { Text(if (hasKey) "搜索密钥（已保存，留空不修改）" else "搜索密钥（可选）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("未单独设置密钥时，仅同一 HTTPS 主机可复用聊天密钥。使用中转服务时，请填写该服务支持的搜索接口及密钥。", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    notice = runCatching { store.save(endpoint, model, key); key = ""; hasKey = store.hasKey() }.fold({ "搜索设置已保存" }, { it.message })
                }) { Text("保存搜索设置") }
                TextButton(enabled = hasKey, onClick = {
                    notice = runCatching { store.save(endpoint, model, null, clearKey = true); key = ""; hasKey = false }.fold({ "独立搜索密钥已删除" }, { it.message })
                }) { Text("删除搜索密钥") }
            }
            notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
