package com.example.aichat.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.aichat.data.model.ChatMessage
import com.example.aichat.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

object ChatImageExporter {

    private const val BITMAP_WIDTH = 1080
    private const val HORIZONTAL_PADDING = 54
    private const val BUBBLE_RADIUS = 32f
    private const val MAX_BUBBLE_WIDTH = 800

    suspend fun exportAndShareImage(
        context: Context,
        conversationTitle: String,
        messages: List<ChatMessage>,
        modelName: String,
        includeThinking: Boolean = true,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = renderChatBitmap(conversationTitle, messages, modelName, includeThinking)
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTitle = conversationTitle.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(30)
            val imageFile = File(exportsDir, "${safeTitle}_${System.currentTimeMillis()}.png")
            imageFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                imageFile,
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, conversationTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "分享对话长图")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            uri
        }
    }

    private fun renderChatBitmap(
        conversationTitle: String,
        messages: List<ChatMessage>,
        modelName: String,
        includeThinking: Boolean,
    ): Bitmap {
        // Paints
        val bgPaint = Paint().apply { color = Color.parseColor("#F4F6F9") }
        val cardBgPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        val dividerPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 2f }
        val userBubblePaint = Paint().apply { color = Color.parseColor("#1976D2"); isAntiAlias = true }
        val assistantBubblePaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        val assistantBubbleBorderPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        val thinkingBubblePaint = Paint().apply { color = Color.parseColor("#F1F5F9"); isAntiAlias = true }

        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 42f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subtitlePaint = TextPaint().apply {
            color = Color.parseColor("#64748B")
            textSize = 28f
            isAntiAlias = true
        }
        val brandPaint = TextPaint().apply {
            color = Color.parseColor("#1976D2")
            textSize = 30f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val userTextPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 34f
            isAntiAlias = true
        }
        val assistantTextPaint = TextPaint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 34f
            isAntiAlias = true
        }
        val thinkingTextPaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 30f
            isAntiAlias = true
        }
        val thinkingHeaderPaint = TextPaint().apply {
            color = Color.parseColor("#334155")
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val footerPaint = TextPaint().apply {
            color = Color.parseColor("#94A3B8")
            textSize = 26f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val contentWidth = BITMAP_WIDTH - HORIZONTAL_PADDING * 2
        val textPaddingH = 36
        val textPaddingV = 28

        // Pass 1: Measure height
        var totalHeight = 0
        totalHeight += 40 // Top margin

        // Header height
        val titleLayout = createStaticLayout(conversationTitle, titlePaint, contentWidth - 48)
        val headerHeight = 40 + 36 + 16 + titleLayout.height + 16 + 32 + 40
        totalHeight += headerHeight + 36

        // Message items measure
        data class RenderItem(
            val message: ChatMessage,
            val userLayout: StaticLayout?,
            val assistantLayout: StaticLayout?,
            val thinkingLayout: StaticLayout?,
            val thinkingHeader: String?,
            val imageBitmap: Bitmap?,
            val bubbleWidth: Int,
            val bubbleHeight: Int,
        )

        val renderItems = mutableListOf<RenderItem>()

        for (msg in messages) {
            when (msg.role) {
                MessageRole.USER -> {
                    var imgBmp: Bitmap? = null
                    if (msg.imagePaths.isNotEmpty()) {
                        val path = msg.imagePaths.firstOrNull()
                        if (path != null && File(path).exists()) {
                            imgBmp = decodeSampledBitmap(path, MAX_BUBBLE_WIDTH - textPaddingH * 2, 600)
                        }
                    }

                    val userLayout = if (msg.text.isNotBlank()) {
                        val desiredWidth = min(
                            userTextPaint.measureText(msg.text).toInt() + 10,
                            MAX_BUBBLE_WIDTH - textPaddingH * 2,
                        )
                        createStaticLayout(msg.text, userTextPaint, maxOf(100, desiredWidth))
                    } else null

                    val textWidth = userLayout?.let { getLayoutMaxWidth(it) } ?: 0
                    val imgWidth = imgBmp?.width ?: 0
                    val bWidth = maxOf(textWidth, imgWidth) + textPaddingH * 2
                    val bHeight = (userLayout?.height ?: 0) +
                        (imgBmp?.let { it.height + 16 } ?: 0) +
                        textPaddingV * 2

                    renderItems.add(
                        RenderItem(
                            message = msg,
                            userLayout = userLayout,
                            assistantLayout = null,
                            thinkingLayout = null,
                            thinkingHeader = null,
                            imageBitmap = imgBmp,
                            bubbleWidth = minOf(bWidth, MAX_BUBBLE_WIDTH),
                            bubbleHeight = bHeight,
                        ),
                    )
                    totalHeight += bHeight + 28
                }
                MessageRole.ASSISTANT -> {
                    var thinkLayout: StaticLayout? = null
                    var thinkHeader: String? = null
                    if (includeThinking && !msg.thinkingContent.isNullOrBlank()) {
                        val dur = (msg.thinkingDurationMs ?: 0L) / 1000L
                        thinkHeader = if (dur > 0) "🧠 思考过程（用时 ${dur}s）" else "🧠 思考过程"
                        thinkLayout = createStaticLayout(
                            msg.thinkingContent.trim(),
                            thinkingTextPaint,
                            MAX_BUBBLE_WIDTH - textPaddingH * 2,
                        )
                    }

                    val assistantLayout = if (msg.text.isNotBlank()) {
                        createStaticLayout(
                            msg.text.trim(),
                            assistantTextPaint,
                            MAX_BUBBLE_WIDTH - textPaddingH * 2,
                        )
                    } else null

                    val bWidth = MAX_BUBBLE_WIDTH
                    var bHeight = textPaddingV * 2
                    if (thinkLayout != null) {
                        bHeight += 36 + 12 + thinkLayout.height + 24
                    }
                    if (assistantLayout != null) {
                        bHeight += assistantLayout.height
                    }

                    renderItems.add(
                        RenderItem(
                            message = msg,
                            userLayout = null,
                            assistantLayout = assistantLayout,
                            thinkingLayout = thinkLayout,
                            thinkingHeader = thinkHeader,
                            imageBitmap = null,
                            bubbleWidth = bWidth,
                            bubbleHeight = bHeight,
                        ),
                    )
                    totalHeight += bHeight + 28
                }
            }
        }

        totalHeight += 120 // Footer

        // Limit maximum height to avoid OOM on huge chats
        val finalHeight = min(totalHeight, 20_000)
        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        canvas.drawRect(0f, 0f, BITMAP_WIDTH.toFloat(), finalHeight.toFloat(), bgPaint)

        var currentY = 40f

        // Draw Header Card
        val headerRect = RectF(
            HORIZONTAL_PADDING.toFloat(),
            currentY,
            (BITMAP_WIDTH - HORIZONTAL_PADDING).toFloat(),
            currentY + headerHeight,
        )
        canvas.drawRoundRect(headerRect, 24f, 24f, cardBgPaint)

        // Header content
        var headerY = currentY + 32f
        canvas.drawText("AI BOTOY", HORIZONTAL_PADDING + 28f, headerY + 28f, brandPaint)
        headerY += 46f

        canvas.save()
        canvas.translate(HORIZONTAL_PADDING + 28f, headerY)
        titleLayout.draw(canvas)
        canvas.restore()
        headerY += titleLayout.height + 16f

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        canvas.drawText("$dateStr  ·  模型: $modelName", HORIZONTAL_PADDING + 28f, headerY + 24f, subtitlePaint)

        currentY += headerHeight + 36f

        // Draw Messages
        for (item in renderItems) {
            if (currentY + item.bubbleHeight > finalHeight - 100) break

            if (item.message.role == MessageRole.USER) {
                val left = BITMAP_WIDTH - HORIZONTAL_PADDING - item.bubbleWidth.toFloat()
                val top = currentY
                val right = (BITMAP_WIDTH - HORIZONTAL_PADDING).toFloat()
                val bottom = top + item.bubbleHeight
                val rect = RectF(left, top, right, bottom)
                canvas.drawRoundRect(rect, BUBBLE_RADIUS, BUBBLE_RADIUS, userBubblePaint)

                var innerY = top + textPaddingV
                if (item.imageBitmap != null) {
                    canvas.drawBitmap(item.imageBitmap, left + textPaddingH, innerY, null)
                    innerY += item.imageBitmap.height + 16
                }
                if (item.userLayout != null) {
                    canvas.save()
                    canvas.translate(left + textPaddingH, innerY)
                    item.userLayout.draw(canvas)
                    canvas.restore()
                }
            } else {
                val left = HORIZONTAL_PADDING.toFloat()
                val top = currentY
                val right = left + item.bubbleWidth
                val bottom = top + item.bubbleHeight
                val rect = RectF(left, top, right, bottom)
                canvas.drawRoundRect(rect, BUBBLE_RADIUS, BUBBLE_RADIUS, assistantBubblePaint)
                canvas.drawRoundRect(rect, BUBBLE_RADIUS, BUBBLE_RADIUS, assistantBubbleBorderPaint)

                var innerY = top + textPaddingV
                if (item.thinkingLayout != null && item.thinkingHeader != null) {
                    val thinkBoxTop = innerY
                    val thinkBoxHeight = 36f + 12f + item.thinkingLayout.height + 20f
                    val thinkRect = RectF(
                        left + textPaddingH - 12f,
                        thinkBoxTop,
                        right - textPaddingH + 12f,
                        thinkBoxTop + thinkBoxHeight,
                    )
                    canvas.drawRoundRect(thinkRect, 16f, 16f, thinkingBubblePaint)

                    canvas.drawText(item.thinkingHeader, left + textPaddingH, thinkBoxTop + 28f, thinkingHeaderPaint)
                    canvas.save()
                    canvas.translate(left + textPaddingH, thinkBoxTop + 44f)
                    item.thinkingLayout.draw(canvas)
                    canvas.restore()

                    innerY += thinkBoxHeight + 20f
                }

                if (item.assistantLayout != null) {
                    canvas.save()
                    canvas.translate(left + textPaddingH, innerY)
                    item.assistantLayout.draw(canvas)
                    canvas.restore()
                }
            }
            currentY += item.bubbleHeight + 28f
        }

        // Footer
        val footerY = currentY + 40f
        if (footerY < finalHeight - 20) {
            canvas.drawLine(
                (BITMAP_WIDTH / 4).toFloat(),
                footerY,
                (BITMAP_WIDTH * 3 / 4).toFloat(),
                footerY,
                dividerPaint,
            )
            canvas.drawText(
                "— 由 AI BOTOY 原生客户端生成 —",
                (BITMAP_WIDTH / 2).toFloat(),
                footerY + 40f,
                footerPaint,
            )
        }

        // Recycle extracted bitmaps
        for (item in renderItems) {
            item.imageBitmap?.recycle()
        }

        return bitmap
    }

    private fun createStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        val safeWidth = maxOf(1, width)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1.15f)
            .setIncludePad(false)
            .build()
    }

    private fun getLayoutMaxWidth(layout: StaticLayout): Int {
        var maxW = 0f
        for (i in 0 until layout.lineCount) {
            val w = layout.getLineWidth(i)
            if (w > maxW) maxW = w
        }
        return maxW.toInt()
    }

    private fun decodeSampledBitmap(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)

        val height = options.outHeight
        val width = options.outWidth
        if (height <= 0 || width <= 0) return null

        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        return BitmapFactory.decodeFile(filePath, decodeOptions)
    }
}
