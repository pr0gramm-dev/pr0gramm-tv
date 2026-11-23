package dev.pr0gramm.tv.core.remotekeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OkPressTest {
    private var now = 0L
    private val press = OkPress(clock = { now })

    @Test
    fun releaseBeforeHoldTimeIsATap() {
        press.down()
        now = 599
        assertFalse(press.poll())
        assertEquals(OkPress.Press.TAP, press.up())
    }

    @Test
    fun holdFiresExactlyOnceAndReleaseIsNotATap() {
        press.down()
        now = 600
        assertTrue(press.poll())
        assertFalse(press.poll())
        now = 900
        assertFalse(press.poll())
        assertEquals(OkPress.Press.NONE, press.up())
    }

    @Test
    fun repeatedDownsDoNotRestartTheTimer() {
        press.down()
        now = 400
        press.down()
        now = 600
        assertTrue(press.poll())
    }

    @Test
    fun upWithoutDownIsNothing() {
        assertEquals(OkPress.Press.NONE, press.up())
    }

    @Test
    fun nextPressStartsFresh() {
        press.down()
        now = 700
        assertTrue(press.poll())
        press.up()
        now = 1000
        press.down()
        now = 1100
        assertFalse(press.poll())
        assertEquals(OkPress.Press.TAP, press.up())
    }

    @Test
    fun cancelEndsAPressWhoseReleaseWentElsewhere() {
        press.down()
        now = 700
        assertTrue(press.poll())
        press.cancel()
        now = 1000
        press.down()
        now = 1100
        assertEquals(OkPress.Press.TAP, press.up())
    }

    @Test
    fun pollWithoutDownIsFalse() {
        now = 5000
        assertFalse(press.poll())
    }
}
