package com.example.aichat.background

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.aichat.R

/** Displays a compact QQ-like answer bubble at the top of the screen. */
class ScreenshotOverlayManager(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var currentView: View? = null

    fun show(answer: String): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false
        val cleanAnswer = answer.trim().ifEmpty { "AI 没有返回可显示的内容" }.take(MAX_ANSWER_LENGTH)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return addBubble(cleanAnswer)
        }
        mainHandler.post { addBubble(cleanAnswer) }
        return true
    }

    fun dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeCurrent()
        } else {
            mainHandler.post(::removeCurrent)
        }
    }

    private fun removeCurrent() {
        currentView?.let { view -> runCatching { windowManager.removeView(view) } }
        currentView = null
    }

    private fun addBubble(cleanAnswer: String): Boolean {
        removeCurrent()
        val bubble = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(112)
            setPadding(dp(14), dp(14), dp(14), dp(7))
            background = roundedBackground(
                color = Color.argb(248, 204, 241, 251),
                radius = 28,
                strokeColor = Color.argb(210, 238, 252, 255),
            )
            elevation = dp(12).toFloat()
            isClickable = false
            isFocusable = false
        }

        val contentRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val iconFrame = FrameLayout(appContext).apply {
            background = roundedBackground(Color.WHITE, 14)
            clipToOutline = true
            elevation = dp(2).toFloat()
        }
        val icon = ImageView(appContext).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(4), dp(4), dp(4), dp(4))
            contentDescription = null
        }
        iconFrame.addView(
            icon,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER),
        )
        contentRow.addView(iconFrame, LinearLayout.LayoutParams(dp(54), dp(54)))

        val textColumn = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(appContext).apply {
            text = "AI 截屏回答"
            setTextColor(Color.rgb(15, 42, 55))
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val body = TextView(appContext).apply {
            text = cleanAnswer
            setTextColor(Color.rgb(25, 57, 70))
            textSize = 15f
            includeFontPadding = false
            maxLines = 7
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(8), 0, 0)
        }
        textColumn.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        textColumn.addView(
            body,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        contentRow.addView(
            textColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(12)
                rightMargin = dp(10)
            },
        )

        val metaColumn = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val time = TextView(appContext).apply {
            text = "现在"
            setTextColor(Color.rgb(83, 126, 144))
            textSize = 12f
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        val avatar = TextView(appContext).apply {
            text = "AI"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(15, 118, 110))
            }
        }
        metaColumn.addView(
            time,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        metaColumn.addView(
            avatar,
            LinearLayout.LayoutParams(dp(38), dp(38)).apply { topMargin = dp(10) },
        )
        contentRow.addView(
            metaColumn,
            LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT),
        )
        bubble.addView(
            contentRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val handleContainer = FrameLayout(appContext)
        val handle = View(appContext).apply {
            background = roundedBackground(Color.rgb(99, 158, 181), 3)
        }
        handleContainer.addView(
            handle,
            FrameLayout.LayoutParams(dp(54), dp(4), Gravity.CENTER),
        )
        bubble.addView(
            handleContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(16),
            ).apply { topMargin = dp(7) },
        )

        val root = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                bubble,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = dp(10)
                    rightMargin = dp(10)
                },
            )
            alpha = 0f
            translationY = -dp(12).toFloat()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(8)
        }
        return runCatching {
            windowManager.addView(root, params)
            currentView = root
            root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(ENTER_ANIMATION_MS)
                .start()
            mainHandler.postDelayed({
                if (currentView === root) removeCurrent()
            }, DISPLAY_DURATION_MS)
            true
        }.getOrElse { false }
    }

    private fun roundedBackground(
        color: Int,
        radius: Int,
        strokeColor: Int? = null,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        strokeColor?.let { setStroke(dp(1), it) }
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private companion object {
        const val DISPLAY_DURATION_MS = 20_000L
        const val ENTER_ANIMATION_MS = 180L
        const val MAX_ANSWER_LENGTH = 4_000
    }
}
