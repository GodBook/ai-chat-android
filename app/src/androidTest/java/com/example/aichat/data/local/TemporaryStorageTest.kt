package com.example.aichat.data.local

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.aichat.data.model.ProviderConfig
import com.example.aichat.data.network.*
import com.example.aichat.ui.openSearchSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TemporaryStorageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun temporaryImageDecodesWithoutCreatingAnyAttachmentFile() = runBlocking {
        val store = ImageFileStore(context)
        val input = File(context.cacheDir, "temporary-test-input.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val before = store.getImageDirectory().listFiles()!!.map { it.name }.toSet()
            val result = store.importInMemory(Uri.fromFile(input))
            assertTrue(result.startsWith("data:image/jpeg;base64,"))
            assertEquals(before, store.getImageDirectory().listFiles()!!.map { it.name }.toSet())
        } finally { bitmap.recycle(); input.delete() }
    }

    @Test fun sourceClickLaunchesTheActualWebUrl() {
        var launched: Intent? = null
        val capture = object : ContextWrapper(context) { override fun startActivity(intent: Intent) { launched = intent } }
        openSearchSource(capture, "https://example.org/news?id=169")
        assertEquals(Intent.ACTION_VIEW, launched?.action)
        assertEquals("https://example.org/news?id=169", launched?.dataString)
        assertTrue(launched!!.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test fun searchKeyIsSeparateAndIsClearedWhenItsEndpointChanges() {
        val scoped = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("test169_$name", mode)
        }
        val store = WebSearchSettingsStore(scoped)
        try {
            val chat = ProviderConfig(baseUrl = "https://chat.example/v1", apiKey = "chat-key")
            store.save("https://search.example/anthropic/v1/messages", "native", "search-key")
            assertEquals("search-key", store.resolve(chat).apiKey)
            store.save("https://different.example/anthropic/v1", "native", null)
            assertFalse(store.hasKey()); assertNull(store.resolve(chat).apiKey)
            store.save("https://chat.example/anthropic/v1", "native", null)
            assertEquals("chat-key", store.resolve(chat).apiKey)
            assertThrows(IllegalArgumentException::class.java) { store.save("http://unsafe.example/v1", "native", "key") }
        } finally {
            store.save(DEFAULT_SEARCH_ENDPOINT, DEFAULT_SEARCH_MODEL, null, clearKey = true)
            scoped.getSharedPreferences("web_search_config", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
