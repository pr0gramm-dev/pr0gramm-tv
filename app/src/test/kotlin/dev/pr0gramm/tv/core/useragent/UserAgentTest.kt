package dev.pr0gramm.tv.core.useragent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserAgentTest {
    private val webView = "Mozilla/5.0 (Linux; Android 14; TCL 65C855 Build/UP1A.231005.007; wv) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.39 Safari/537.36"

    @Test
    fun reducedChromeKeepsOnlyTheWebViewsChromeVersion() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.6778.39 Safari/537.36",
            reducedChromeUserAgent(webView, mobile = false),
        )
    }

    @Test
    fun nothingRevealsAppOrDevice() {
        val ua = siteUserAgent(webView)
        listOf("wv", "TCL", "Build/", "Version/4.0", "Mobile").forEach { assertFalse(it, ua.contains(it)) }
    }

    @Test
    fun mobileAddsTheMobileToken() {
        assertTrue(reducedChromeUserAgent(webView, mobile = true).endsWith("Chrome/131.0.6778.39 Mobile Safari/537.36"))
    }

    @Test
    fun withoutChromeInTheDefaultAFallbackVersionIsUsed() {
        assertTrue(siteUserAgent("odd").contains("Chrome/$FALLBACK_CHROME "))
    }

    @Test
    fun siteUserAgentIsTheReducedChromeWithoutMobile() {
        assertFalse(SITE_MOBILE)
        assertEquals(reducedChromeUserAgent(webView, mobile = false), siteUserAgent(webView))
    }

    @Test
    fun siteUserAgentSaysMobileExactlyWhenTheSiteDecisionDoes() {
        assertEquals(SITE_MOBILE, siteUserAgent(webView).contains(" Mobile "))
    }

    @Test
    fun aNonDesktopSiteGetsTheSiteUserAgent() {
        assertEquals(siteUserAgent(webView), userAgentFor(webView, desktop = false))
    }

    @Test
    fun aDesktopSiteGetsTheDesktopUserAgent() {
        assertEquals(desktopUserAgent(webView), userAgentFor(webView, desktop = true))
    }

    @Test
    fun metadataBrandsNameChromeAndMajorVersion() {
        val brands = chromeBrands(webView).map { Triple(it.first, it.second, it.third) }
        assertTrue(brands.contains(Triple("Google Chrome", "131", "131.0.6778.39")))
        assertTrue(brands.contains(Triple("Chromium", "131", "131.0.6778.39")))
        assertFalse(brands.any { it.first.contains("WebView") })
    }

    @Test
    fun desktopKeepsTheChromeVersion() {
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.6778.39 Safari/537.36",
            desktopUserAgent(webView),
        )
    }
}
