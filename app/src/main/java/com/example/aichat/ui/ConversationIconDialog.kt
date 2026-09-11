package com.example.aichat.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val IMAGE_PREFIX = "data:image/jpeg;base64,"

@Composable
internal fun ConversationAvatar(icon: String?, size: Dp) {
    val bitmap = remember(icon) {
        if (icon?.startsWith(IMAGE_PREFIX) == true) runCatching {
            val bytes = Base64.decode(icon.removePrefix(IMAGE_PREFIX), Base64.DEFAULT)
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                decoder.setTargetSize(192, 192)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap()
        }.getOrNull() else null
    }
    when {
        bitmap != null -> Image(bitmap, "聊天图标", Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
        !icon.isNullOrBlank() && !icon.startsWith("data:") -> Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(icon, fontSize = (size.value * 0.48f).sp, maxLines = 1) }
        else -> AiAvatar(size)
    }
}

@Composable
internal fun ConversationIconDialog(currentIcon: String?, onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var selected by remember { mutableStateOf(currentIcon) }
    var custom by remember { mutableStateOf(currentIcon?.takeUnless { it.startsWith("data:") }.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            loading = true
            error = null
            try {
                selected = withContext(Dispatchers.IO) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val scale = 384.0 / maxOf(info.size.width, info.size.height).coerceAtLeast(384)
                        decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    try {
                        val side = minOf(bitmap.width, bitmap.height)
                        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
                        val small = Bitmap.createScaledBitmap(cropped, 192, 192, true)
                        val bytes = ByteArrayOutputStream().use { out ->
                            small.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            out.toByteArray()
                        }
                        if (small !== cropped && small !== bitmap) small.recycle()
                        if (cropped !== bitmap) cropped.recycle()
                        IMAGE_PREFIX + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    } finally { bitmap.recycle() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "无法读取图片，请换一张图片重试"
            } finally { loading = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("自定义聊天图标") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ConversationAvatar(selected, 72.dp) }
                Text("选择表情、输入文字，或使用相册图片。", style = MaterialTheme.typography.bodyMedium)
                listOf("🤖", "💬", "💻", "📚", "🎨", "🌍", "🧠", "✨", "🐱", "🎮", "📝", "💼").chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { emoji ->
                            Surface(
                                shape = CircleShape,
                                color = if (selected == emoji) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(48.dp).clickable(enabled = !loading) { selected = emoji; custom = emoji },
                            ) { Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 26.sp) } }
                        }
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { if (it.codePointCount(0, it.length) <= 12) { custom = it; selected = it.trim().ifEmpty { null } } },
                    label = { Text("表情或简短文字") }, singleLine = true, enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "正在处理图片…" else "从相册选择图片")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { selected = null; custom = "" }, enabled = !loading) { Text("恢复默认图标") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }, enabled = !loading) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") } },
    )
}
