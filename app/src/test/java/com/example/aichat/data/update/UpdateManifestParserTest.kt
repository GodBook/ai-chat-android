package com.example.aichat.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestParserTest {
    private val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun `parses and normalizes a valid manifest`() {
        val info = UpdateManifestParser.parse(
            """
            {
              "versionCode": "2",
              "versionName": " 1.1 ",
              "downloadUrl": "https://updates.example.test/app.apk",
              "sha256": "${hash.uppercase()}",
              "releaseNotes": "  修复问题  "
            }
            """.trimIndent(),
        )

        assertEquals(2L, info.versionCode)
        assertEquals("1.1", info.versionName)
        assertEquals(hash, info.sha256)
        assertEquals("修复问题", info.releaseNotes)
    }

    @Test
    fun `rejects non HTTPS download URL`() {
        val failure = assertThrows(AppUpdateException::class.java) {
            UpdateManifestParser.parse(
                """
                {"versionCode":2,"versionName":"1.1","downloadUrl":"http://updates.example.test/app.apk","sha256":"$hash"}
                """.trimIndent(),
            )
        }
        assertEquals(UpdateErrorKind.INVALID_URL, failure.kind)
    }

    @Test
    fun `rejects malformed hash`() {
        val failure = assertThrows(AppUpdateException::class.java) {
            UpdateManifestParser.parse(
                """
                {"versionCode":2,"versionName":"1.1","downloadUrl":"https://updates.example.test/app.apk","sha256":"xyz"}
                """.trimIndent(),
            )
        }
        assertEquals(UpdateErrorKind.INVALID_MANIFEST, failure.kind)
        assertTrue(failure.message.contains("sha256"))
    }

    @Test
    fun `rejects credentials embedded in update URL`() {
        val failure = assertThrows(AppUpdateException::class.java) {
            UpdateManifestParser.validateHttpsUrl("https://user:password@updates.example.test/app.apk")
        }

        assertEquals(UpdateErrorKind.INVALID_URL, failure.kind)
    }

    @Test
    fun `rejects a relative update URL`() {
        val failure = assertThrows(AppUpdateException::class.java) {
            UpdateManifestParser.validateHttpsUrl("/releases/app.apk")
        }

        assertEquals(UpdateErrorKind.INVALID_URL, failure.kind)
    }
}
