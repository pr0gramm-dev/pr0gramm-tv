package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.data.blocklist.Blocklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class GuardTest {
    private var now = 100_000L
    private val list = Blocklist({ ByteArrayInputStream("ads.example\n".toByteArray()) }).also {
        it.start()
        it.blocked("warm.up", emptyList()) // waits until the list is loaded
    }

    private fun guard(blocklist: Blocklist? = list, allow: Set<String> = setOf("site.example")) =
        Guard(blocklist, allow) { now }.also { it.adBlock = true }

    private fun Guard.mainFrame(host: String, gesture: Boolean = false) =
        decide(NavRequest("https", host, true, gesture, false, "site.example", sinceUserNav()))

    @Test
    fun aClickOpensAForwardWindowThatAKeyPressCloses() {
        val guard = guard()
        assertEquals(Nav.ALLOW, guard.mainFrame("other.example", gesture = true))
        now += 1_000
        assertEquals(Nav.FORWARD, guard.mainFrame("third.example"))
        guard.keyPressed()
        assertEquals(Nav.REDIRECT, guard.mainFrame("third.example"))
    }

    @Test
    fun openingAnAddressCountsAsUserNavigation() {
        val guard = guard()
        assertEquals(Nav.REDIRECT, guard.mainFrame("other.example"))
        guard.userNavigated()
        assertEquals(Nav.FORWARD, guard.mainFrame("other.example"))
        now += Navigation.FORWARD_MS
        assertEquals(Nav.REDIRECT, guard.mainFrame("other.example"))
    }

    @Test
    fun aBlockedClickOpensNoForwardWindow() {
        val guard = guard()
        assertEquals(Nav.AD, guard.mainFrame("x.ads.example", gesture = true))
        assertEquals(Nav.REDIRECT, guard.mainFrame("other.example"))
    }

    @Test
    fun adsAreBlockedOnlyWithTheSiteOptionOnAndOutsideAllow() {
        val guard = guard(allow = setOf("site.example", "ok.ads.example"))
        assertTrue(guard.blockedNow("x.ads.example"))
        assertTrue(guard.blocks("x.ads.example"))
        assertFalse(guard.blocks("ok.ads.example"))
        assertFalse(guard.blocks(null))
        guard.adBlock = false
        assertFalse(guard.blockedNow("x.ads.example"))
        assertFalse(guard.blocks("x.ads.example"))
        assertEquals(Nav.ALLOW, guard.mainFrame("site.example"))
    }

    @Test
    fun withoutBlocklistNothingIsBlocked() {
        val guard = guard(blocklist = null)
        assertFalse(guard.blocks("x.ads.example"))
        assertEquals(Nav.ALLOW, guard.mainFrame("x.ads.example", gesture = true))
    }
}
