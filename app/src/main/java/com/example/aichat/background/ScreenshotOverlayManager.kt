package com.example.aichat.background

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.aichat.R
import com.example.aichat.data.model.DEFAULT_OVERLAY_BACKGROUND_COLOR
import com.example.aichat.data.model.normalizeOverlayBackgroundColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/** Displays a fixed-size, scrollable QQ-like answer bubble at the top of the screen. */
class ScreenshotOverlayManager(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = android.os.Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var autoDismissRunnable: Runnable? = null

    fun show(
        answer: String,
        backgroundColor: String = DEFAULT_OVERLAY_BACKGROUND_COLOR,
        glassEnabled: Boolean = false,
    ): Boolean {
        if (!Settings.canDrawOverlays(appContext)) return false
        val cleanAnswer = answer.trim().ifEmpty { "AI 没有返回可显示的内容" }
        val normalizedColor = normalizeOverlayBackgroundColor(backgroundColor)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return addBubble(cleanAnswer, normalizedColor, glassEnabled)
        }
        mainHandler.post { addBubble(cleanAnswer, normalizedColor, glassEnabled) }
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
        autoDismissRunnable?.let(mainHandler::removeCallbacks)
        autoDismissRunnable = null
        currentView?.let { view -> runCatching { windowManager.removeView(view) } }
        currentView = null
    }

    private fun addBubble(
        cleanAnswer: String,
        backgroundColor: String,
        glassEnabled: Boolean,
    ): Boolean {
        removeCurrent()
        val palette = createPalette(backgroundColor, glassEnabled)
        val bubble = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(112)
            setPadding(dp(14), dp(14), dp(14), dp(7))
            background = roundedBackground(
                color = palette.background,
                radius = 28,
                strokeColor = palette.border,
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
            setTextColor(palette.title)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val body = TextView(appContext).apply {
            text = cleanAnswer
            setTextColor(palette.body)
            textSize = 15f
            includeFontPadding = false
            maxLines = Int.MAX_VALUE
            ellipsize = null
            setLineSpacing(dp(2).toFloat(), 1f)
            setPadding(0, dp(8), dp(4), dp(4))
        }
        val bodyScroller = BoundedScrollView(
            context = appContext,
            maxHeight = dp(BODY_VIEWPORT_DP),
        ).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(10))
            clipToPadding = false
            addView(
                body,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        textColumn.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        // The viewport keeps the bubble's height stable while the complete answer remains
        // available through a normal vertical swipe.
        textColumn.addView(
            bodyScroller,
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
            setTextColor(palette.meta)
            textSize = 12f
            includeFontPadding = false
            gravity = Gravity.CENTER
        }
        val avatar = TextView(appContext).apply {
            text = "AI"
            setTextColor(palette.avatarText)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.avatar)
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
            background = roundedBackground(palette.handle, 3)
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

        val root = SwipeDismissLayout(appContext).apply {
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
            isClickable = true
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(8)
            if (glassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                setBlurBehindRadius(dp(GLASS_BLUR_RADIUS_DP))
            }
        }
        val added = runCatching {
            windowManager.addView(root, params)
            true
        }.getOrElse {
            if (!glassEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                false
            } else {
                // Some vendor window managers reject blur for application overlays. Keep the
                // translucent glass surface usable by retrying without the optional blur flag.
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                params.setBlurBehindRadius(0)
                runCatching {
                    windowManager.addView(root, params)
                    true
                }.getOrDefault(false)
            }
        }
        if (!added) return false
        currentView = root
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(ENTER_ANIMATION_MS)
            .start()
        scheduleAutoDismiss(root)
        return true
    }

    private fun scheduleAutoDismiss(view: View) {
        autoDismissRunnable?.let(mainHandler::removeCallbacks)
        autoDismissRunnable = Runnable {
            if (currentView === view) removeCurrent()
        }.also { mainHandler.postDelayed(it, DISPLAY_DURATION_MS) }
    }

    private fun pauseAutoDismiss() {
        autoDismissRunnable?.let(mainHandler::removeCallbacks)
        autoDismissRunnable = null
    }

    /** Intercepts only horizontal movement so the nested answer ScrollView owns vertical swipes. */
    private inner class SwipeDismissLayout(context: Context) : FrameLayout(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var dragging = false
        private var dismissing = false

        override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            // ScrollView normally asks its parent to stop intercepting after ACTION_DOWN. Keep
            // observing moves so a horizontal gesture can still become the existing dismiss action;
            // vertical moves continue to return false from onInterceptTouchEvent and scroll inside.
            if (!disallowIntercept) super.requestDisallowInterceptTouchEvent(false)
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    dismissing = false
                    pauseAutoDismiss()
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!dragging && abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                        dragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    if (!dragging && currentView === this) scheduleAutoDismiss(this)
                    return dragging
                }
            }
            return false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    dismissing = false
                    pauseAutoDismiss()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        val deltaX = event.rawX - downX
                        val deltaY = event.rawY - downY
                        if (abs(deltaX) > touchSlop && abs(deltaX) > abs(deltaY)) {
                            dragging = true
                        }
                    }
                    if (dragging) {
                        val deltaX = event.rawX - downX
                        translationX = deltaX
                        alpha = 1f - (abs(deltaX) / width.coerceAtLeast(1)).coerceIn(0f, 0.65f)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) finishHorizontalGesture(event.rawX - downX)
                    if (!dismissing && currentView === this) scheduleAutoDismiss(this)
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragging && !dismissing) returnToOrigin()
                    if (currentView === this) scheduleAutoDismiss(this)
                    return true
                }
            }
            return true
        }

        private fun finishHorizontalGesture(deltaX: Float) {
            val threshold = max(dp(SWIPE_DISMISS_THRESHOLD_DP).toFloat(), width * SWIPE_DISMISS_FRACTION)
            if (abs(deltaX) >= threshold) {
                dismissing = true
                animate()
                    .translationX(sign(deltaX) * (width + dp(48)).toFloat())
                    .alpha(0f)
                    .setDuration(SWIPE_DISMISS_ANIMATION_MS)
                    .withEndAction {
                        if (currentView === this) removeCurrent()
                    }
                    .start()
            } else {
                returnToOrigin()
            }
        }

        private fun returnToOrigin() {
            animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(SWIPE_RETURN_ANIMATION_MS)
                .start()
        }
    }

    /** Measures like a normal ScrollView until the old seven-line viewport is reached. */
    private class BoundedScrollView(
        context: Context,
        private val maxHeight: Int,
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            if (measuredHeight > maxHeight) {
                setMeasuredDimension(measuredWidth, maxHeight)
            }
        }
    }

    private data class OverlayPalette(
        val background: Int,
        val border: Int,
        val title: Int,
        val body: Int,
        val meta: Int,
        val handle: Int,
        val avatar: Int,
        val avatarText: Int,
    )

    private fun createPalette(backgroundColor: String, glassEnabled: Boolean): OverlayPalette {
        val base = Color.parseColor(normalizeOverlayBackgroundColor(backgroundColor))
        val darkSurface = luminance(base) < 0.52f
        val foreground = if (darkSurface) Color.WHITE else Color.rgb(25, 48, 61)
        val secondary = if (darkSurface) Color.argb(224, 240, 246, 250) else Color.rgb(39, 67, 81)
        val meta = if (darkSurface) Color.argb(210, 220, 234, 241) else Color.rgb(83, 126, 144)
        val avatar = if (darkSurface) Color.rgb(144, 201, 221) else Color.rgb(15, 118, 110)
        return OverlayPalette(
            background = Color.argb(
                if (glassEnabled) GLASS_BACKGROUND_ALPHA else SOLID_BACKGROUND_ALPHA,
                Color.red(base),
                Color.green(base),
                Color.blue(base),
            ),
            border = if (glassEnabled) {
                Color.argb(185, 255, 255, 255)
            } else {
                Color.argb(210, 255, 255, 255)
            },
            title = foreground,
            body = secondary,
            meta = meta,
            handle = if (darkSurface) Color.argb(220, 212, 235, 243) else Color.rgb(99, 158, 181),
            avatar = avatar,
            avatarText = if (darkSurface) Color.rgb(22, 49, 62) else Color.WHITE,
        )
    }

    private fun luminance(color: Int): Float =
        (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f

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
        const val SWIPE_DISMISS_ANIMATION_MS = 180L
        const val SWIPE_RETURN_ANIMATION_MS = 140L
        const val SWIPE_DISMISS_THRESHOLD_DP = 96
        const val SWIPE_DISMISS_FRACTION = 0.25f
        const val BODY_VIEWPORT_DP = 148
        const val GLASS_BLUR_RADIUS_DP = 28
        const val SOLID_BACKGROUND_ALPHA = 248
        const val GLASS_BACKGROUND_ALPHA = 182
    }
}
