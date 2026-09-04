package com.example.aichat.background

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

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
            setPadding(dp(16), dp(10), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.argb(242, 26, 28, 34))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(80, 255, 255, 255))
            }
            elevation = dp(8).toFloat()
            isClickable = false
            isFocusable = false
        }
        val title = TextView(appContext).apply {
            text = "AI 截屏回答"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val body = TextView(appContext).apply {
            text = cleanAnswer
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 8
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }
        bubble.addView(title)
        bubble.addView(body)
        val root = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(
                bubble,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    leftMargin = dp(12)
                    rightMargin = dp(12)
                },
            )
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
            y = dp(10)
            horizontalMargin = 0.04f
        }
        return runCatching {
            windowManager.addView(root, params)
            currentView = root
            mainHandler.postDelayed({
                if (currentView === root) removeCurrent()
            }, DISPLAY_DURATION_MS)
            true
        }.getOrElse { false }
    }

    private fun dp(value: Int): Int =
        (value * appContext.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private companion object {
        const val DISPLAY_DURATION_MS = 12_000L
        const val MAX_ANSWER_LENGTH = 4_000
    }
}
