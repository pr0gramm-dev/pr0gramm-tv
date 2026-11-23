package dev.pr0gramm.tv.ui.browser

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.translate
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.data.appsettings.PointerLook
import kotlinx.coroutines.delay

/**
 * The pointer of pointer mode (AC5). Arrow keys move it, faster while held; a tap clicks like a finger at its
 * position; pushing against an edge scrolls with mouse-wheel events there. Events go to whatever view
 * [target] returns (the WebView, or the video fullscreen view). Positions are in pixels of that view, which
 * fills the screen like the overlay that draws the pointer ([Content]).
 */
@Stable
class PointerOverlay(private val target: () -> View) {
    var x by mutableFloatStateOf(-1f)
        private set
    var y by mutableFloatStateOf(-1f)
        private set

    /** Drawn; [Content] hides it [HIDE_MS] after it was last shown. */
    var visible by mutableStateOf(false)
        private set

    /** Counts every [show], so the hide timer starts over. */
    private var shows by mutableIntStateOf(0)

    private var downTime = 0L

    /** One step of [dx], [dy] (-1, 0 or 1); [repeat] is the key's repeat count. A hidden pointer only shows. */
    fun move(dx: Int, dy: Int, repeat: Int) {
        if (!visible) {
            show()
            return
        }
        placeFirst()
        val step = minOf(MAX_STEP, BASE_STEP + REPEAT_STEP * repeat).toFloat()
        val nx = x + dx * step
        val ny = y + dy * step
        clampTo(nx, ny)
        // Pushing past an edge scrolls the content under the pointer (also inner scroll areas). The scroll keeps one
        // pace: a held key speeds up the pointer, not the page.
        if (nx != x || ny != y) mouse(MotionEvent.ACTION_SCROLL, -dx * SCROLL_STEP, -dy * SCROLL_STEP)
        mouse(MotionEvent.ACTION_HOVER_MOVE)
        show()
    }

    /** Moves the pointer, e.g. onto the element that had the focus, or onto a player to [tap]. */
    fun placeAt(px: Float, py: Float) {
        clampTo(px, py)
        mouse(MotionEvent.ACTION_HOVER_MOVE)
    }

    /** Clicks like a finger where the pointer is. */
    fun tap() {
        placeFirst()
        downTime = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            send(action, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.TOOL_TYPE_FINGER) {
                it.pressure = 1f
                it.size = 1f
            }
        }
        show()
    }

    fun show() {
        visible = true
        shows++
    }

    /** Hidden until the next arrow key (that key only brings it back). */
    fun hide() {
        visible = false
    }

    /** Draws the pointer in [look] while [enabled] (pointer mode) and [visible]; hides it after a while idle. */
    @Composable
    fun Content(enabled: Boolean, look: PointerLook, modifier: Modifier = Modifier) {
        LaunchedEffect(shows) {
            delay(HIDE_MS)
            hide()
        }
        Canvas(modifier.fillMaxSize()) {
            if (!enabled || !visible) return@Canvas
            translate(x, y) { drawPointer(look) }
        }
    }

    /** The pointer starts in the middle of the screen. */
    private fun placeFirst() {
        if (x < 0) clampTo(target().width / 2f, target().height / 2f)
    }

    private fun clampTo(px: Float, py: Float) {
        val v = target()
        x = px.coerceIn(0f, (v.width - 1f).coerceAtLeast(0f))
        y = py.coerceIn(0f, (v.height - 1f).coerceAtLeast(0f))
    }

    private fun mouse(action: Int, hScroll: Float = 0f, vScroll: Float = 0f) =
        send(action, InputDevice.SOURCE_MOUSE, MotionEvent.TOOL_TYPE_MOUSE) {
            it.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
            it.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        }

    /** One event at the pointer: touches as touch events, mouse moves and wheel scrolls as generic motion. */
    private inline fun send(action: Int, source: Int, tool: Int, axes: (MotionEvent.PointerCoords) -> Unit) {
        val props = MotionEvent.PointerProperties().also {
            it.id = 0
            it.toolType = tool
        }
        val coords = MotionEvent.PointerCoords().also {
            it.x = x
            it.y = y
            axes(it)
        }
        val now = SystemClock.uptimeMillis()
        val touch = source == InputDevice.SOURCE_TOUCHSCREEN
        val event = MotionEvent.obtain(
            if (touch) downTime else now, now, action, 1, arrayOf(props), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, source, 0,
        )
        val v = target()
        if (touch) v.dispatchTouchEvent(event) else v.dispatchGenericMotionEvent(event)
        event.recycle()
    }

    private companion object {
        const val BASE_STEP = 8
        const val REPEAT_STEP = 4
        const val MAX_STEP = 40

        // Wheel units per arrow key event at an edge; the old first step, before the key repeat sped it up 5 times.
        const val SCROLL_STEP = 0.5f
    }
}

/**
 * Everything drawn over the page: on the first load the [Splash] until the page shows something; later a thin bar
 * at the top while a page loads longer than [BAR_DELAY_MS]; the pointer; the [notices].
 */
@Composable
fun BrowserOverlay(look: SplashLook, splash: Boolean, state: BrowserState, pointer: PointerOverlay, notices: Notices) {
    val progress = state.progress
    val stage = LocalStage.current
    var bar by remember { mutableStateOf(false) }
    val loading = progress < FULL
    LaunchedEffect(loading) {
        bar = false
        if (loading) {
            delay(BAR_DELAY_MS)
            bar = true
        }
    }
    if (bar && !splash) {
        Box(Modifier.fillMaxWidth(progress / FULL.toFloat()).height(stage.dp(BAR_HEIGHT)).background(Night.Focus))
    }
    AnimatedVisibility(splash, enter = EnterTransition.None, exit = fadeOut()) { Splash(look, progress) }
    pointer.Content(enabled = state.pointer && !splash, look = state.look)
    notices.Content()
}

private const val HIDE_MS = 5_000L
private const val BAR_DELAY_MS = 400L
private const val BAR_HEIGHT = 6
private const val FULL = 100
