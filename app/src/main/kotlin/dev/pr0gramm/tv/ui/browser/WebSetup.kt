package dev.pr0gramm.tv.ui.browser

import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.ServiceWorkerController
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.pr0gramm.tv.core.useragent.SITE_MOBILE
import dev.pr0gramm.tv.core.useragent.chromeBrands
import dev.pr0gramm.tv.core.useragent.chromeVersion
import dev.pr0gramm.tv.core.useragent.userAgentFor
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteOptions

/** Service workers fetch outside the WebView clients; their requests pass the same filter. One per process. */
internal fun Guard.watchServiceWorkers() {
    val guard = this
    ServiceWorkerController.getInstance().setServiceWorkerClient(
        object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest) = guard.intercept(request)
        },
    )
}

/** Settings for [site] with the session's [options]; before the first load. */
internal fun WebView.configure(site: Site, options: SiteOptions) {
    val desktop = options.layout == Layout.DESKTOP
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        // Else every requestFocus (the page menu closing, fullscreen ending) focuses the page's first element,
        // and the page scrolls up to it.
        setNeedInitialFocus(false)
        // Every new window reaches onCreateWindow, which decides by user gesture.
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true
        // Lets an https page load http content, scripts included, which someone on the network could tamper
        // with; only for sites that need it.
        if (site.allowInsecureContent) mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        blendIn(desktop)
        if (desktop) {
            useWideViewPort = true
            loadWithOverviewMode = true
        }
        // The zoom sets the page's viewport width (zoom-viewport.js); the WebView honours it only like this.
        if (options.zoom.percent != NO_ZOOM) useWideViewPort = true
    }
    CookieManager.getInstance().apply {
        setAcceptCookie(true)
        setAcceptThirdPartyCookies(this@configure, true)
    }
}

/**
 * Makes requests look like Chrome on Android: reduced user agent and, where the device's WebView supports them,
 * client hints that name Chrome and no model. `X-Requested-With` stays: its origin allow list ended with
 * WebView's deprecation trial, and androidx.webkit 1.18 turned the setter into a no-op.
 */
private fun WebSettings.blendIn(desktop: Boolean) {
    val default = userAgentString
    userAgentString = userAgentFor(default, desktop)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
        WebSettingsCompat.setUserAgentMetadata(this, clientHints(default, desktop))
    }
}

private fun clientHints(default: String, desktop: Boolean): UserAgentMetadata = UserAgentMetadata.Builder()
    .setBrandVersionList(
        chromeBrands(default).map { (brand, major, full) ->
            UserAgentMetadata.BrandVersion.Builder().setBrand(brand).setMajorVersion(major).setFullVersion(full).build()
        },
    )
    .setFullVersion(chromeVersion(default))
    .setPlatform(if (desktop) "Linux" else "Android")
    .setPlatformVersion(if (desktop) "" else "10.0.0")
    .setModel("")
    .setMobile(SITE_MOBILE && !desktop)
    .apply {
        // Throws on a WebView without form factors.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA_FORM_FACTORS)) {
            setFormFactors(listOf(formFactor(desktop)))
        }
    }
    .build()

private fun formFactor(desktop: Boolean) = when {
    desktop -> UserAgentMetadata.FORM_FACTOR_DESKTOP
    SITE_MOBILE -> UserAgentMetadata.FORM_FACTOR_MOBILE
    else -> UserAgentMetadata.FORM_FACTOR_TABLET
}

internal const val NO_ZOOM = 100
