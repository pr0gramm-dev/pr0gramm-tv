package dev.pr0gramm.tv.ui.launcher

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.cutCorners
import dev.pr0gramm.tv.core.remotekeys.MENU_KEY
import dev.pr0gramm.tv.core.remotekeys.OK_KEYS
import dev.pr0gramm.tv.core.remotekeys.OkPress
import dev.pr0gramm.tv.core.text.asString
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

/**
 * The OK button of the remote: tap -> [onTap], held for 600 ms -> [onHold] (one rule, see [OkPress]). The gear key
 * ([MENU_KEY]) does what the hold does, on release.
 */
@Composable
private fun Modifier.okKeys(onTap: () -> Unit, onHold: () -> Unit): Modifier {
    val press = remember { OkPress(SystemClock::uptimeMillis) }
    var held by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val hold by rememberUpdatedState(onHold)
    LaunchedEffect(held) {
        while (held) {
            delay(POLL_MS)
            if (press.poll()) hold()
        }
    }
    // The hold opens a menu that takes the focus, so the release never comes here: end the press on focus loss.
    return onFocusChanged {
        if (!it.isFocused) {
            press.cancel()
            held = false
        }
    }.onKeyEvent { event ->
        if (event.key == MENU_KEY) {
            if (event.type == KeyEventType.KeyUp) hold()
            return@onKeyEvent true
        }
        if (event.key !in OK_KEYS) return@onKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                press.down()
                held = true
            }
            KeyEventType.KeyUp -> {
                held = false
                if (press.up() == OkPress.Press.TAP) tap()
            }
        }
        true
    }
}

/**
 * One tile like `final.html`: a frame of 360 x 202 on the stage, cut like [cutCorners] on the page colour, with the
 * title tag and the sub line below it. Focus changes only colours: the ring on the frame and the title tag (AC13).
 * [onDrawn] is called once its picture is on screen (or when it has none to wait for), for `reportFullyDrawn` (AC14).
 */
@Composable
fun TileCard(
    tile: Tile,
    onFocus: () -> Unit,
    onTap: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onDrawn: () -> Unit = {},
) {
    val stage = LocalStage.current
    var focused by remember { mutableStateOf(false) }
    // "today" and "yesterday" change at midnight: the date is read again each minute, like the clock of the header.
    val today by produceState(LocalDate.now()) {
        while (true) {
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
            value = LocalDate.now()
        }
    }
    val sub = remember(tile, today) { TileText.sub(tile, today, ZoneId.systemDefault()) }?.asString()
    Column(
        modifier
            .width(stage.dp(TILE_WIDTH))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .okKeys(onTap, onHold)
            .focusable(),
    ) {
        Box(
            Modifier
                .size(stage.dp(TILE_WIDTH), stage.dp(TILE_HEIGHT))
                .cutCorners(stage.dp(CUT), stage.dp(RING), Night.Bg, Night.Focus) { focused }
                .background(Night.Surface1),
        ) {
            TileFace(tile, onDrawn)
        }
        TitleTag(TileText.title(tile), TileText.star(tile), focused)
        // Always one sub line, blank without text: every tile of a strip has the same height, so the strip does not
        // jump when the tiles in view change while scrolling sideways. A no-break space, so the blank line is
        // measured like a line with text.
        SubLine(sub ?: NO_BREAK_SPACE, TileText.source(tile).takeIf { sub != null })
    }
}

private const val NO_BREAK_SPACE = "\u00A0"
const val TILE_WIDTH = 360
const val TILE_HEIGHT = 202
private const val POLL_MS = 50L
private const val MINUTE_MS = 60_000L
private const val CUT = 20
private const val RING = 4
