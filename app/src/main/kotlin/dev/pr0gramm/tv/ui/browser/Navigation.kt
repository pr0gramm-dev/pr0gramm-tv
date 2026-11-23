package dev.pr0gramm.tv.ui.browser

import androidx.annotation.StringRes
import dev.pr0gramm.tv.R

/**
 * A page change the WebView asks about (`shouldOverrideUrlLoading`), reduced to what [Navigation.decide] needs.
 * [currentHost] is the host of the page shown now, null before the first page. [msSinceUserNav] is the time
 * since the user last started a page change (a click, or a popup from a click); the caller resets that mark on
 * every key press, so any key press ends the [Navigation.FORWARD_MS] window.
 */
data class NavRequest(
    val scheme: String?,
    val host: String?,
    val mainFrame: Boolean,
    val gesture: Boolean,
    val redirect: Boolean,
    val currentHost: String?,
    val msSinceUserNav: Long,
)

/** The decision on a page change. [log] is the English text for the log; [notice], when set, shows on screen. */
enum class Nav(val blocked: Boolean, @StringRes val notice: Int?, val log: String) {
    ALLOW(false, null, "allowed"),
    FORWARD(false, null, "allowed (redirect right after a click)"),
    SCHEME(true, null, "scheme ignored"),
    AD(true, R.string.browser_ad_blocked, "ad blocked"),
    REDIRECT(true, R.string.browser_redirect_blocked, "redirect blocked"),
}

/** Popup and redirect protection (AC5): pure rules, the WebView clients only ask. */
object Navigation {
    /** How long after a page change the user started the new page may forward to another site on its own. */
    const val FORWARD_MS = 5_000L

    private val LOADABLE_SCHEMES = setOf("http", "https", "about", "data", "blob", "javascript")
    private const val COUNTRY_CODE = 2
    private const val SHORT_SECOND_LEVEL = 3
    private const val SITE_LABELS = 2
    private const val COUNTRY_SITE_LABELS = 3

    /**
     * Foreign schemes (intent:, market:, mailto: …) are dropped silently. A main-frame change to a domain
     * [adBlocked] lists is blocked (sub-frames are left to the request filter). Ad scripts send the whole page
     * to another site without a key press, so a main-frame change to another site without gesture or server
     * redirect is blocked, unless the user started a page change less than [FORWARD_MS] ago (hosters move:
     * a page opened from a link may forward by script to its current domain).
     */
    fun decide(req: NavRequest, adBlocked: (String) -> Boolean): Nav {
        val current = req.currentHost
        return when {
            req.scheme?.lowercase() !in LOADABLE_SCHEMES -> Nav.SCHEME
            req.mainFrame && req.host != null && adBlocked(req.host) -> Nav.AD
            !req.mainFrame || req.gesture || req.redirect || current == null -> Nav.ALLOW
            siteOf(req.host) == siteOf(current) -> Nav.ALLOW
            req.msSinceUserNav < FORWARD_MS -> Nav.FORWARD
            else -> Nav.REDIRECT
        }
    }

    /** New windows only after a click, and never from a player embedded from another site (its clicks open ads). */
    fun allowPopup(userGesture: Boolean, inEmbeddedPlayer: Boolean): Boolean = userGesture && !inEmbeddedPlayer

    /**
     * The site a host belongs to, roughly: its last two labels, three for short country pairs such as co.uk.
     * Without the public suffix list some different sites look the same (fewer blocks, never more).
     */
    private fun siteOf(host: String?): String {
        val labels = host.orEmpty().lowercase().trimEnd('.').split('.')
        val countryPair = labels.size >= COUNTRY_SITE_LABELS &&
            labels[labels.size - 2].length <= SHORT_SECOND_LEVEL &&
            labels.last().length == COUNTRY_CODE
        return labels.takeLast(if (countryPair) COUNTRY_SITE_LABELS else SITE_LABELS).joinToString(".")
    }
}
