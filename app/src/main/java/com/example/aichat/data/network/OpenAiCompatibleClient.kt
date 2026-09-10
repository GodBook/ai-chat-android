package com.example.aichat.data.network

import android.util.Base64
import com.example.aichat.data.local.ImageStore
import com.example.aichat.data.model.ChatRequestMessage
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.ProviderConfig
import com.example.aichat.data.model.modelCandidatesFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class ChatErrorKind {
    MISSING_CONFIG,
    INVALID_REQUEST,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    STREAM_INTERRUPTED,
    PROVIDER,
}

class ChatClientException(
    val kind: ChatErrorKind,
    override val message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
    /** True when the user's message is already in Room and should not be restored as a draft. */
    val requestWasPersisted: Boolean = false,
) : IOException(message, cause)

sealed interface ChatStreamEvent {
    data class Delta(val text: String) : ChatStreamEvent
    data object Done : ChatStreamEvent
}

/** OpenAI /v1/chat/completions compatible streaming client. */
class OpenAiCompatibleClient(
    private val imageFileStore: ImageStore,
    httpClient: OkHttpClient? = null,
    private val allowInsecureHttp: Boolean = false,
) {
    private val client = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Streams a reply, retrying with the fallback model when the configured one is gone
     * (expired timed releases, renamed models, and so on).
     *
     * A retry only happens before any text has been emitted, so a reply is never duplicated.
     * The first error is the one reported when every candidate fails.
     */
    fun streamChat(
        config: ProviderConfig,
        messages: List<ChatRequestMessage>,
    ): Flow<ChatStreamEvent> = flow {
        val candidates = if (config.autoFallbackEnabled) {
            modelCandidatesFor(config.model)
        } else {
            listOf(config.model.trim())
        }
        var firstFailure: ChatClientException? = null
        for ((index, model) in candidates.withIndex()) {
            var emittedAnyText = false
            try {
                streamChatOnce(config, model, messages).collect { event ->
                    if (event is ChatStreamEvent.Delta) emittedAnyText = true
                    emit(event)
                }
                return@flow
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ChatClientException) {
                val canRetry = !emittedAnyText &&
                    index < candidates.lastIndex &&
                    failure.isModelUnavailable()
                if (!canRetry) throw firstFailure ?: failure
                if (firstFailure == null) firstFailure = failure
            }
        }
    }

    private fun streamChatOnce(
        config: ProviderConfig,
        model: String,
        messages: List<ChatRequestMessage>,
    ): Flow<ChatStreamEvent> = flow {
        val endpoint = validateAndBuildEndpoint(config)
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer ${config.apiKey!!.trim()}")
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .post(ChatCompletionsRequestBody(model.trim(), messages, imageFileStore))
            .build()
        val call = client.newCall(request)
        val cancellationSignal = call.cancellationSignal(currentCoroutineContext()[Job])
        var responseOpened = false
        try {
            val response = call.execute()
            responseOpened = true
            response.use {
                if (!it.isSuccessful) {
                    throw parseHttpError(it.code, it.body?.string().orEmpty())
                }
                val body = it.body ?: throw ChatClientException(
                    ChatErrorKind.NETWORK,
                    "服务端没有返回响应内容",
                    it.code,
                )
                val source = body.source()
                var done = false
                suspend fun processEvent(data: String) {
                    if (data.isBlank()) return
                    if (data == "[DONE]") {
                        done = true
                        emit(ChatStreamEvent.Done)
                        return
                    }
                    val chunk = runCatching {
                        json.decodeFromString<ChatCompletionChunk>(data)
                    }.getOrElse { parseStreamError(data, it) }
                    chunk.error?.message?.takeIf { it.isNotBlank() }?.let { message ->
                        throw ChatClientException(ChatErrorKind.PROVIDER, message)
                    }
                    chunk.choices.asSequence()
                        .mapNotNull { choice -> choice.delta.content }
                        .filter { content -> content.isNotEmpty() }
                        .forEach { content -> emit(ChatStreamEvent.Delta(content)) }
                }
                val parser = SseParser()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    parser.accept(line).forEach { processEvent(it) }
                    if (done) break
                }
                if (!done) {
                    parser.finish().forEach { processEvent(it) }
                }
                if (!done) {
                    throw ChatClientException(
                        ChatErrorKind.STREAM_INTERRUPTED,
                        "流式响应意外中断",
                        it.code,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: ChatClientException) {
            throw exception
        } catch (exception: IOException) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("请求已取消")
            }
            throw ChatClientException(
                if (responseOpened) ChatErrorKind.STREAM_INTERRUPTED else ChatErrorKind.NETWORK,
                if (responseOpened) "流式响应连接中断" else (exception.message ?: "网络连接失败"),
                cause = exception,
            )
        } finally {
            cancellationSignal.complete()
        }
    }

    /** Validates user configuration before a message is persisted or sent. */
    fun validateConfig(config: ProviderConfig) {
        validateAndBuildEndpoint(config)
    }

    private fun validateAndBuildEndpoint(config: ProviderConfig): String {
        if (config.apiKey.isNullOrBlank()) {
            throw ChatClientException(ChatErrorKind.MISSING_CONFIG, "请先在设置中填写 API Key")
        }
        if (config.model.isBlank()) {
            throw ChatClientException(ChatErrorKind.MISSING_CONFIG, "请先在设置中填写模型名称")
        }
        val base = config.baseUrl.trim().removeSuffix("/")
        if (base.isBlank()) {
            throw ChatClientException(ChatErrorKind.MISSING_CONFIG, "请先在设置中填写接口地址")
        }
        val endpoint = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val parsed = endpoint.toHttpUrlOrNull()
        if (parsed == null || (!allowInsecureHttp && parsed.scheme != "https")) {
            throw ChatClientException(ChatErrorKind.INVALID_REQUEST, "接口地址必须是有效的 HTTPS 地址")
        }
        return parsed.toString()
    }

    private fun parseHttpError(statusCode: Int, body: String): ChatClientException {
        val providerError = runCatching {
            json.decodeFromString<ProviderErrorEnvelope>(body).error
        }.getOrNull()
        val fallback = when (statusCode) {
            401, 403 -> "API Key 无效或无权限"
            429 -> "请求过于频繁，请稍后再试"
            in 500..599 -> "模型服务暂时不可用"
            else -> "模型服务请求失败（HTTP $statusCode）"
        }
        val kind = when (statusCode) {
            401, 403 -> ChatErrorKind.UNAUTHORIZED
            429 -> ChatErrorKind.RATE_LIMITED
            in 500..599 -> ChatErrorKind.SERVER
            else -> ChatErrorKind.PROVIDER
        }
        return ChatClientException(
            kind = kind,
            message = providerError?.message?.takeIf { it.isNotBlank() } ?: fallback,
            statusCode = statusCode,
        )
    }

    private fun parseStreamError(data: String, cause: Throwable): Nothing {
        val providerError = runCatching {
            json.decodeFromString<ProviderErrorEnvelope>(data).error
        }.getOrNull()
        throw ChatClientException(
            ChatErrorKind.PROVIDER,
            providerError?.message?.takeIf { it.isNotBlank() } ?: "无法解析模型服务的流式响应",
            cause = cause,
        )
    }

    private fun okhttp3.Call.cancellationSignal(parent: Job?): kotlinx.coroutines.CompletableJob =
        Job(parent).also { signal ->
            signal.invokeOnCompletion { failure ->
                if (failure is CancellationException) cancel()
            }
        }
}

