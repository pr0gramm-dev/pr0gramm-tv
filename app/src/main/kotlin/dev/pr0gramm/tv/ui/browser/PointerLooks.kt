package dev.pr0gramm.tv.ui.browser

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.data.appsettings.PointerLook
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Draws the pointer in [look] with its click point at (0, 0). Orange: it marks where OK acts, like the focus. */
internal fun DrawScope.drawPointer(look: PointerLook) = when (look) {
    PointerLook.HAND -> drawHand()
    PointerLook.GLOW -> drawGlow()
}

/**
 * The pointing hand of macOS (the pointer over links) in orange: a dark edge, a soft shadow below, dark lines
 * between the bent fingers. The click point is the tip of the index finger.
 */
internal fun DrawScope.drawHand() {
    translate(top = SHADOW_DROP) { drawPath(HAND, SHADOW) }
    drawPath(HAND, Night.Bg, style = Stroke(width = HAND_EDGE * 2, join = StrokeJoin.Round))
    drawPath(HAND, Night.Focus)
    for (line in KNUCKLES) drawLine(KNUCKLE, line.top, line.bottom, strokeWidth = KNUCKLE_WIDTH, cap = StrokeCap.Round)
}

/** A glowing dot like a laser pointer: an orange halo that fades out, an orange dot with a dark ring in it. */
internal fun DrawScope.drawGlow() {
    drawCircle(HALO, radius = HALO_RADIUS, center = Offset.Zero)
    drawCircle(Night.Focus, radius = DOT_RADIUS, center = Offset.Zero)
    drawCircle(Night.Bg, radius = DOT_RADIUS, center = Offset.Zero, style = Stroke(width = DOT_EDGE))
}

/** Left and right edge and height (px) of the hand, from the fingertip at (0, 0); the thumb reaches left of it. */
internal const val HAND_LEFT = -17.6f
internal const val HAND_RIGHT = 31.5f
internal const val HAND_HEIGHT = 52f

/** Radius (px) of the glow's halo: the glow fills a square of twice this size around its centre. */
internal const val HALO_RADIUS = 44f

/** A stroke from [a] to [b] with round ends of [radius], in pixels. */
private class Capsule(val a: Offset, val b: Offset, val radius: Float)

/** A line from [top] to [bottom], in pixels. */
private class Knuckle(val top: Offset, val bottom: Offset)

/**
 * The fingers and the thumb: the index finger from the tip down, then the three bent fingers left to right, then the
 * thumb, which points up and left.
 */
private val CAPSULES = listOf(
    Capsule(Offset(0f, 5f), Offset(0f, 27f), 5f),
    Capsule(Offset(9.5f, 18.5f), Offset(9.5f, 28f), 4.5f),
    Capsule(Offset(18.75f, 19.75f), Offset(18.75f, 29f), 4.75f),
    Capsule(Offset(27.5f, 23f), Offset(27.5f, 30f), 4f),
    Capsule(Offset(-12.6f, 27.5f), Offset(-2f, 35f), 5f),
)

/** The corners of the palm and wrist, as x, y pairs in pixels. */
private val PALM = floatArrayOf(-5f, 24f, 31.5f, 24f, 31.5f, 40f, 27.5f, 46f, 27.5f, 52f, 4f, 52f, 4f, 47f, -5.5f, 41f)

/** The lines between the bent fingers. */
private val KNUCKLES = listOf(
    Knuckle(Offset(5f, 18f), Offset(5f, 28f)),
    Knuckle(Offset(14f, 20f), Offset(14f, 29f)),
    Knuckle(Offset(23.25f, 23f), Offset(23.25f, 30f)),
)

private val HAND: Path = run {
    var hand = Path().apply {
        moveTo(PALM[0], PALM[1])
        for (i in 2 until PALM.size step 2) lineTo(PALM[i], PALM[i + 1])
        close()
    }
    for (capsule in CAPSULES) hand = Path.combine(PathOperation.Union, hand, capsule.outline())
    hand
}

/** The closed outline: one long side, round around [Capsule.b], the other side, round around [Capsule.a]. */
private fun Capsule.outline() = Path().apply {
    val angle = atan2(b.y - a.y, b.x - a.x)
    val side = Offset(-sin(angle), cos(angle)) * radius
    val degrees = Math.toDegrees(angle.toDouble()).toFloat()
    moveTo((a + side).x, (a + side).y)
    lineTo((b + side).x, (b + side).y)
    arcTo(Rect(b, radius), degrees + QUARTER, -HALF, forceMoveTo = false)
    lineTo((a - side).x, (a - side).y)
    arcTo(Rect(a, radius), degrees - QUARTER, -HALF, forceMoveTo = false)
    close()
}

private const val QUARTER = 90f
private const val HALF = 180f
private const val HAND_EDGE = 3f
private const val KNUCKLE_WIDTH = 2f
private const val KNUCKLE_ALPHA = 0.6f
private val KNUCKLE = Night.Bg.copy(alpha = KNUCKLE_ALPHA)
private const val SHADOW_DROP = 2.5f
private const val SHADOW_ALPHA = 0.35f
private val SHADOW = Color.Black.copy(alpha = SHADOW_ALPHA)

private const val DOT_RADIUS = 13f
private const val DOT_EDGE = 3f
private const val HALO_ALPHA = 0.7f
private val HALO = Brush.radialGradient(
    listOf(Night.Focus.copy(alpha = HALO_ALPHA), Color.Transparent),
    center = Offset.Zero,
    radius = HALO_RADIUS,
)
