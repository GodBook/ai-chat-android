package com.example.aichat.background

import com.example.aichat.data.model.ScreenshotTrigger

/** Tracks complete key presses so a screenshot shortcut cannot lower media volume. */
internal class ScreenshotKeyHandler {
    enum class Key { DOWN, UP }
    data class Result(val consume: Boolean = false, val capture: Boolean = false, val stopSpeech: Boolean = false)

    private val pressedAt = mutableMapOf<Key, Long>()
    private val consumed = mutableSetOf<Key>()
    private var pairTriggered = false
    private var previousTrigger: ScreenshotTrigger? = null
    private var previouslyEnabled = false

    fun handle(
        key: Key,
        down: Boolean,
        repeat: Boolean,
        now: Long,
        enabled: Boolean,
        trigger: ScreenshotTrigger,
        speechActive: Boolean,
    ): Result {
        if (enabled != previouslyEnabled || trigger != previousTrigger) {
            pressedAt.clear()
            pairTriggered = false
            previouslyEnabled = enabled
            previousTrigger = trigger
        }
        if (!down) {
            pressedAt.remove(key)
            pairTriggered = false
            return Result(consume = consumed.remove(key))
        }
        val stopSpeech = key == Key.UP && speechActive && !repeat
        if (stopSpeech) consumed.add(key)
        if (!enabled) return Result(consume = key in consumed, stopSpeech = stopSpeech)

        // Consume single-key screenshot presses, including repeats and the matching key-up.
        if (trigger == ScreenshotTrigger.VOLUME_DOWN && key == Key.DOWN) consumed.add(key)
        pressedAt[key] = now
        if (repeat) return Result(consume = key in consumed)
        val otherKey = if (key == Key.DOWN) Key.UP else Key.DOWN
        val matched = when (trigger) {
            ScreenshotTrigger.VOLUME_DOWN -> key == Key.DOWN
            ScreenshotTrigger.VOLUME_UP_DOWN -> !pairTriggered &&
                pressedAt[otherKey]?.let { now - it in 0..PAIR_WINDOW_MS } == true
        }
        if (matched && trigger == ScreenshotTrigger.VOLUME_UP_DOWN) {
            pairTriggered = true
            consumed.add(key)
        }
        // Stopping speech must not hide this key from a subsequent two-key shortcut.
        return Result(consume = key in consumed, capture = matched, stopSpeech = stopSpeech)
    }

    companion object {
        private const val PAIR_WINDOW_MS = 2_000L
    }
}
