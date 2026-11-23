package dev.pr0gramm.tv.core.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.Dp
import kotlin.math.sqrt

/**
 * The shape "Schnitt" ([cutShape]) drawn without clipping: the content is painted as a rectangle, then the two cut
 * corners are painted over in [page], the colour behind the element, and the focus ring of width [ring] is painted
 * in [ringColor] while [showRing] says so.
 *
 * A clip to a non-rectangular outline costs this TV's GPU about 0.25 ms per tile on every frame (the start page
 * redraws the whole screen each frame), which alone pushed every D-pad frame over 16.7 ms (AC14). Two small
 * triangles cost nearly nothing. Only valid where the element lies on a plain [page] colour.
 */
fun Modifier.cutCorners(cut: Dp, ring: Dp, page: Color, ringColor: Color, showRing: () -> Boolean): Modifier =
    drawWithCache {
        val c = cut.toPx()
        val w = size.width
        val h = size.height
        val topEnd = polygon(floatArrayOf(w - c, 0f, w, 0f, w, c))
        val bottomStart = polygon(floatArrayOf(0f, h - c, c, h, 0f, h))
        val frame = polygon(CutCorners.outline(w, h, c, 0f)).apply {
            addPath(polygon(CutCorners.outline(w, h, c, ring.toPx())))
            fillType = PathFillType.EvenOdd
        }
        onDrawWithContent {
            drawContent()
            drawPath(topEnd, page)
            drawPath(bottomStart, page)
            if (showRing()) drawPath(frame, ringColor)
        }
    }

private fun polygon(points: FloatArray): Path = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    close()
}

/** The geometry of the shape "Schnitt". */
object CutCorners {
    private val DIAGONAL_INSET = sqrt(2f) - 1f

    /**
     * The corners (x, y pairs, clockwise from top left) of a [width] x [height] rectangle whose top right and bottom
     * left corners are cut off by [cut], moved inwards by [inset] on every edge, the diagonals included.
     */
    fun outline(width: Float, height: Float, cut: Float, inset: Float): FloatArray {
        val d = inset
        val along = cut + d * DIAGONAL_INSET
        return floatArrayOf(
            d, d,
            width - along, d,
            width - d, along,
            width - d, height - d,
            along, height - d,
            d, height - along,
        )
    }
}
