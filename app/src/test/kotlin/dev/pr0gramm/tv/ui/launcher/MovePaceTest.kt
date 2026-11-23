package dev.pr0gramm.tv.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class MovePaceTest {
    @Test
    fun `a held key moves once per scroll time`() {
        val pace = MovePace(150)
        // First press, then repeats every 50 ms for 600 ms.
        val moves = listOf(pace.allow(0, repeat = false)) + (1..12).map { pace.allow(500L + it * 50, repeat = true) }
        // At 0 ms, then at 550, 700, 850 and 1000 ms: 150 ms apart.
        assertEquals(1 + 4, moves.count { it })
    }

    @Test
    fun `single presses always move`() {
        val pace = MovePace(150)
        assertEquals(listOf(true, true, true), listOf(0L, 40L, 80L).map { pace.allow(it, repeat = false) })
    }

    @Test
    fun `the first repeat after a press waits for the scroll time`() {
        val pace = MovePace(150)
        pace.allow(1000, repeat = false)
        assertEquals(false, pace.allow(1100, repeat = true))
        assertEquals(true, pace.allow(1150, repeat = true))
    }
}
