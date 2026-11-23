package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.core.remotekeys.OkPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyRouterTest {
    private var now = 0L
    private val router = KeyRouter(clock = { now })

    private val focus = Mode(fullscreen = false, pointer = false, menuOpen = false, canGoBack = false)
    private val pointer = focus.copy(pointer = true)
    private val fullscreenVideo = focus.copy(fullscreen = true, focus = PageFocus.VIDEO)

    private fun press(key: Key, mode: Mode): Pair<KeyAction, KeyAction> =
        router.on(key, down = true, mode) to router.on(key, down = false, mode)

    private fun tap(mode: Mode): KeyAction {
        val (down, up) = press(Key.OK, mode)
        assertEquals(KeyAction.Consume, down)
        return up
    }

    // ---- OK: tap and hold ----------------------------------------------------

    @Test
    fun holdingOkSwitchesThePointerOnceThroughPoll() {
        assertEquals(KeyAction.Consume, router.on(Key.OK, down = true, focus))
        now = OkPress.LONG_PRESS_MS - 1
        assertNull(router.poll())
        now = OkPress.LONG_PRESS_MS
        assertEquals(KeyAction.TogglePointer, router.poll())
        assertNull(router.poll())
        val switched = focus.copy(pointer = true)
        assertEquals(KeyAction.Consume, router.on(Key.OK, down = true, switched))
        assertEquals(KeyAction.Consume, router.on(Key.OK, down = false, switched))
    }

    @Test
    fun holdingOkSwitchesThePointerThroughKeyRepeats() {
        router.on(Key.OK, down = true, pointer)
        now = 500
        assertEquals(KeyAction.Consume, router.on(Key.OK, down = true, pointer))
        now = 650
        assertEquals(KeyAction.TogglePointer, router.on(Key.OK, down = true, pointer))
        now = 700
        assertEquals(KeyAction.Consume, router.on(Key.OK, down = true, focus))
        assertEquals(KeyAction.Consume, router.on(Key.OK, down = false, focus))
    }

    @Test
    fun holdingOkInFullscreenSwitchesThePointerToo() {
        router.on(Key.OK, down = true, fullscreenVideo)
        now = OkPress.LONG_PRESS_MS
        assertEquals(KeyAction.TogglePointer, router.poll())
        assertEquals(KeyAction.Consume, router.on(Key.OK, down = false, fullscreenVideo))
    }

    @Test
    fun okInsideTheOpenMenuGoesToTheMenu() {
        val open = focus.copy(menuOpen = true)
        assertEquals(KeyAction.Pass to KeyAction.Pass, press(Key.OK, open))
        now = 5_000
        assertNull(router.poll())
    }

    @Test
    fun okUpWithoutDownIsLeftAlone() {
        assertEquals(KeyAction.Pass, router.on(Key.OK, down = false, focus))
    }

    @Test
    fun shortOkInPointerModeTaps() {
        assertEquals(KeyAction.Tap, tap(pointer))
        assertEquals(KeyAction.Tap, tap(pointer.copy(fullscreen = true, focus = PageFocus.VIDEO)))
    }

    @Test
    fun shortOkInFocusModeDependsOnTheFocusedElement() {
        assertEquals(KeyAction.PressOk, tap(focus))
        assertEquals(KeyAction.PlayVideo, tap(focus.copy(focus = PageFocus.VIDEO)))
        assertEquals(KeyAction.PlayEmbedded, tap(focus.copy(focus = PageFocus.FRAME)))
        assertEquals(KeyAction.OpenKeyboard, tap(focus.copy(focus = PageFocus.TEXT)))
    }

    // ---- fullscreen video keys ----------------------------------------------

    @Test
    fun fullscreenVideoKeys() {
        assertEquals(KeyAction.Volume(+1) to KeyAction.Consume, press(Key.UP, fullscreenVideo))
        assertEquals(KeyAction.Volume(-1) to KeyAction.Consume, press(Key.DOWN, fullscreenVideo))
        assertEquals(KeyAction.Seek(-10) to KeyAction.Consume, press(Key.LEFT, fullscreenVideo))
        assertEquals(KeyAction.Seek(+10) to KeyAction.Consume, press(Key.RIGHT, fullscreenVideo))
        assertEquals(KeyAction.TogglePlay, tap(fullscreenVideo))
    }

    @Test
    fun heldArrowRepeatsInFullscreen() {
        router.on(Key.RIGHT, down = true, fullscreenVideo)
        assertEquals(KeyAction.Seek(+10), router.on(Key.RIGHT, down = true, fullscreenVideo))
    }

    @Test
    fun embeddedPlayerInFullscreenSeeksWithItsOwnKeys() {
        val frame = fullscreenVideo.copy(focus = PageFocus.FRAME)
        assertEquals(KeyAction.Volume(+1) to KeyAction.Consume, press(Key.UP, frame))
        assertEquals(KeyAction.Pass to KeyAction.Pass, press(Key.LEFT, frame))
        assertEquals(KeyAction.PressOk, tap(frame))
    }

    @Test
    fun fullscreenWithoutPlayerPassesArrows() {
        val none = fullscreenVideo.copy(focus = PageFocus.NONE)
        assertEquals(KeyAction.Pass to KeyAction.Pass, press(Key.UP, none))
        assertEquals(KeyAction.PressOk, tap(none))
    }

    // ---- arrows outside fullscreen ------------------------------------------

    @Test
    fun arrowsMoveThePointerInPointerMode() {
        assertEquals(KeyAction.MovePointer(0, -1) to KeyAction.Consume, press(Key.UP, pointer))
        assertEquals(KeyAction.MovePointer(0, 1) to KeyAction.Consume, press(Key.DOWN, pointer))
        assertEquals(KeyAction.MovePointer(-1, 0) to KeyAction.Consume, press(Key.LEFT, pointer))
        assertEquals(KeyAction.MovePointer(1, 0) to KeyAction.Consume, press(Key.RIGHT, pointer))
    }

    @Test
    fun arrowsGoToThePageInFocusMode() {
        assertEquals(KeyAction.Pass to KeyAction.Pass, press(Key.DOWN, focus))
        assertEquals(KeyAction.Pass to KeyAction.Pass, press(Key.DOWN, focus.copy(focus = PageFocus.TEXT)))
    }

    @Test
    fun arrowsLeaveAFocusedPlayer() {
        val video = focus.copy(focus = PageFocus.VIDEO)
        assertEquals(KeyAction.LeavePlayer(Key.DOWN) to KeyAction.Consume, press(Key.DOWN, video))
        val frame = focus.copy(focus = PageFocus.FRAME)
        assertEquals(KeyAction.LeavePlayer(Key.LEFT) to KeyAction.Consume, press(Key.LEFT, frame))
    }

    @Test
    fun arrowsGoToTheOpenMenu() {
        val open = pointer.copy(menuOpen = true, fullscreen = true, focus = PageFocus.VIDEO)
        assertEquals(KeyAction.Pass to KeyAction.Pass, press(Key.DOWN, open))
    }

    @Test
    fun otherKeysPass() {
        assertEquals(KeyAction.Pass to KeyAction.Pass, press(Key.OTHER, fullscreenVideo))
        assertEquals(KeyAction.Pass to KeyAction.Pass, press(Key.OTHER, pointer.copy(menuOpen = true)))
    }

    // ---- BACK ----------------------------------------------------------------

    @Test
    fun backOrderIsFullscreenMenuHistoryClose() {
        val all = Mode(fullscreen = true, pointer = false, menuOpen = true, canGoBack = true)
        assertEquals(KeyAction.Consume to KeyAction.ExitFullscreen, press(Key.BACK, all))
        val menu = all.copy(fullscreen = false)
        assertEquals(KeyAction.Consume to KeyAction.CloseMenu, press(Key.BACK, menu))
        val history = menu.copy(menuOpen = false)
        assertEquals(KeyAction.Consume to KeyAction.HistoryBack, press(Key.BACK, history))
        val last = history.copy(canGoBack = false)
        assertEquals(KeyAction.Consume to KeyAction.Close, press(Key.BACK, last))
    }

    @Test
    fun holdingBackLeavesTheBrowserThroughKeyRepeats() {
        val all = Mode(fullscreen = true, pointer = false, menuOpen = true, canGoBack = true)
        assertEquals(KeyAction.Consume, router.on(Key.BACK, down = true, all))
        now = OkPress.LONG_PRESS_MS - 1
        assertEquals(KeyAction.Consume, router.on(Key.BACK, down = true, all))
        now = OkPress.LONG_PRESS_MS
        assertEquals(KeyAction.Close, router.on(Key.BACK, down = true, all))
        now += 50
        assertEquals(KeyAction.Consume, router.on(Key.BACK, down = true, all))
        assertEquals(KeyAction.Consume, router.on(Key.BACK, down = false, all))
    }

    @Test
    fun holdingBackWithoutRepeatsLeavesOnRelease() {
        val history = focus.copy(canGoBack = true)
        router.on(Key.BACK, down = true, history)
        now = OkPress.LONG_PRESS_MS
        assertEquals(KeyAction.Close, router.on(Key.BACK, down = false, history))
        now += 100
        assertEquals(KeyAction.Consume to KeyAction.HistoryBack, press(Key.BACK, history))
    }

    // ---- MENU (the gear on the TCL remote) -----------------------------------

    @Test
    fun menuKeyOpensAndClosesTheMenuOnRelease() {
        assertEquals(KeyAction.Consume to KeyAction.OpenMenu, press(Key.MENU, focus))
        assertEquals(KeyAction.Consume to KeyAction.CloseMenu, press(Key.MENU, focus.copy(menuOpen = true)))
        assertEquals(KeyAction.Consume to KeyAction.OpenMenu, press(Key.MENU, fullscreenVideo))
    }

    @Test
    fun menuKeyActsOnlyAfterItsOwnPress() {
        assertEquals(KeyAction.Consume, router.on(Key.MENU, down = false, focus))
        router.on(Key.MENU, down = true, focus)
        now = 2_000
        assertEquals(KeyAction.Consume, router.on(Key.MENU, down = true, focus))
        assertEquals(KeyAction.OpenMenu, router.on(Key.MENU, down = false, focus))
    }

    @Test
    fun backActsOnReleaseOnlyAfterItsOwnPress() {
        assertEquals(KeyAction.Consume, router.on(Key.BACK, down = false, focus))
        router.on(Key.BACK, down = true, focus)
        router.on(Key.BACK, down = true, focus)
        assertEquals(KeyAction.Close, router.on(Key.BACK, down = false, focus))
        assertEquals(KeyAction.Consume, router.on(Key.BACK, down = false, focus))
    }
}
