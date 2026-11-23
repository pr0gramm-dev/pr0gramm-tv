package dev.pr0gramm.tv.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.cutShape
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.asString
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.appsettings.PointerLook
import kotlinx.coroutines.delay

/** The picture in front of a notice: the pointer, or two links with a jump between them for "Focus". */
enum class NoticeIcon { HAND, GLOW, FOCUS }

/**
 * One short message over the page; [hint] is an optional second line. [id] tells two equal messages apart, so the
 * second shows its full time too.
 */
data class Notice(val text: UiText, val hint: UiText?, val icon: NoticeIcon?, val id: Long)

/**
 * Short messages over the page, drawn by the app in place of a system toast: since Android 12 a toast shows
 * the app's launcher icon, and this TV app has none, only a banner. The newest one replaces the one before and
 * goes after [NOTICE_MS].
 */
@Stable
class Notices {
    var current by mutableStateOf<Notice?>(null)
        private set

    private var count = 0L

    fun show(text: UiText, hint: UiText? = null, icon: NoticeIcon? = null) {
        current = Notice(text, hint, icon, ++count)
    }

    /** Says which control the page menu switched to, named as in the setting "Control"; [look] picks the icon. */
    fun pointer(on: Boolean, look: PointerLook) = if (on) {
        val icon = if (look == PointerLook.GLOW) NoticeIcon.GLOW else NoticeIcon.HAND
        show(text(R.string.control_pointer), text(R.string.notice_pointer_hint), icon)
    } else {
        show(text(R.string.control_focus), text(R.string.notice_focus_hint), NoticeIcon.FOCUS)
    }

    /** Draws [current] in the top right corner; it slides in from the right like a notification of the TV. */
    @Composable
    fun Content() {
        val notice = current
        LaunchedEffect(notice?.id) {
            if (notice == null) return@LaunchedEffect
            delay(NOTICE_MS)
            if (current?.id == notice.id) current = null
        }
        // Keeps the text while the notice fades out.
        var last by remember { mutableStateOf(notice) }
        if (notice != null) last = notice
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            AnimatedVisibility(
                notice != null,
                enter = slideInHorizontally { it / SLIDE_PART } + fadeIn(),
                exit = fadeOut(),
            ) {
                last?.let { NoticeCard(it) }
            }
        }
    }
}

/** Surface 1 with the cut corners; an orange edge on the left when the notice is about the focus or pointer. */
@Composable
private fun NoticeCard(notice: Notice) {
    val stage = LocalStage.current
    val edge = if (notice.icon != null) Night.Focus else Night.Text3
    Row(
        Modifier
            .padding(top = stage.dp(NOTICE_TOP), end = stage.dp(NOTICE_END))
            .widthIn(max = stage.dp(NOTICE_MAX_WIDTH))
            .clip(stage.cutShape(NOTICE_CUT))
            .background(Night.Surface1)
            .drawBehind { drawRect(edge, size = Size(stage.dp(EDGE).toPx(), size.height)) }
            .padding(start = stage.dp(EDGE + PAD_X), end = stage.dp(PAD_X + NOTICE_CUT))
            .padding(vertical = stage.dp(PAD_Y)),
        horizontalArrangement = Arrangement.spacedBy(stage.dp(GAP)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        notice.icon?.let { Icon(it, Modifier.size(stage.dp(ICON_SIZE))) }
        Column {
            Text(
                notice.text.asString(),
                fontSize = stage.sp(TITLE_SIZE),
                fontWeight = FontWeight.SemiBold,
                color = Night.Text,
            )
            notice.hint?.let { Text(it.asString(), fontSize = stage.sp(HINT_SIZE), color = Night.Text2) }
        }
    }
}

@Composable
private fun Icon(icon: NoticeIcon, modifier: Modifier) {
    Canvas(modifier) {
        when (icon) {
            NoticeIcon.HAND -> {
                val factor = size.height / HAND_HEIGHT
                // The hand is narrower than high and the thumb reaches left of the tip; centre it in the square.
                translate(left = (size.width - (HAND_RIGHT + HAND_LEFT) * factor) / 2) {
                    scale(factor, pivot = Offset.Zero) { drawHand() }
                }
            }
            NoticeIcon.GLOW -> translate(size.width / 2, size.height / 2) {
                scale(size.width / 2 / HALO_RADIUS, pivot = Offset.Zero) { drawGlow() }
            }
            NoticeIcon.FOCUS -> scale(size.width / FOCUS_BOX, pivot = Offset.Zero) { drawFocus() }
        }
    }
}

/** Two links on a 24 x 24 grid; a bow with an arrowhead jumps from the left, outlined one to the right, filled one. */
private fun DrawScope.drawFocus() {
    val line = Stroke(width = FOCUS_LINE, join = StrokeJoin.Round)
    drawRect(Night.Focus, topLeft = FOCUS_FROM, size = FOCUS_LINK, style = Stroke(width = FOCUS_FRAME))
    drawRect(Night.Focus, topLeft = FOCUS_TO, size = FOCUS_LINK)
    drawPath(FOCUS_BOW, Night.Focus, style = line)
    drawPath(FOCUS_HEAD, Night.Focus, style = line)
}

private val FOCUS_LINK = Size(8f, 7f)
private val FOCUS_FROM = Offset(1.5f, 13f)
private val FOCUS_TO = Offset(14.5f, 13f)

private val FOCUS_BOW = Path().apply {
    moveTo(5.5f, 11f)
    cubicTo(6.5f, 4f, 17.5f, 4f, 18.5f, 9.5f)
}

private val FOCUS_HEAD = Path().apply {
    moveTo(15.6f, 8.4f)
    lineTo(18.7f, 11f)
    lineTo(20.6f, 7.4f)
}

private const val NOTICE_MS = 3_000L
private const val SLIDE_PART = 3
private const val NOTICE_TOP = 56
private const val NOTICE_END = 64
private const val NOTICE_MAX_WIDTH = 760
private const val NOTICE_CUT = 14
private const val EDGE = 8
private const val PAD_X = 30
private const val PAD_Y = 24
private const val GAP = 24
private const val TITLE_SIZE = 30
private const val HINT_SIZE = 22
private const val ICON_SIZE = 44
private const val FOCUS_BOX = 24f
private const val FOCUS_LINE = 2.2f
private const val FOCUS_FRAME = 2f
