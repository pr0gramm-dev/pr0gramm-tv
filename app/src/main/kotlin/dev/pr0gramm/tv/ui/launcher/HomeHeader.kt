package dev.pr0gramm.tv.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.GeistMono
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.cutShape
import dev.pr0gramm.tv.core.remotekeys.OK_KEYS
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Row as LayoutRow

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

/** The wordmark, the gear and the clock; there is no tab bar (spec). [gear] goes on the settings button. */
@Composable
internal fun Header(onSettings: () -> Unit, gear: Modifier = Modifier, modifier: Modifier = Modifier) {
    val stage = LocalStage.current
    val time by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
        }
    }
    LayoutRow(
        modifier
            .fillMaxWidth()
            .padding(top = stage.dp(HEADER_TOP))
            .height(stage.dp(HEADER_HEIGHT))
            .padding(horizontal = stage.dp(SAFE_X)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { Wordmark() }
        Gear(onSettings, gear)
        Text(CLOCK.format(time), fontSize = stage.sp(CLOCK_SIZE), fontFamily = GeistMono, color = Night.Text2)
    }
}

/** The settings button; it only changes colour with the focus. */
@Composable
private fun Gear(onClick: () -> Unit, modifier: Modifier) {
    val stage = LocalStage.current
    var focused by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.home_settings),
        fontSize = stage.sp(GEAR_SIZE),
        color = if (focused) Night.OnFocus else Night.Text2,
        modifier = modifier
            .padding(end = stage.dp(GEAR_GAP))
            .clip(stage.cutShape(GEAR_CUT))
            .background(if (focused) Night.Focus else Night.Bg)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val ok = event.key in OK_KEYS
                if (ok && event.type == KeyEventType.KeyUp) onClick()
                ok
            }
            .focusable()
            .padding(stage.dp(GEAR_H), stage.dp(GEAR_V)),
    )
}

// Room above the header row, so the wordmark starts inside the title-safe area (5 % of 1080).
private const val HEADER_TOP = 30
private const val HEADER_HEIGHT = 130
private const val CLOCK_SIZE = 28
private const val GEAR_SIZE = 28
private const val GEAR_CUT = 10
private const val GEAR_GAP = 32
private const val GEAR_H = 22
private const val GEAR_V = 10
private const val MINUTE_MS = 60_000L
