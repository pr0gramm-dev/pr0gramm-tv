package dev.pr0gramm.tv.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.Text
import dev.pr0gramm.tv.core.designsystem.GeistMono
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.Stage

/**
 * "PR0GRAMM" as a cinema ticket, the same design as the launcher banner: orange, slanted, corners cut top right
 * and bottom left, a notch top and bottom and a dashed tear line in front of the stub on the right. The zero is the
 * slashed zero of Geist Mono, stretched to the width of the Geist "O". A static shape, no animation (AC12).
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val stage = LocalStage.current
    val shape = remember(stage.scale) { TicketShape(stage) }
    val word = remember {
        buildAnnotatedString {
            append("PR")
            withStyle(SLASHED_ZERO) { append("0") }
            append("GRAMM")
        }
    }
    Text(
        word,
        fontSize = stage.sp(TEXT_SIZE),
        fontWeight = FontWeight.ExtraBold,
        color = Night.OnFocus,
        modifier = modifier
            .graphicsLayer { rotationZ = TILT }
            .clip(shape)
            .background(Night.Focus)
            .drawBehind {
                val x = size.width - stage.dp(PERFORATION_FROM_RIGHT).toPx()
                drawLine(
                    Night.OnFocus,
                    Offset(x, stage.dp(PERFORATION_INSET).toPx()),
                    Offset(x, size.height - stage.dp(PERFORATION_INSET).toPx()),
                    strokeWidth = stage.dp(PERFORATION_WIDTH).toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(stage.dp(DASH).toPx(), stage.dp(GAP).toPx())),
                )
            }
            .padding(stage.dp(PAD_LEFT), stage.dp(PAD_TOP), stage.dp(PAD_RIGHT), stage.dp(PAD_BOTTOM)),
    )
}

/**
 * A rectangle with the corners top right and bottom left cut off by [CHAMFER] like the UI, and a half circle bitten
 * out of the top and the bottom edge, [NOTCH_FROM_RIGHT] from the right.
 */
private class TicketShape(private val stage: Stage) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { stage.dp(NOTCH_RADIUS).toPx() }
        val x = size.width - with(density) { stage.dp(NOTCH_FROM_RIGHT).toPx() }
        val cut = with(density) { stage.dp(CHAMFER).toPx() }
        val ticket = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - cut, 0f)
            lineTo(size.width, cut)
            lineTo(size.width, size.height)
            lineTo(cut, size.height)
            lineTo(0f, size.height - cut)
            close()
        }
        val notches = Path().apply {
            addOval(Rect(Offset(x, 0f), radius))
            addOval(Rect(Offset(x, size.height), radius))
        }
        return Outline.Generic(Path().apply { op(ticket, notches, PathOperation.Difference) })
    }
}

private const val TEXT_SIZE = 40
private const val TILT = -4f
private const val PAD_LEFT = 16
private const val PAD_TOP = 8
private const val PAD_RIGHT = 46
private const val PAD_BOTTOM = 6
private const val NOTCH_RADIUS = 9
private const val NOTCH_FROM_RIGHT = 28
private const val PERFORATION_FROM_RIGHT = 27
private const val PERFORATION_INSET = 12
private const val PERFORATION_WIDTH = 3
private const val DASH = 5
private const val GAP = 3
private const val CHAMFER = 10

private val SLASHED_ZERO = SpanStyle(
    fontFamily = GeistMono,
    textGeometricTransform = TextGeometricTransform(scaleX = ZERO_SCALE_X),
)

/** Advance of the Geist ExtraBold "O" (788) over the Geist Mono ExtraBold "0" (600), both in font units. */
private const val ZERO_SCALE_X = 1.313f
