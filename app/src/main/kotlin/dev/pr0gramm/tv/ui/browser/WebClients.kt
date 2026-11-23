package dev.pr0gramm.tv.ui.browser

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.blocklist.Blocklist
import java.io.ByteArrayInputStream

/**
 * Popup, redirect and ad protection of one browser session (AC5, AC6) around [Navigation] and [Blocklist].
 * [clock] is `elapsedRealtime`; the forward window is UI-thread state, [adBlock] is read by request threads.
 */
internal class Guard(
    private val blocklist: Blocklist?,
    private val allow: Set<String>,
    private val clock: () -> Long,
) {
    /** The site's "Werbeblocker" option in this session. */
    @Volatile var adBlock = false

    /** When the user last started a page change; 0 after any key press since. */
    private var userNavAt = 0L

    fun keyPressed() {
        userNavAt = 0L
    }

    /** A page change the user started: a click, an accepted popup, or opening a saved address. */
    fun userNavigated() {
        userNavAt = clock()
    }

    fun sinceUserNav(): Long = clock() - userNavAt

    /** [Navigation.decide]; a main-frame click that goes through opens the forward window. */
    fun decide(req: NavRequest): Nav =
        Navigation.decide(req, ::blockedNow).also {
            if (req.mainFrame && req.gesture && !it.blocked) userNavigated()
        }

    /** For the UI thread: never waits for the list. */
    fun blockedNow(host: String?): Boolean = adBlock && blocklist?.blockedNow(host, allow) == true

    /** For request threads: waits for the list up to its limit. */
    fun blocks(host: String?): Boolean = adBlock && blocklist?.blocked(host, allow) == true
}

/** An empty 403 for a blocked request, else null (load normally). */
internal fun Guard.intercept(request: WebResourceRequest): WebResourceResponse? =
    if (!blocks(request.url.host)) {
        null
    } else {
        val empty = ByteArrayInputStream(ByteArray(0))
        WebResourceResponse("text/plain", "utf-8", FORBIDDEN, "Forbidden", emptyMap(), empty)
    }

/** What the WebView clients report to the browser screen. Called on the UI thread. */
internal interface PageHost {
    /** The user's last input went into a player embedded from another site (its clicks open ads). */
    val inEmbeddedPlayer: Boolean

    /** The page shows something (`onPageCommitVisible`). */
    fun pageShown(view: WebView, url: String)

    /** `onPageFinished`, also for error pages. */
    fun pageFinished(view: WebView)

    fun progress(percent: Int)

    /** Load an accepted popup's address in the page. */
    fun open(url: String)

    fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback)

    /** The page left fullscreen itself. */
    fun hideFullscreen()

    fun rendererGone(view: WebView)

    fun say(text: UiText)
}

/**
 * `window.tvwebapp` for the page scripts (focus-ring.js, watch-video.js). Any frame can call it, so it may do no
 * harm: [started] only hides the pointer; [focused] and [fullscreen] (a [PageFocus] ordinal) only change what
 * the arrows and OK do and whether popups from embedded players are dropped.
 */
internal class PageBridge(
    private val ui: (Runnable) -> Unit,
    private val onStarted: () -> Unit,
    private val onFocus: (PageFocus) -> Unit,
    private val onFullscreen: (PageFocus) -> Unit,
) {
    @JavascriptInterface fun started() = ui { onStarted() }

    @JavascriptInterface fun focused(kind: Int) = ui { onFocus(focusOf(kind)) }

    @JavascriptInterface fun fullscreen(kind: Int) = ui { onFullscreen(focusOf(kind)) }

    private fun focusOf(kind: Int) = PageFocus.entries.getOrElse(kind) { PageFocus.NONE }

    companion object {
        const val NAME = "tvwebapp"
    }
}

