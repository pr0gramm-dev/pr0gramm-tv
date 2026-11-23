package dev.pr0gramm.tv.core.useragent

/** Chrome version used when the WebView's own user agent has none. */
internal const val FALLBACK_CHROME = "120.0.0.0"

/** `Accept` of a browser's image request; the cover download sends it. */
internal const val IMAGE_ACCEPT = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"

/**
 * Whether a site's user agent and client hints say `Mobile`; decided on the TV with the catalog's pages (tablet:
 * no). The only owner of this decision: the WebView ([userAgentFor]) and the cover download ([siteUserAgent]) both
 * read it, so they keep one identity.
 */
internal const val SITE_MOBILE = false

private val CHROME_VERSION = Regex("Chrome/([\\d.]+)")

/** The Chrome version inside the WebView's [default] user agent. */
internal fun chromeVersion(default: String): String =
    CHROME_VERSION.find(default)?.groupValues?.get(1) ?: FALLBACK_CHROME

/**
 * Chrome's reduced user agent on Android: no `wv`, no device model, no `Build/…`, no `Version/4.0`. The
 * Chrome version is the WebView's. [mobile] adds the `Mobile` token (tablets leave it out).
 */
internal fun reducedChromeUserAgent(default: String, mobile: Boolean): String =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/${chromeVersion(default)} ${if (mobile) "Mobile " else ""}Safari/537.36"

/** The user agent of a site request that is not in the desktop layout: page loads and the cover download. */
internal fun siteUserAgent(default: String): String = reducedChromeUserAgent(default, SITE_MOBILE)

/** The user agent the WebView sends for a site; [desktop] is the desktop layout. */
internal fun userAgentFor(default: String, desktop: Boolean): String =
    if (desktop) desktopUserAgent(default) else siteUserAgent(default)

/** The WebView's user agent turned into desktop Chrome on Linux, keeping its Chrome version. */
internal fun desktopUserAgent(default: String): String =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/${chromeVersion(default)} Safari/537.36"

/** Brands for the client hints (`Sec-CH-UA`): brand, major version, full version. */
internal fun chromeBrands(default: String): List<Triple<String, String, String>> {
    val full = chromeVersion(default)
    val major = full.substringBefore('.')
    return listOf(
        Triple("Not.A/Brand", "24", "24.0.0.0"),
        Triple("Chromium", major, full),
        Triple("Google Chrome", major, full),
    )
}
