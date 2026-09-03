package com.example.aichat.data.update

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpdateTransportSecurityTest {
    @Test
    fun `accepts an HTTPS redirect chain`() {
        val first = response("https://updates.example.test/manifest.json", 302)
        val final = response(
            url = "https://cdn.example.test/manifest.json",
            code = 200,
            priorResponse = first,
        )

        ensureSecureUpdateResponse(final, allowInsecureHttpForTests = false)
    }

    @Test
    fun `rejects a followed redirect containing an HTTP hop`() {
        val insecureHop = response("http://updates.example.test/redirect", 302)
        val final = response(
            url = "https://cdn.example.test/app.apk",
            code = 200,
            priorResponse = insecureHop,
        )

        val failure = assertThrows(AppUpdateException::class.java) {
            ensureSecureUpdateResponse(final, allowInsecureHttpForTests = false)
        }

        assertEquals(UpdateErrorKind.INVALID_URL, failure.kind)
    }

    @Test
    fun `rejects an HTTPS response redirecting to HTTP`() {
        val redirect = response(
            url = "https://updates.example.test/app.apk",
            code = 302,
            location = "http://cdn.example.test/app.apk",
        )

        val failure = assertThrows(AppUpdateException::class.java) {
            ensureSecureUpdateResponse(redirect, allowInsecureHttpForTests = false)
        }

        assertEquals(UpdateErrorKind.INVALID_URL, failure.kind)
    }

    @Test
    fun `rejects redirect credentials`() {
        val redirect = response(
            url = "https://updates.example.test/app.apk",
            code = 302,
            location = "https://user:password@cdn.example.test/app.apk",
        )

        val failure = assertThrows(AppUpdateException::class.java) {
            ensureSecureUpdateResponse(redirect, allowInsecureHttpForTests = false)
        }

        assertEquals(UpdateErrorKind.INVALID_URL, failure.kind)
    }

    private fun response(
        url: String,
        code: Int,
        location: String? = null,
        priorResponse: Response? = null,
    ): Response = Response.Builder()
        .request(Request.Builder().url(url).build())
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("test")
        .apply {
            if (location != null) header("Location", location)
            if (priorResponse != null) this.priorResponse(priorResponse)
        }
        .build()
}
