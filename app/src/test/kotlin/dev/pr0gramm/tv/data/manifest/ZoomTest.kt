package dev.pr0gramm.tv.data.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomTest {
    private fun zoom(percent: Int) = Zoom.of(percent)!!

    private fun percents(default: Int) = Zoom.steps(zoom(default)).map { it.percent }

    @Test
    fun ofAcceptsTheRangeInclusive() {
        assertEquals(50, Zoom.of(50)?.percent)
        assertEquals(125, Zoom.of(125)?.percent)
        assertEquals(150, Zoom.of(150)?.percent)
    }

    @Test
    fun ofRejectsValuesOutsideTheRange() {
        assertNull(Zoom.of(49))
        assertNull(Zoom.of(151))
        assertNull(Zoom.of(0))
        assertNull(Zoom.of(-100))
    }

    @Test
    fun stepsAreTheGridWhenTheDefaultIsOnIt() {
        val grid = listOf(50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150)
        assertEquals(grid, percents(100))
        assertEquals(grid, percents(50))
        assertEquals(grid, percents(150))
    }

    @Test
    fun stepsInsertAnOffGridDefaultInOrder() {
        assertEquals(
            listOf(50, 60, 70, 80, 90, 100, 110, 120, 125, 130, 140, 150),
            percents(125),
        )
        assertEquals(
            listOf(50, 51, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150),
            percents(51),
        )
        assertEquals(
            listOf(50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 149, 150),
            percents(149),
        )
    }
}
