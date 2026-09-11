package com.example.aichat.data.network

import com.example.aichat.data.local.ImageStore
import com.example.aichat.data.model.ChatRequestMessage
import com.example.aichat.data.model.DEFAULT_MODEL
import com.example.aichat.data.model.FALLBACK_MODEL
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
    fun `temporary data URI images are transmitted without accessing image files`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("data: [DONE]\n\n"))
            val client = OpenAiCompatibleClient(
                imageFileStore = object : ImageStore { override fun mimeType(path: String): String = error("Temporary image must not access files") },
                allowInsecureHttp = true,
            )
            val data = "data:image/png;base64,AQID"
            client.streamChat(
                ProviderConfig(baseUrl = server.url("/v1").toString(), model = "vision", apiKey = "test", visionEnabled = true),
                listOf(ChatRequestMessage(MessageRole.USER, "private image", listOf(data))),
            ).toList()
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"url\":\"$data\""))
            assertTrue(body.contains("private image"))
        }
    }

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
    fun `an unavailable model is retried with the fallback model`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("{\"error\":{\"message\":\"model deepseek-v4.1-flash-expires-on-0910 not found\"}}"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"已切换\"}}]}\n\ndata: [DONE]\n\n"),
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
                    model = DEFAULT_MODEL,
                    apiKey = "test-key",
                ),
                messages = listOf(ChatRequestMessage(MessageRole.USER, "hello")),
            ).toList()

            assertEquals(
                listOf(ChatStreamEvent.Delta("已切换"), ChatStreamEvent.Done),
                events,
            )
            assertTrue(
                server.takeRequest().body.readUtf8().contains("\"model\":\"$DEFAULT_MODEL\""),
            )
            assertTrue(
                server.takeRequest().body.readUtf8().contains("\"model\":\"$FALLBACK_MODEL\""),
            )
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `auth failures are not retried with the fallback model`() = runBlocking {
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
                httpClient = OkHttpClient.Builder().build(),
                allowInsecureHttp = true,
            )
            try {
                client.streamChat(
                    ProviderConfig(
                        baseUrl = server.url("/v1").toString().removeSuffix("/"),
                        model = DEFAULT_MODEL,
                        apiKey = "test-key",
                    ),
                    listOf(ChatRequestMessage(MessageRole.USER, "hello")),
                ).toList()
                throw AssertionError("expected ChatClientException")
            } catch (failure: ChatClientException) {
                assertEquals(ChatErrorKind.UNAUTHORIZED, failure.kind)
            }
            server.takeRequest()
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a generic request error does not switch to the fallback model`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\":{\"message\":\"请求参数无效\"}}"),
        )
        server.start()
        try {
            val client = OpenAiCompatibleClient(
                imageFileStore = TestImageStore,
                httpClient = OkHttpClient.Builder().build(),
                allowInsecureHttp = true,
            )
            try {
                client.streamChat(
                    ProviderConfig(
                        baseUrl = server.url("/v1").toString().removeSuffix("/"),
                        model = DEFAULT_MODEL,
                        apiKey = "test-key",
                    ),
                    listOf(ChatRequestMessage(MessageRole.USER, "hello")),
                ).toList()
                throw AssertionError("expected ChatClientException")
            } catch (failure: ChatClientException) {
                assertEquals("请求参数无效", failure.message)
            }
            server.takeRequest()
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `disabling auto fallback keeps the configured model even when it is gone`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("{\"error\":{\"message\":\"model deepseek-v4.1-flash-expires-on-0910 not found\"}}"),
        )
        server.start()
        try {
            val client = OpenAiCompatibleClient(
                imageFileStore = TestImageStore,
                httpClient = OkHttpClient.Builder().build(),
                allowInsecureHttp = true,
            )
            try {
                client.streamChat(
                    ProviderConfig(
                        baseUrl = server.url("/v1").toString().removeSuffix("/"),
                        model = DEFAULT_MODEL,
                        apiKey = "test-key",
                        autoFallbackEnabled = false,
                    ),
                    listOf(ChatRequestMessage(MessageRole.USER, "hello")),
                ).toList()
                throw AssertionError("expected ChatClientException")
            } catch (failure: ChatClientException) {
                assertEquals(404, failure.statusCode)
            }
            assertTrue(
                server.takeRequest().body.readUtf8().contains("\"model\":\"$DEFAULT_MODEL\""),
            )
            assertEquals(1, server.requestCount)
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

    @Test
    fun `probeConnection returns success when server responds 200`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"pong\"}}]}"),
        )
        server.start()
        try {
            val client = OpenAiCompatibleClient(
                imageFileStore = TestImageStore,
                httpClient = OkHttpClient.Builder().build(),
                allowInsecureHttp = true,
            )
            val result = client.probeConnection(
                ProviderConfig(
                    baseUrl = server.url("/v1").toString().removeSuffix("/"),
                    model = "test-model",
                    apiKey = "valid-key",
                ),
            )
            assertTrue(result.isSuccess)
            assertEquals(200, result.statusCode)
            assertTrue(result.message.contains("连接成功"))
            assertTrue(result.latencyMs >= 0)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `probeConnection returns failure when server returns 401`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"Invalid API key provided\"}}"),
        )
        server.start()
        try {
            val client = OpenAiCompatibleClient(
                imageFileStore = TestImageStore,
                httpClient = OkHttpClient.Builder().build(),
                allowInsecureHttp = true,
            )
            val result = client.probeConnection(
                ProviderConfig(
                    baseUrl = server.url("/v1").toString().removeSuffix("/"),
                    model = "test-model",
                    apiKey = "bad-key",
                ),
            )
            org.junit.Assert.assertFalse(result.isSuccess)
            assertEquals(401, result.statusCode)
            assertTrue(result.message.contains("Invalid API key provided"))
        } finally {
            server.shutdown()
        }
    }

    private object TestImageStore : ImageStore {
        override fun mimeType(path: String): String = "image/jpeg"
    }
}
