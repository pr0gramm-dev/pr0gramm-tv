package dev.pr0gramm.tv.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import kotlin.math.abs

/**
 * Keeps the focused tile in view with the least scrolling (AC13). [before] and [after] (px) stay free around the
 * focused frame, so the block title above and the title tag and sub line below show too. Lazy lists wrap this spec and
 * keep only its distance (foundation 1.12): the strips and the page scroll with the default spring, about 200 ms
 * measured on the TV.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ShortScroll(private val before: Float = 0f, private val after: Float = 0f) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        scrollDistance(offset, size, containerSize, before, after)
}

/**
 * The least scroll that brings [offset]..[offset]+[size], widened by [before] and [after], into 0..[containerSize];
 * 0 if it fits or cannot fit.
 */
internal fun scrollDistance(offset: Float, size: Float, containerSize: Float, before: Float, after: Float): Float {
    val leading = offset - before
    val trailing = offset + size + after
    return when {
        leading >= 0f && trailing <= containerSize -> 0f
        leading < 0f && trailing > containerSize -> 0f
        abs(leading) < abs(trailing - containerSize) -> leading
        else -> trailing - containerSize
    }
}

/**
 * Lets a held arrow key move the focus at most once per [gapMs], so a held key scrolls at a
 * constant pace. Without it the key repeats about every 50 ms, each repeat restarts the scroll with the
 * whole remaining distance, the page races, and the focus runs ahead of it. A single press always passes.
 */
internal class MovePace(private val gapMs: Long) {
    private var last = Long.MIN_VALUE / 2

    /** Whether the arrow key event at [now] (ms) may move the focus; [repeat] is a repeat of a held key. */
    fun allow(now: Long, repeat: Boolean): Boolean {
        if (repeat && now - last < gapMs) return false
        last = now
        return true
    }
}

internal const val SCROLL_MS = 150

// Time (ms) between two focus moves of a held arrow key: ten tiles per second (owner's pace).
internal const val MOVE_GAP_MS = 100L
