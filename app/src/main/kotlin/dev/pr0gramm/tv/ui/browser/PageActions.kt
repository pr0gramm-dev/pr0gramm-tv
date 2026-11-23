package dev.pr0gramm.tv.ui.browser

import android.media.AudioManager
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.pr0gramm.tv.core.designsystem.Night

/**
 * Carries out the [KeyAction]s that act on the page, its pointer and its players, mostly through
 * `window.__tvw` (focus-ring.js). [fullscreen] is the video fullscreen view, if any; [gone] is true once the
 * WebView is destroyed. UI thread only.
 */
internal class PageActions(
    private val webView: WebView,
    private val pointer: PointerOverlay,
    private val scripts: Scripts,
    private val fullscreen: () -> View?,
    private val gone: () -> Boolean,
) {
    /** [event] is the key event the router answered with [action]. */
    fun run(action: KeyAction, event: KeyEvent) {
        when (action) {
            is KeyAction.MovePointer -> pointer.move(action.dx, action.dy, event.repeatCount)
            KeyAction.Tap -> pointer.tap()
            KeyAction.PressOk -> pressOk(event)
            KeyAction.PlayVideo -> {
                pressOk(event)
                // Right after a real key press: the page may enter fullscreen only within a user gesture.
                call("enterPlayer()")
            }
            KeyAction.PlayEmbedded -> playEmbedded()
            KeyAction.OpenKeyboard -> webView.evaluateJavascript("window.__tvw&&__tvw.allowKeyboard()") {
                if (!gone()) webView.context.getSystemService(InputMethodManager::class.java).showSoftInput(webView, 0)
            }
            is KeyAction.LeavePlayer -> leavePlayer(KeyEvent(event))
            is KeyAction.Volume -> webView.context.getSystemService(AudioManager::class.java).adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (action.delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI,
            )
            is KeyAction.Seek -> call("seek(${action.seconds})")
            KeyAction.TogglePlay -> call("toggle()")
            else -> Unit
        }
    }

    /** Switches between pointer and focus mode and hands the position over, so the user goes on from there. */
    fun setPointer(on: Boolean) {
        if (gone()) return
        if (on) {
            webView.evaluateJavascript("window.__tvw&&(__tvw.ring(false),__tvw.focusCenter())") { json ->
                point(json)?.let { pointer.placeAt(it.first, it.second) }
                pointer.show()
            }
        } else {
            pointer.hide()
            val focusAt = "__tvw.focusAt(${pointer.x},${pointer.y})"
            webView.evaluateJavascript("window.__tvw&&($focusAt,__tvw.ring(true))", null)
        }
    }

    /** The focus ring script in the theme colours: orange with a dark contrast line. */
    private val focusRing by lazy {
        scripts[Script.FOCUS_RING].replace(Scripts.ACCENT, css(Night.Focus)).replace(Scripts.OUTLINE, css(Night.Bg))
    }

    /** Sets a new document up: zoom (percent), video watch and the focus ring, which is on in focus mode. */
    fun prepare(view: WebView, zoom: Int, pointerMode: Boolean) {
        if (zoom != NO_ZOOM) view.evaluateJavascript("${scripts[Script.ZOOM_VIEWPORT]}($zoom)", null)
        view.evaluateJavascript(scripts[Script.WATCH_VIDEO], null)
        view.evaluateJavascript("$focusRing;__tvw.ring(${!pointerMode})", null)
    }

    /** Plays a media address the WebView offered as a download. Joins the history, so BACK works. */
    fun playMedia(url: String) {
        val src = url.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
        val html = """<!doctype html><html><head><meta name="viewport" content="width=device-width">
            |<style>html,body{margin:0;height:100%;background:#000}video{width:100%;height:100%}</style></head>
            |<body><video src="$src" autoplay controls></video></body></html>
        """.trimMargin()
        webView.loadDataWithBaseURL(url, html, "text/html", "utf-8", url)
    }

    /** Calls a function of `window.__tvw` in the main frame. */
    fun call(function: String) {
        if (!gone()) webView.evaluateJavascript("window.__tvw&&__tvw.$function", null)
    }

    /** OK (down and up, from the release [up]) to the page or the fullscreen view. */
    private fun pressOk(up: KeyEvent) {
        val target = fullscreen() ?: webView
        target.dispatchKeyEvent(KeyEvent.changeAction(up, KeyEvent.ACTION_DOWN))
        target.dispatchKeyEvent(up)
    }

    /** Embedded players start on a tap; Enter only reaches the hoster page's body. */
    private fun playEmbedded() {
        webView.evaluateJavascript("window.__tvw&&__tvw.focusCenter()") { json ->
            val xy = point(json)
            if (xy == null || gone()) return@evaluateJavascript
            pointer.placeAt(xy.first, xy.second)
            pointer.tap()
            // Once the tap has reached the page: fullscreen needs the user activation it brings.
            webView.postDelayed({ call("enterPlayer()") }, ENTER_PLAYER_MS)
        }
    }

    /** Players take the arrows as hotkeys: the focus moves onto a stand-in first, then the page navigates. */
    private fun leavePlayer(arrow: KeyEvent) {
        val code = arrow.keyCode
        val down = code == KeyEvent.KEYCODE_DPAD_DOWN
        val up = code == KeyEvent.KEYCODE_DPAD_UP
        webView.evaluateJavascript("!!window.__tvw&&__tvw.standIn($down,$up)") {
            if (it == "true" && !gone()) {
                webView.dispatchKeyEvent(arrow)
                webView.dispatchKeyEvent(KeyEvent.changeAction(arrow, KeyEvent.ACTION_UP))
            }
        }
    }

    companion object {
        private const val ENTER_PLAYER_MS = 300L
        private const val RGB = 0xffffff
        private val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)

        /** The router's name for an Android key code. */
        fun keyOf(code: Int): Key = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> Key.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Key.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Key.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Key.RIGHT
            KeyEvent.KEYCODE_BACK -> Key.BACK
            KeyEvent.KEYCODE_NOTIFICATION -> Key.MENU
            in OK_KEYS -> Key.OK
            else -> Key.OTHER
        }

        /** CSS form of a colour, e.g. "#cba6f7". */
        private fun css(color: Color) = "#%06x".format(color.toArgb() and RGB)

        /** `[x, y]` from focusCenter() as JSON, or null. */
        private fun point(json: String?): Pair<Float, Float>? {
            val xy = json.orEmpty().trim('[', ']').split(',').mapNotNull { it.trim().toFloatOrNull() }
            return if (xy.size == 2) xy[0] to xy[1] else null
        }
    }
}

/** Full screen for the page; a swipe would show the bars for a moment (no swipes on a TV). */
internal fun Window.hideSystemBars() {
    insetsController?.let {
        it.hide(WindowInsets.Type.systemBars())
        it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
