package dev.pr0gramm.tv.ui.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollDistanceTest {
    private fun distance(offset: Float, before: Float = 0f, after: Float = 0f) =
        scrollDistance(offset, size = 200f, containerSize = 1000f, before = before, after = after)

    @Test
    fun aTileWithItsRoomInViewDoesNotScroll() {
        assertEquals(0f, distance(300f, before = 100f, after = 100f))
    }

    @Test
    fun aTileAtTheBottomScrollsUpUntilTheRoomBelowItShows() {
        // Frame fits (700..900), but the 130 px for title tag and sub line do not.
        assertEquals(0f, distance(700f))
        assertEquals(30f, distance(700f, after = 130f))
    }

    @Test
    fun aTileAtTheTopScrollsDownUntilTheRoomAboveItShows() {
        assertEquals(0f, distance(50f))
        assertEquals(-46f, distance(50f, before = 96f))
    }

    @Test
    fun aTileBiggerThanThePageWithItsRoomDoesNotScroll() {
        assertEquals(0f, scrollDistance(-10f, size = 1020f, containerSize = 1000f, before = 0f, after = 0f))
    }
}
