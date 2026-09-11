package com.example.aichat.data.repository

import com.example.aichat.data.model.*
import com.example.aichat.data.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class TemporaryChatSessionTest {
    private val config = ProviderConfig(model = "test", apiKey = "test", visionEnabled = true)
    private suspend fun TemporaryChatSession.idle() = withTimeout(4000) { state.first { !it.working } }

    @Test fun `each question sends only itself and closing clears all in-memory data`() = runBlocking {
        val requests = CopyOnWriteArrayList<List<ChatRequestMessage>>()
        val session = TemporaryChatSession(this, { _, messages -> requests.add(messages); flowOf(ChatStreamEvent.Delta("answer")) }, { _, _ -> error("Search disabled") })
        session.start()
        session.addImage(session.state.value.id!!, "data:image/png;base64,AQID")
        session.send("private first", config); session.idle()
        session.send("second", config); session.idle()
        assertEquals(4, session.state.value.messages.size)
        assertEquals(1, requests[0].size); assertEquals(1, requests[1].size)
        assertEquals("second", requests[1].single().text)
        assertTrue(requests[1].single().imagePaths.isEmpty())
        assertEquals(listOf("data:image/png;base64,AQID"), requests[0].single().imagePaths)
        session.close()
        assertEquals(TemporaryChatState(), session.state.value)
    }

    @Test fun `temporary search receives only current question and keeps sources in session`() = runBlocking {
        val queries = CopyOnWriteArrayList<String>()
        val requests = CopyOnWriteArrayList<List<ChatRequestMessage>>()
        val source = WebSearchResult("title", "https://example.org/", "excerpt")
        val session = TemporaryChatSession(this, { _, messages -> requests.add(messages); flowOf(ChatStreamEvent.Done) }, { query, _ -> queries.add(query); listOf(source) })
        session.start(); assertFalse(session.state.value.searchEnabled)
        session.toggleSearch()
        session.send("private first", config); session.idle()
        session.send("second", config); session.idle()
        assertEquals(listOf("private first", "second"), queries)
        assertFalse(requests.last().single().text.contains("private first"))
        assertEquals(listOf(source), session.state.value.messages.last().webSearchResults)
        session.close(); session.start()
        assertFalse(session.state.value.searchEnabled)
        assertTrue(session.state.value.messages.isEmpty())
    }

    @Test fun `closing and reopening during stream rejects late updates and images`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val session = TemporaryChatSession(this, { _, _ -> flow {
            emit(ChatStreamEvent.Delta("sensitive")); started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        } }, { _, _ -> emptyList() })
        session.start(); val oldId = session.state.value.id!!
        session.send("private", config); withTimeout(3000) { started.await() }
        session.close(); session.start()
        session.addImage(oldId, "data:image/png;base64,AQID")
        withTimeout(3000) { cancelled.await() }
        // Join the cancelled request, including its final state update.
        coroutineContext.job.children.toList().joinAll()
        assertNotEquals(oldId, session.state.value.id)
        assertTrue(session.state.value.messages.isEmpty()); assertTrue(session.state.value.images.isEmpty())
        assertFalse(session.state.value.working)
    }

    @Test fun `duplicate send is ignored while busy and stop permits a new independent question`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val session = TemporaryChatSession(this, { _, _ -> flow { entered.complete(Unit); awaitCancellation() } }, { _, _ -> emptyList() })
        session.start(); session.send("one", config); session.send("duplicate", config)
        withTimeout(3000) { entered.await() }
        assertEquals(2, session.state.value.messages.size)
        session.stop(); session.idle()
        assertEquals(MessageStatus.INTERRUPTED, session.state.value.messages.last().status)
        session.clear(); assertTrue(session.state.value.messages.isEmpty())
    }

    @Test fun `search failure does not silently call chat and retry uses only failed question`() = runBlocking {
        var failSearch = true
        val requests = CopyOnWriteArrayList<List<ChatRequestMessage>>()
        val session = TemporaryChatSession(this, { _, messages -> requests.add(messages); flowOf(ChatStreamEvent.Done) }, { _, _ ->
            if (failSearch) error("Search unavailable") else emptyList()
        })
        session.start(); session.toggleSearch(); session.send("question", config); session.idle()
        assertTrue(requests.isEmpty())
        assertEquals(MessageStatus.FAILED, session.state.value.messages.last().status)
        failSearch = false
        session.retry(session.state.value.messages.last().id, config); session.idle()
        assertEquals(1, requests.single().size)
        assertTrue(requests.single().single().text.startsWith("question"))
    }

    @Test fun `immediate stop cannot leave session busy or overwrite next request`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val session = TemporaryChatSession(this, { _, messages -> flow {
            if (messages.single().text == "new") entered.complete(Unit)
            awaitCancellation()
        } }, { _, _ -> emptyList() })
        session.start(); session.send("old", config); session.stop()
        assertFalse(session.state.value.working)
        session.send("new", config)
        withTimeout(3000) { entered.await() }
        assertTrue(session.state.value.working)
        assertEquals(MessageStatus.INTERRUPTED, session.state.value.messages[1].status)
        session.close()
    }
}