/** Status codes that can mean the requested model is gone, usually a 404 from the provider. */
private val MODEL_UNAVAILABLE_STATUSES = setOf(null, 400, 403, 404, 422)

/** The message has to talk about the model... */
private val MODEL_TOKENS = listOf("model", "模型")

/** ...and say it is gone, unknown or off limits. */
private val MODEL_UNAVAILABLE_TOKENS = listOf(
    "not found",
    "does not exist",
    "not exist",
    "unknown",
    "invalid",
    "unavailable",
    "not available",
    "unsupported",
    "not supported",
    "deprecated",
    "expired",
    "no access",
    "不存在",
    "未知",
    "无效",
    "不可用",
    "不支持",
    "过期",
    "下线",
    "已失效",
    "无权限",
)

/**
 * True when the provider rejected the model itself rather than the request or the account.
 *
 * Both a model token and an unavailability token are required: a generic 400 such as
 * "模型服务请求失败（HTTP 400）" or a key problem must not silently switch the model.
 */
private fun ChatClientException.isModelUnavailable(): Boolean {
    if (statusCode == 404) return true
    if (statusCode !in MODEL_UNAVAILABLE_STATUSES) return false
    val text = message.lowercase()
    return MODEL_TOKENS.any { text.contains(it) } &&
        MODEL_UNAVAILABLE_TOKENS.any { text.contains(it) }
}

private class ChatCompletionsRequestBody(
    private val model: String,
    private val messages: List<ChatRequestMessage>,
    private val imageFileStore: ImageStore,
) : RequestBody() {
    override fun contentType() = "application/json; charset=utf-8".toMediaType()

    override fun writeTo(sink: BufferedSink) {
        sink.writeUtf8("{\"model\":")
        sink.writeUtf8(jsonQuote(model))
        sink.writeUtf8(",\"stream\":true,\"messages\":[")
        messages.forEachIndexed { index, message ->
            if (index > 0) sink.writeByte(','.code)
            sink.writeUtf8("{\"role\":")
            sink.writeUtf8(jsonQuote(message.role.providerValue))
            sink.writeUtf8(",\"content\":")
            if (message.imagePaths.isEmpty()) {
                sink.writeUtf8(jsonQuote(message.text))
            } else {
                sink.writeByte('['.code)
                var contentIndex = 0
                if (message.text.isNotBlank()) {
                    sink.writeUtf8("{\"type\":\"text\",\"text\":")
                    sink.writeUtf8(jsonQuote(message.text))
                    sink.writeByte('}'.code)
                    contentIndex++
                }
                message.imagePaths.forEach { path ->
                    if (contentIndex++ > 0) sink.writeByte(','.code)
                    val mime = imageFileStore.mimeType(path)
                    sink.writeUtf8("{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:$mime;base64,")
                    // The Base64 payload is streamed from the private image file in small chunks.
                    streamBase64(sink, File(path))
                    sink.writeUtf8("\"}}")
                }
                sink.writeByte(']'.code)
            }
            sink.writeByte('}'.code)
        }
        sink.writeUtf8("]}")
    }

    private fun streamBase64(sink: BufferedSink, file: File) {
        if (!file.isFile) throw IOException("图片文件不存在")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var carry = ByteArray(0)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                val combined = ByteArray(carry.size + read)
                carry.copyInto(combined)
                buffer.copyInto(combined, carry.size, 0, read)
                val completeSize = combined.size - (combined.size % 3)
                if (completeSize > 0) {
                    sink.writeUtf8(Base64.encodeToString(combined, 0, completeSize, Base64.NO_WRAP))
                }
                carry = combined.copyOfRange(completeSize, combined.size)
            }
            if (carry.isNotEmpty()) {
                sink.writeUtf8(Base64.encodeToString(carry, Base64.NO_WRAP))
            }
        }
    }
}

private fun jsonQuote(value: String): String = JsonPrimitive(value).toString()

private val MessageRole.providerValue: String
    get() = when (this) {
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
    }
