package com.example.aichat.data.network

import com.example.aichat.data.local.ImageStore
import com.example.aichat.data.model.ChatRequestMessage
import com.example.aichat.data.model.MessageRole
import com.example.aichat.data.model.ProviderConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiCompatibleClientTest {
    @Test
    fun `stream request serializes OpenAI messages and emits deltas`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"！\"}}]}\n\n" +
                        "data: [DONE]\n\n",
                ),
        )
        server.start()
        try {
            val client = OpenAiCompatibleClient(
                imageFileStore = TestImageStore,
                httpClient = OkHttpClient.Builder().build(),
                allowInsecureHttp = true,
            )
            val events = client.streamChat(
                config = ProviderConfig(
                    baseUrl = server.url("/v1").toString().removeSuffix("/"),
                    model = "test-model",
                    apiKey = "test-key",
                ),
                messages = listOf(
                    ChatRequestMessage(MessageRole.USER, "hello"),
                ),
            ).toList()

            assertEquals(
                listOf(ChatStreamEvent.Delta("你好"), ChatStreamEvent.Delta("！"), ChatStreamEvent.Done),
                events,
            )
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
            assertEquals("Bearer test-key", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"model\":\"test-model\""))
            assertTrue(body.contains("\"stream\":true"))
            assertTrue(body.contains("\"role\":\"user\""))
            assertTrue(body.contains("\"content\":\"hello\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `provider status errors are mapped to a readable client exception`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"密钥无效\"}}"),
        )
        server.start()
        try {
            val client = OpenAiCompatibleClient(
                imageFileStore = TestImageStore,
                allowInsecureHttp = true,
            )
            try {
                client.streamChat(
                    ProviderConfig(
                        baseUrl = server.url("/v1").toString().removeSuffix("/"),
                        model = "test-model",
                        apiKey = "test-key",
                    ),
                    listOf(ChatRequestMessage(MessageRole.USER, "hello")),
                ).toList()
                throw AssertionError("expected ChatClientException")
            } catch (failure: ChatClientException) {
                assertEquals(ChatErrorKind.UNAUTHORIZED, failure.kind)
                assertEquals("密钥无效", failure.message)
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `cancelling a blocked stream closes the HTTP call promptly`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )
        server.start()
        try {
            val client = OpenAiCompatibleClient(
                imageFileStore = TestImageStore,
                httpClient = OkHttpClient.Builder().build(),
                allowInsecureHttp = true,
            )
            val stream = async(Dispatchers.IO) {
                client.streamChat(
                    ProviderConfig(
                        baseUrl = server.url("/v1").toString().removeSuffix("/"),
                        model = "test-model",
                        apiKey = "test-key",
                    ),
                    listOf(ChatRequestMessage(MessageRole.USER, "hello")),
                ).toList()
            }

            server.takeRequest(2, TimeUnit.SECONDS)
                ?: throw AssertionError("request was not received")
            withTimeout(2_000) {
                stream.cancelAndJoin()
            }
        } finally {
            server.shutdown()
        }
    }

    private object TestImageStore : ImageStore {
        override fun mimeType(path: String): String = "image/jpeg"
    }
}
