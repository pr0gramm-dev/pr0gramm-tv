package dev.pr0gramm.tv.core.designsystem

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The shape "Schnitt" painted without a clip (AC13, AC14): its outline and the focus ring's inner edge. */
class CutCornersTest {
    @Test
    fun outlineCutsTopRightAndBottomLeft() {
        assertArrayEquals(
            floatArrayOf(0f, 0f, 340f, 0f, 360f, 20f, 360f, 202f, 20f, 202f, 0f, 182f),
            CutCorners.outline(360f, 202f, 20f, 0f),
            DELTA,
        )
    }

    @Test
    fun insetMovesEveryEdgeInwardsByTheRingWidth() {
        val outer = CutCorners.outline(360f, 202f, 20f, 0f)
        val inner = CutCorners.outline(360f, 202f, 20f, 4f)
        for (edge in 0 until 6) {
            val distance = distanceToLine(inner, edge, outer, edge)
            assertEquals("edge $edge", 4f, distance, DELTA)
        }
    }

    /** Distance of the inner edge's first corner to the line through the outer edge with the same index. */
    private fun distanceToLine(inner: FloatArray, innerEdge: Int, outer: FloatArray, outerEdge: Int): Float {
        val (x, y) = corner(inner, innerEdge)
        val (ax, ay) = corner(outer, outerEdge)
        val (bx, by) = corner(outer, (outerEdge + 1) % 6)
        val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
        val length = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay))
        return abs(cross) / length
    }

    private fun corner(points: FloatArray, i: Int) = points[2 * i] to points[2 * i + 1]

    private companion object {
        const val DELTA = 0.001f
    }
}