internal class PageClient(private val guard: Guard, private val host: PageHost) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        val current = view.url?.let { Uri.parse(it).host }
        val nav = guard.decide(
            NavRequest(
                scheme = url.scheme,
                host = url.host,
                mainFrame = request.isForMainFrame,
                gesture = request.hasGesture(),
                redirect = request.isRedirect,
                currentHost = current,
                msSinceUserNav = guard.sinceUserNav(),
            ),
        )
        if (request.isForMainFrame) {
            log(
                "Navigation: ${short(view.url)} -> ${short(url.toString())} " +
                    "(click=${request.hasGesture()}, server redirect=${request.isRedirect}): ${nav.log}",
            )
        }
        nav.notice?.let { host.say(text(it)) }
        return nav.blocked
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = guard.intercept(request)

    // Both: commit is early (less zoom flicker), finished catches pages that replace <html> late.
    override fun onPageCommitVisible(view: WebView, url: String) = host.pageShown(view, url)

    override fun onPageFinished(view: WebView, url: String) = host.pageFinished(view)

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()

    /** The app stays open: the screen goes back to the start page (see [PageHost.rendererGone]). */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        host.rendererGone(view)
        return true
    }
}

internal class PageChrome(private val guard: Guard, private val host: PageHost) : WebChromeClient() {
    private val ui = Handler(Looper.getMainLooper())

    /** New windows only after a click and not from embedded players; the first address loads in the page. */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        if (!Navigation.allowPopup(isUserGesture, host.inEmbeddedPlayer)) {
            log("Popup: dropped (${if (isUserGesture) "from embedded player" else "without click"})")
            return false
        }
        val catcher = WebView(view.context)
        catcher.webViewClient = PopupCatcher(guard) { url ->
            // Ads open their window as the finger touches down; the page reports that the focus went into the
            // player only after that. Decide once the report is in.
            ui.postDelayed({
                popup(url)
                catcher.destroy()
            }, POPUP_DECIDE_MS)
        }
        (resultMsg.obj as WebView.WebViewTransport).webView = catcher
        resultMsg.sendToTarget()
        return true
    }

    private fun popup(url: String) {
        when {
            host.inEmbeddedPlayer -> log("Popup: ${short(url)}: dropped (from embedded player)")
            Uri.parse(url).scheme?.lowercase() !in POPUP_SCHEMES -> log("Popup: ${short(url)}: dropped (scheme)")
            guard.blockedNow(Uri.parse(url).host) -> {
                log("Popup: ${short(url)}: ${Nav.AD.log}")
                Nav.AD.notice?.let { host.say(text(it)) }
            }
            else -> {
                log("Popup: ${short(url)}: opened in the window")
                guard.userNavigated()
                host.open(url)
            }
        }
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) = host.progress(newProgress)

    override fun onShowCustomView(view: View, callback: CustomViewCallback) = host.showFullscreen(view, callback)

    override fun onHideCustomView() = host.hideFullscreen()

    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
        callback.invoke(origin, false, false)

    // The default poster is a grey play icon; a transparent pixel looks cleaner.
    override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
}

/** The schemes a popup may open in the window. [Nav] allows more schemes for the main page (about, data, blob). */
private val POPUP_SCHEMES = setOf("http", "https")

/** Catches a popup window's first address and hands it to [take]; the window itself never shows. */
private class PopupCatcher(private val guard: Guard, private val take: (String) -> Unit) : WebViewClient() {
    private var done = false

    private fun first(url: String?) {
        if (done || url == null || url == "about:blank") return
        done = true
        take(url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        first(request.url.toString())
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = first(url)

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = guard.intercept(request)

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        view.destroy()
        return true
    }
}

/** One line in `adb logcat -s pr0gramm`: navigation and popup decisions, so a report can be replayed. */
internal fun log(line: String) {
    Log.i("pr0gramm", line)
}

private fun short(url: String?) = url?.let { if (it.length > MAX_LOG_URL) it.take(MAX_LOG_URL) + "…" else it } ?: "-"

private const val FORBIDDEN = 403
private const val POPUP_DECIDE_MS = 300L
private const val MAX_LOG_URL = 200
