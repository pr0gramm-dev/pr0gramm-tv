package dev.pr0gramm.tv.ui.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Text
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.cutShape
import dev.pr0gramm.tv.core.designsystem.marked
import dev.pr0gramm.tv.core.text.asString
import androidx.compose.foundation.layout.Row as LayoutRow

/**
 * The page "Remote keys": the key table of [Controls], two places per row, and a line about the gear at the bottom.
 * The page only reads; it holds the focus so no key reaches the screen below. Back is handled by the activity.
 */
@Composable
fun ControlsScreen(modifier: Modifier = Modifier) {
    val stage = LocalStage.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    Box(modifier.focusRequester(requester).focusable().fillMaxSize().background(Night.Bg)) {
        Column(Modifier.padding(start = stage.dp(SAFE_X), top = stage.dp(PAGE_TOP))) {
            NightTitle(stringResource(R.string.general_controls), Controls.HINT.asString())
            Column(verticalArrangement = Arrangement.spacedBy(stage.dp(PLACE_GAP_Y))) {
                Controls.places.chunked(2).forEach { pair ->
                    LayoutRow(horizontalArrangement = Arrangement.spacedBy(stage.dp(PLACE_GAP_X))) {
                        pair.forEach { Place(it) }
                    }
                }
            }
        }
        Text(
            Controls.FOOT.asString(),
            fontSize = stage.sp(FOOT_SIZE),
            color = Night.Text3,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = stage.dp(SAFE_X), bottom = stage.dp(FOOT_BOTTOM)),
        )
    }
}

@Composable
private fun Place(place: KeyPlace) {
    val stage = LocalStage.current
    Column(Modifier.width(stage.dp(PLACE_WIDTH))) {
        Text(
            place.name.asString(),
            fontSize = stage.sp(PLACE_SIZE),
            fontWeight = FontWeight.Bold,
            color = Night.Text,
            modifier = Modifier.padding(bottom = stage.dp(PLACE_TITLE_GAP)),
        )
        place.lines.forEach { Line(it) }
    }
}

@Composable
private fun Line(line: KeyLine) {
    val stage = LocalStage.current
    LayoutRow(Modifier.heightIn(min = stage.dp(LINE_HEIGHT)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(stage.dp(KEY_COLUMN))) { KeyCap(line) }
        Text(marked(line.text.asString(), Night.Text), fontSize = stage.sp(TEXT_SIZE), color = Night.Text2)
    }
}

/** The key in a dark cap with a cut corner; "(hold)" in a dimmer colour after it. */
@Composable
private fun KeyCap(line: KeyLine) {
    val stage = LocalStage.current
    val key = line.key.asString()
    val hold = stringResource(R.string.controls_hold)
    val text = buildAnnotatedString {
        append(key)
        if (line.held) withStyle(SpanStyle(color = Night.Text3, fontWeight = FontWeight.Medium)) { append(" $hold") }
    }
    Text(
        text,
        fontSize = stage.sp(CAP_SIZE),
        fontWeight = FontWeight.SemiBold,
        color = Night.Text,
        modifier = Modifier
            .clip(stage.cutShape(CAP_CUT))
            .background(Night.Surface2)
            .padding(horizontal = stage.dp(CAP_PAD_X), vertical = stage.dp(CAP_PAD_Y)),
    )
}

private const val PLACE_WIDTH = 840
private const val PLACE_GAP_X = 48
private const val PLACE_GAP_Y = 28
private const val PLACE_SIZE = 30
private const val PLACE_TITLE_GAP = 8
private const val LINE_HEIGHT = 46
private const val KEY_COLUMN = 246
private const val TEXT_SIZE = 24
private const val CAP_SIZE = 22
private const val CAP_CUT = 8
private const val CAP_PAD_X = 14
private const val CAP_PAD_Y = 7
private const val FOOT_SIZE = 22
private const val FOOT_BOTTOM = 40
