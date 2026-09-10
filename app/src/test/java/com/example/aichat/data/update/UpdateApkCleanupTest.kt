package com.example.aichat.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateApkCleanupTest {
    @Test
    fun `version codes are parsed only from this manager's file names`() {
        assertEquals(19L, parseUpdateApkVersionCode("ai-chat-19.apk"))
        assertEquals(19L, parseUpdateApkVersionCode("ai-chat-19.apk.bak"))
        assertNull(parseUpdateApkVersionCode("ai-chat-update-123.download.apk"))
        assertNull(parseUpdateApkVersionCode("other.apk"))
        assertNull(parseUpdateApkVersionCode("ai-chat-19.apk.txt"))
    }

    @Test
    fun `installed and stale files are deleted while a newer download is kept`() {
        val names = listOf(
            "ai-chat-18.apk",
            "ai-chat-19.apk",
            "ai-chat-19.apk.bak",
            "ai-chat-20.apk",
            "ai-chat-update-123.download.apk",
            "other.apk",
        )
        assertEquals(
            listOf(
                "ai-chat-18.apk",
                "ai-chat-19.apk",
                "ai-chat-19.apk.bak",
                "ai-chat-update-123.download.apk",
            ),
            installedUpdateFileNames(names, currentVersionCode = 19L),
        )
    }
}
