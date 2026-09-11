package com.example.aichat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal fun chatImageModel(path: String): Any {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(path) {
        if (path.startsWith("data:image/")) coil3.request.ImageRequest.Builder(context)
            .data(android.util.Base64.decode(path.substringAfter(','), android.util.Base64.NO_WRAP))
            .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
            .diskCachePolicy(coil3.request.CachePolicy.DISABLED).build()
        else java.io.File(path)
    }
}
