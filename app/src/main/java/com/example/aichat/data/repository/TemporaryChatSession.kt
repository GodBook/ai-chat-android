package com.example.aichat.data.repository

import com.example.aichat.data.model.*
import com.example.aichat.data.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.util.UUID

data class TemporaryChatState(
    val id: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val images: List<String> = emptyList(),
    val working: Boolean = false,
    val searchEnabled: Boolean = false,
    val searching: Boolean = false,
)

/** Deliberately has no database, file, backup or memory-store dependency. */
class TemporaryChatSession(
    private val scope: CoroutineScope,
    private val stream: (ProviderConfig, List<ChatRequestMessage>) -> Flow<ChatStreamEvent>,
    private val search: suspend (String, ProviderConfig) -> List<WebSearchResult>,
) {
    private val mutable = MutableStateFlow(TemporaryChatState())
    val state: StateFlow<TemporaryChatState> = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var activeRequest: String? = null

    fun start() { close(); mutable.value = TemporaryChatState(id = "temporary:${UUID.randomUUID()}") }
    fun close() { activeRequest = null; mutable.value = TemporaryChatState(); job?.cancel(); job = null }
    fun stop() {
        val stoppedRequest = activeRequest
        activeRequest = null
        job?.cancel(); job = null
        // Also handles a request cancelled before its coroutine first starts.
        mutable.update { current -> current.copy(working = false, searching = false, messages = current.messages.map {
            if (it.requestId == stoppedRequest && it.status in setOf(MessageStatus.SENDING, MessageStatus.STREAMING))
                it.copy(status = MessageStatus.INTERRUPTED, errorMessage = "已停止") else it
        }) }
    }
    fun toggleSearch() { mutable.update { it.copy(searchEnabled = !it.searchEnabled) } }
    fun addImage(sessionId: String, dataUri: String) {
        require(dataUri.startsWith("data:image/") && dataUri.length <= 8_000_000)
        mutable.update { if (it.id == sessionId && it.images.size < 4) it.copy(images = it.images + dataUri) else it }
    }
    fun removeImage(image: String) { mutable.update { it.copy(images = it.images - image) } }
    fun deleteMessage(id: String) { if (!mutable.value.working) mutable.update { it.copy(messages = it.messages.filterNot { m -> m.id == id }) } }
    fun clear() { if (mutable.value.id != null) start() }

    fun send(text: String, config: ProviderConfig, images: List<String> = mutable.value.images) {
        val snapshot = mutable.value
        val id = snapshot.id ?: return
        if (snapshot.working || (text.isBlank() && images.isEmpty())) return
        require(images.all { it.startsWith("data:image/") }) { "临时对话只接受内存图片" }
        val requestId = UUID.randomUUID().toString()
        activeRequest = requestId
        val user = ChatMessage(UUID.randomUUID().toString(), MessageRole.USER, text.trim(), images, requestId = requestId, conversationId = id)
        val assistant = ChatMessage(UUID.randomUUID().toString(), MessageRole.ASSISTANT, "", status = MessageStatus.SENDING, requestId = requestId, conversationId = id)
        mutable.value = snapshot.copy(messages = (snapshot.messages + user + assistant).takeLast(40), images = emptyList(), working = true, searching = snapshot.searchEnabled)
        job = scope.launch(Dispatchers.IO) {
            var answer = assistant
            val thinking = StringBuilder()
            val content = StringBuilder()
            val started = System.currentTimeMillis()
            fun publish() { mutable.update { current -> if (current.id == id && activeRequest == requestId) current.copy(messages = current.messages.map { if (it.id == answer.id) answer else it }) else current } }
            try {
                require(images.isEmpty() || config.visionEnabled) { "请先开启图片支持" }
                val sources = if (snapshot.searchEnabled) search(user.text, config) else null
                answer = answer.copy(webSearchResults = sources)
                mutable.update { if (it.id == id && activeRequest == requestId) it.copy(searching = false) else it }
                publish()
                val prompt = if (sources != null) "${user.text}\n\n${buildSearchContext(user.text, sources, Clock.systemDefaultZone())}" else user.text
                // A single request only. Prior normal or temporary messages never enter the payload.
                stream(config, listOf(ChatRequestMessage(MessageRole.USER, prompt, images))).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> content.append(event.text)
                        is ChatStreamEvent.ThinkingDelta -> thinking.append(event.text)
                        ChatStreamEvent.Done -> Unit
                    }
                    answer = answer.copy(text = content.toString(), thinkingContent = thinking.toString().ifBlank { null }, status = MessageStatus.STREAMING)
                    publish()
                }
                answer = answer.copy(status = MessageStatus.SENT, thinkingDurationMs = if (thinking.isNotEmpty()) System.currentTimeMillis() - started else null)
            } catch (cancelled: CancellationException) {
                answer = answer.copy(status = MessageStatus.INTERRUPTED, errorMessage = "已停止")
            } catch (failure: Exception) {
                answer = answer.copy(status = MessageStatus.FAILED, errorMessage = failure.message ?: "请求失败，请重试")
            } finally {
                publish()
                mutable.update { if (it.id == id && activeRequest == requestId) it.copy(working = false) else it }
            }
        }
    }

    fun retry(messageId: String, config: ProviderConfig) {
        val messages = mutable.value.messages
        val target = messages.find { it.id == messageId } ?: return
        val user = messages.firstOrNull { it.requestId == target.requestId && it.role == MessageRole.USER } ?: return
        send(user.text, config, user.imagePaths)
    }
}
