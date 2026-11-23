package dev.pr0gramm.tv.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Text
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.TagShape

/**
 * The slanted title tag under the frame (`body.nacht .tile .label`): plain grey text, filled with the focus colour
 * and bold while focused (AC13). It sticks out to the left by its start padding, so its text lines up with the frame.
 * The fill is a shape background, not a clip (a clip per tile costs this TV's GPU too much, see `core/CutCorners.kt`).
 */
@Composable
internal fun TitleTag(text: String, star: Boolean, focused: Boolean) {
    val stage = LocalStage.current
    val shape = remember(stage.scale) { TagShape(stage.dp(TAG_SLANT)) }
    // The star comes from a fallback font with a taller line, which made a favourite's tile 2 px taller: its strip
    // jumped when the favourite scrolled out of view. So the line has a fixed height, and the star is in its paragraph.
    val starColor = if (focused) Night.OnFocus else Night.Star
    val line = remember(text, star, starColor) {
        buildAnnotatedString {
            if (star) {
                withStyle(SpanStyle(color = starColor)) { append("★") }
                append(STAR_GAP)
            }
            append(text)
        }
    }
    Row(
        Modifier
            .padding(top = stage.dp(TAG_TOP))
            .offset(x = -stage.dp(TAG_START))
            .widthIn(max = stage.dp(TILE_WIDTH))
            .then(if (focused) Modifier.background(Night.Focus, shape) else Modifier)
            .padding(stage.dp(TAG_START), stage.dp(TAG_V), stage.dp(TAG_END), stage.dp(TAG_V)),
    ) {
        Text(
            line,
            fontSize = stage.sp(TAG_SIZE),
            lineHeight = stage.sp(TAG_LINE),
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            color = if (focused) Night.OnFocus else Night.Text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(stage.dp(TAG_LINE)),
        )
    }
}

/**
 * The grey line under the title tag (`.sub`): an app's state, or when an entry was saved or watched. [source], the
 * entry's site, comes first and a little brighter: "**Lumen** · gestern" (herkunft.html, variant D).
 */
@Composable
internal fun SubLine(text: String, source: String? = null) {
    val stage = LocalStage.current
    val line = remember(text, source) {
        buildAnnotatedString {
            if (source != null) {
                withStyle(SpanStyle(color = Night.Text2, fontWeight = FontWeight.SemiBold)) { append(source) }
                append(" · ")
            }
            append(text)
        }
    }
    Text(
        line,
        fontSize = stage.sp(SUB_SIZE),
        lineHeight = stage.sp(SUB_LINE),
        color = Night.Text3,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = stage.dp(SUB_TOP)),
    )
}

// The mock's sizes (final.html, body.nacht), 1:1: the tiles are narrower than the mock's, the text is not.
private const val TAG_TOP = 10
private const val TAG_SLANT = 8
private const val TAG_START = 12
private const val TAG_END = 16
private const val TAG_V = 3
private const val TAG_SIZE = 24
private const val TAG_LINE = 30
private const val STAR_GAP = "\u2002" // en space, about the mock's 6 px gap at this size
private const val SUB_TOP = 2
private const val SUB_SIZE = 20
private const val SUB_LINE = 25
