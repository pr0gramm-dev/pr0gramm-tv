package dev.pr0gramm.tv.domain.selfupdate

import dev.pr0gramm.tv.core.updatefailure.Failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfInstallStateTest {
    @Test
    fun `a handed-off version counts as queued until its install ends`() {
        val state = SelfInstallState().handedOff(6L)
        assertTrue(state.queued(6L))
        assertFalse(state.queued(7L))
        assertFalse(state.ended(6, failed = false).queued(6L))
    }

    @Test
    fun `a failed install skips its version`() {
        val state = SelfInstallState().handedOff(6L).ended(6, failed = true)
        assertFalse(state.queued(6L))
        assertEquals(6L to UpdateStatus.Failed(Failure.INSTALL_FAILED), state.skipped())
    }

    @Test
    fun `an install that succeeds or is cancelled clears an earlier failure`() {
        val failed = SelfInstallState().ended(6, failed = true)
        assertNull(failed.handedOff(7L).ended(7, failed = false).skipped())
    }

    @Test
    fun `the end of an older version keeps a newer hand-off`() {
        val state = SelfInstallState().handedOff(6L).handedOff(7L).ended(6, failed = true)
        assertTrue(state.queued(7L))
        assertEquals(6L to UpdateStatus.Failed(Failure.INSTALL_FAILED), state.skipped())
    }

    @Test
    fun `nothing handed off and nothing failed is a fresh state`() {
        val state = SelfInstallState()
        assertFalse(state.queued(6L))
        assertNull(state.skipped())
    }
}
