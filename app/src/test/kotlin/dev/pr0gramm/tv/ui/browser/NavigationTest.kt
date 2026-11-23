package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.StringTable
import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.core.text.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    private val ads = setOf("ads.example")

    /** A script on www.site.example sends the main frame to another site, long after the last click. */
    private val script = NavRequest(
        scheme = "https",
        host = "other.example",
        mainFrame = true,
        gesture = false,
        redirect = false,
        currentHost = "www.site.example",
        msSinceUserNav = Long.MAX_VALUE,
    )

    private fun decide(req: NavRequest) = Navigation.decide(req) { it in ads }

    @Test
    fun clickedLinkToAnotherSiteIsAllowed() {
        assertEquals(Nav.ALLOW, decide(script.copy(gesture = true)))
    }

    @Test
    fun serverRedirectToAnotherSiteIsAllowed() {
        assertEquals(Nav.ALLOW, decide(script.copy(redirect = true)))
    }

    @Test
    fun scriptNavigationWithinTheSameSiteIsAllowed() {
        assertEquals(Nav.ALLOW, decide(script.copy(host = "video.site.example")))
        assertEquals(Nav.ALLOW, decide(script.copy(host = "SITE.example.")))
    }

    @Test
    fun countryPairsCountAsOneSuffix() {
        val req = script.copy(host = "b.example.co.uk", currentHost = "a.example.co.uk")
        assertEquals(Nav.ALLOW, decide(req))
        assertEquals(Nav.REDIRECT, decide(req.copy(host = "other.co.uk")))
    }

    @Test
    fun subframeNavigationIsAllowed() {
        assertEquals(Nav.ALLOW, decide(script.copy(mainFrame = false)))
    }

    @Test
    fun firstPageWithoutCurrentHostIsAllowed() {
        assertEquals(Nav.ALLOW, decide(script.copy(currentHost = null)))
    }

    @Test
    fun scriptNavigationToAnotherSiteIsBlockedAsRedirect() {
        val nav = decide(script)
        assertEquals(Nav.REDIRECT, nav)
        assertTrue(nav.blocked)
        assertEquals(R.string.browser_redirect_blocked, nav.notice)
        assertEquals("redirect blocked", nav.log)
    }

    @Test
    fun forwardingRightAfterAUserNavigationIsAllowedWithinTheWindow() {
        val inside = decide(script.copy(msSinceUserNav = Navigation.FORWARD_MS - 1))
        assertEquals(Nav.FORWARD, inside)
        assertFalse(inside.blocked)
        assertEquals(Nav.REDIRECT, decide(script.copy(msSinceUserNav = Navigation.FORWARD_MS)))
    }

    @Test
    fun foreignSchemesAreIgnoredSilently() {
        for (scheme in listOf("intent", "market", "mailto", null)) {
            val nav = decide(script.copy(scheme = scheme, gesture = true))
            assertEquals(Nav.SCHEME, nav)
            assertTrue(nav.blocked)
            assertNull(nav.notice)
        }
        assertEquals(Nav.ALLOW, decide(script.copy(scheme = "HTTP", gesture = true)))
        assertEquals(Nav.ALLOW, decide(script.copy(scheme = "about", host = null, gesture = true)))
    }

    @Test
    fun mainFrameAdIsBlockedEvenAfterAClick() {
        val nav = decide(script.copy(host = "ads.example", gesture = true))
        assertEquals(Nav.AD, nav)
        assertTrue(nav.blocked)
        assertEquals(R.string.browser_ad_blocked, nav.notice)
        assertEquals("ad blocked", nav.log)
    }

    @Test
    fun logsAreEnglishAndOnlyBlocksShowANotice() {
        val logs = listOf(
            "allowed",
            "allowed (redirect right after a click)",
            "scheme ignored",
            "ad blocked",
            "redirect blocked",
        )
        assertEquals(logs, Nav.entries.map { it.log })
        assertEquals(listOf(Nav.AD, Nav.REDIRECT), Nav.entries.filter { it.notice != null })
    }

    @Test
    fun noticesInBothLanguages() {
        fun notice(nav: Nav, table: StringTable) = table(text(requireNotNull(nav.notice)))
        assertEquals("Werbung blockiert", notice(Nav.AD, Strings.de))
        assertEquals("Weiterleitung blockiert", notice(Nav.REDIRECT, Strings.de))
        assertEquals("Ad blocked", notice(Nav.AD, Strings.en))
        assertEquals("Redirect blocked", notice(Nav.REDIRECT, Strings.en))
    }

    @Test
    fun subframeAdIsLeftToTheRequestFilter() {
        assertEquals(Nav.ALLOW, decide(script.copy(host = "ads.example", mainFrame = false)))
    }

    @Test
    fun blocklistIsNotAskedForForeignSchemes() {
        var asked = false
        Navigation.decide(script.copy(scheme = "intent")) {
            asked = true
            true
        }
        assertFalse(asked)
    }

    @Test
    fun popupsNeedAClickAndMustNotComeFromAnEmbeddedPlayer() {
        assertTrue(Navigation.allowPopup(userGesture = true, inEmbeddedPlayer = false))
        assertFalse(Navigation.allowPopup(userGesture = false, inEmbeddedPlayer = false))
        assertFalse(Navigation.allowPopup(userGesture = true, inEmbeddedPlayer = true))
        assertFalse(Navigation.allowPopup(userGesture = false, inEmbeddedPlayer = true))
    }
}
