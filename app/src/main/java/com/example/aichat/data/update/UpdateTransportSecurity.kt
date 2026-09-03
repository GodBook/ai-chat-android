package com.example.aichat.data.update

import okhttp3.Interceptor
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

/** Enforces HTTPS for the initial update request and every redirect hop. */
internal fun OkHttpClient.securedForUpdates(allowInsecureHttpForTests: Boolean): OkHttpClient {
    if (allowInsecureHttpForTests) return this

    return newBuilder()
        // Do not let OkHttp follow an HTTPS -> HTTP redirect before it can be rejected.
        .followSslRedirects(false)
        .addNetworkInterceptor(HttpsOnlyUpdateInterceptor)
        .build()
}

internal fun ensureSecureUpdateResponse(
    response: Response,
    allowInsecureHttpForTests: Boolean,
) {
    if (allowInsecureHttpForTests) return

    generateSequence(response) { it.priorResponse }.forEach { redirectResponse ->
        requireSecureUpdateUrl(redirectResponse.request.url)
    }

    // With followSslRedirects(false), a downgrade remains as a 3xx response.
    // Validate its destination so the caller gets the correct security error.
    if (response.code in 300..399) {
        response.header("Location")
            ?.let(response.request.url::resolve)
            ?.let(::requireSecureUpdateUrl)
    }
}

private object HttpsOnlyUpdateInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        requireSecureUpdateUrl(chain.request().url)
        return chain.proceed(chain.request())
    }
}

private fun requireSecureUpdateUrl(url: HttpUrl) {
    if (!url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) {
        throw AppUpdateException(UpdateErrorKind.INVALID_URL, "更新连接或重定向地址不安全")
    }
}
