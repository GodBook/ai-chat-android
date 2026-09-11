package com.example.aichat.data.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import kotlin.coroutines.resumeWithException

internal suspend fun OkHttpClient.searchResponse(request: Request): Response = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                // Keep cancellation connected to the call until the body has arrived too.
                val buffered = response.use {
                    val body = it.body
                    val source = body?.source()
                    val limit = 4L * 1024 * 1024
                    if (source != null && source.request(limit + 1)) throw IOException("检索响应过大")
                    val bytes = source?.readByteArray() ?: byteArrayOf()
                    it.newBuilder().body(bytes.toResponseBody(body?.contentType())).build()
                }
                continuation.resume(buffered, onCancellation = { _, value, _ -> value.close() })
            } catch (error: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(error)
            }
        }
    })
}
