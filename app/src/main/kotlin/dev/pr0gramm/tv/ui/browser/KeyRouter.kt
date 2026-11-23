package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.core.remotekeys.OkPress
import dev.pr0gramm.tv.core.remotekeys.SEEK_S

/**
 * Remote keys the browser tells apart. OK stands for DPAD_CENTER, ENTER and NUMPAD_ENTER. MENU is the gear of
 * the TCL remote, which sends NOTIFICATION; its own menu key opens the TV's quick settings and never arrives.
 */
enum class Key { UP, DOWN, LEFT, RIGHT, OK, BACK, MENU, OTHER }

/**
 * What the page reports about the element the keys act on: in fullscreen the player shown there, otherwise
 * the focused element. FRAME is a player embedded from another site (iframe, out of the app's reach), VIDEO a
 * `<video>` or the player element around it, TEXT a text field.
 */
enum class PageFocus { NONE, FRAME, VIDEO, TEXT }

/** The browser state the keys depend on. */
data class Mode(
    val fullscreen: Boolean,
    val pointer: Boolean,
    val menuOpen: Boolean,
    val canGoBack: Boolean,
    val focus: PageFocus = PageFocus.NONE,
)

/** What the Activity does with a key event. */
sealed interface KeyAction {
    /** Hand the event on unchanged (to the page, or to the open menu). */
    data object Pass : KeyAction

    /** Swallow the event. */
    data object Consume : KeyAction

    data object OpenMenu : KeyAction

    data object CloseMenu : KeyAction

    /** Switch between pointer and focus mode. */
    data object TogglePointer : KeyAction

    /** Move the pointer one step; [dx] and [dy] are -1, 0 or 1. */
    data class MovePointer(val dx: Int, val dy: Int) : KeyAction

    /** Click where the pointer is. */
    data object Tap : KeyAction

    /** Send OK (down and up) to the page or the fullscreen view. */
    data object PressOk : KeyAction

    /** Send OK to the focused video, then play it in fullscreen. */
    data object PlayVideo : KeyAction

    /** Tap the centre of the focused embedded player, then play it in fullscreen. */
    data object PlayEmbedded : KeyAction

    /** Open the keyboard for the focused text field. */
    data object OpenKeyboard : KeyAction

    /** Move the focus off the focused player, then send [key] to the page, so its navigation goes on from there. */
    data class LeavePlayer(val key: Key) : KeyAction

    /** Change the TV volume by one step, [delta] is +1 or -1. */
    data class Volume(val delta: Int) : KeyAction

    data class Seek(val seconds: Int) : KeyAction

    data object TogglePlay : KeyAction

    data object ExitFullscreen : KeyAction

    data object HistoryBack : KeyAction

    /** Leave the browser, back to the start page. */
    data object Close : KeyAction
}

/**
 * The browser's key table (AC5, AC7). OK held for [OkPress.LONG_PRESS_MS] switches between pointer and focus
 * mode: through key repeats, or through [poll], which the Activity calls while OK is held. BACK acts on release,
 * in the order fullscreen → menu → history → close; held as long, it closes the browser at once (through key
 * repeats, or on release). MENU opens and closes the page menu on release. In fullscreen (focus mode) ▲/▼ set the volume, ◀/▶ seek [SEEK_S]
 * seconds (embedded players with their own keys) and OK plays or pauses.
 */
class KeyRouter(clock: () -> Long) {
    private val ok = OkPress(clock)
    private var okHeld = false
    private val backHold = OkPress(clock)
    private var backHeld = false
    private var menuHeld = false

    fun on(key: Key, down: Boolean, mode: Mode): KeyAction =
        when (key) {
            Key.BACK -> back(down, mode)
            Key.MENU -> menu(down, mode)
            Key.OK -> ok(down, mode)
            Key.UP, Key.DOWN, Key.LEFT, Key.RIGHT -> arrow(key, mode).let { if (down) it else released(it) }
            Key.OTHER -> KeyAction.Pass
        }

    /** [KeyAction.TogglePointer] exactly once when OK has been held long enough, else null. */
    fun poll(): KeyAction? = KeyAction.TogglePointer.takeIf { okHeld && ok.poll() }

    /** A release without its own press (e.g. one that started before this screen) does nothing. */
    private fun back(down: Boolean, mode: Mode): KeyAction {
        val pressed = backHeld
        backHeld = down
        if (down && !pressed) backHold.down()
        // poll is true once, as soon as the hold is long: on a repeat, or on the release of a remote without repeats.
        val long = pressed && backHold.poll()
        val tap = pressed && !down && backHold.up() == OkPress.Press.TAP
        return when {
            long -> KeyAction.Close
            !tap -> KeyAction.Consume
            mode.fullscreen -> KeyAction.ExitFullscreen
            mode.menuOpen -> KeyAction.CloseMenu
            mode.canGoBack -> KeyAction.HistoryBack
            else -> KeyAction.Close
        }
    }

    /** Acts on release, and only after its own press. */
    private fun menu(down: Boolean, mode: Mode): KeyAction {
        val pressed = menuHeld
        menuHeld = down
        return when {
            down || !pressed -> KeyAction.Consume
            mode.menuOpen -> KeyAction.CloseMenu
            else -> KeyAction.OpenMenu
        }
    }

    /** A press that starts in the open menu belongs to the menu; one that started outside stays here. */
    private fun ok(down: Boolean, mode: Mode): KeyAction =
        when {
            down && okHeld -> poll() ?: KeyAction.Consume
            down && mode.menuOpen -> KeyAction.Pass
            down -> {
                ok.down()
                okHeld = true
                KeyAction.Consume
            }
            !okHeld -> KeyAction.Pass
            else -> {
                okHeld = false
                if (ok.up() == OkPress.Press.TAP && !mode.menuOpen) tap(mode) else KeyAction.Consume
            }
        }

    private fun tap(mode: Mode): KeyAction =
        when {
            mode.pointer -> KeyAction.Tap
            mode.fullscreen -> if (mode.focus == PageFocus.VIDEO) KeyAction.TogglePlay else KeyAction.PressOk
            else -> when (mode.focus) {
                PageFocus.VIDEO -> KeyAction.PlayVideo
                PageFocus.FRAME -> KeyAction.PlayEmbedded
                PageFocus.TEXT -> KeyAction.OpenKeyboard
                PageFocus.NONE -> KeyAction.PressOk
            }
        }

    private fun arrow(key: Key, mode: Mode): KeyAction {
        val vertical = key == Key.UP || key == Key.DOWN
        val forward = key == Key.DOWN || key == Key.RIGHT
        val step = if (forward) 1 else -1
        return when {
            mode.menuOpen -> KeyAction.Pass
            mode.pointer -> if (vertical) KeyAction.MovePointer(0, step) else KeyAction.MovePointer(step, 0)
            mode.fullscreen -> when {
                mode.focus == PageFocus.NONE -> KeyAction.Pass
                vertical -> KeyAction.Volume(-step)
                mode.focus == PageFocus.FRAME -> KeyAction.Pass
                else -> KeyAction.Seek(step * SEEK_S)
            }
            mode.focus.isPlayer -> KeyAction.LeavePlayer(key)
            else -> KeyAction.Pass
        }
    }

    /** An arrow's release goes where its press went (the page never sees half a key), and only acts there. */
    private fun released(press: KeyAction): KeyAction =
        if (press == KeyAction.Pass) KeyAction.Pass else KeyAction.Consume

    private val PageFocus.isPlayer get() = this == PageFocus.VIDEO || this == PageFocus.FRAME
}
