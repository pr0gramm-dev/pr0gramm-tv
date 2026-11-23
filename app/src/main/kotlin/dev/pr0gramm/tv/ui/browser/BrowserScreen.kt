package dev.pr0gramm.tv.ui.browser

import android.os.SystemClock
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.PrTheme
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.domain.catalogsync.catalog
import dev.pr0gramm.tv.domain.savedpages.Visit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One site in a WebView with a Compose overlay (AC5), shown in [activity]. Every key goes through [KeyRouter]; this
 * class only carries out the [KeyAction]s. The activity passes its keys and lifecycle calls in; [pass] is the
 * activity's own key handling, for keys the page does not take.
 */
class BrowserScreen private constructor(
    private val activity: ComponentActivity,
    private val site: Site,
    private val catalog: CachedCatalog,
    private val args: BrowserArgs,
    private val deps: BrowserDependencies,
    private val pass: (KeyEvent) -> Boolean,
) {
    internal val visit = Visit(site.url, fromStart = args.url == null)
    internal val guard = Guard(deps.blocklist, site.allow, SystemClock::elapsedRealtime)
    internal val scripts = Scripts(activity.assets)
    internal lateinit var root: FrameLayout
    internal lateinit var webView: WebView
    internal lateinit var pointer: PointerOverlay
    internal val notices = Notices()
    internal lateinit var fullscreen: Fullscreen
    internal lateinit var actions: PageActions
    private val host = BrowserHost(this)
    private val keys = KeyRouter(SystemClock::uptimeMillis)
    internal var options: SiteOptions? = null
    private var okPoll: Job? = null

    /** True until the WebView exists, and again once it is destroyed; lifecycle calls must skip it then. */
    internal var webViewGone = true

    /** What the page's focus is on outside fullscreen, and the player shown in fullscreen (page reports). */
    internal var focus = PageFocus.NONE
    private var fullscreenFocus = PageFocus.NONE
    internal val pageFocus get() = if (fullscreen.view != null) fullscreenFocus else focus

    internal val vm: BrowserViewModel by lazy {
        val factory = viewModelFactory {
            initializer {
                BrowserViewModel(site, deps.settings, deps.looks, deps.shelves, visit, deps.sender)
            }
        }
        ViewModelProvider(activity, factory)[BrowserViewModel::class.java]
    }
    internal val pointerMode get() = vm.state.value.pointer
    internal var splash by mutableStateOf(true)
    internal val window: Window get() = activity.window

    private fun create() {
        val address = args.url
        if (address != null) guard.userNavigated()
        guard.watchServiceWorkers()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Release builds are not debuggable, so page inspection from the Mac (chrome://inspect) is enabled here.
        WebView.setWebContentsDebuggingEnabled(true)
        webView = createWebView()
        webViewGone = false
        pointer = PointerOverlay { fullscreen.view ?: webView }
        val look = SplashLook.of(site, catalog, args.title, args.image)
        val overlay = ComposeView(activity).apply {
            // The page keeps the key focus; the overlay only draws.
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setContent {
                PrTheme {
                    val state by vm.state.collectAsState()
                    BrowserOverlay(look, splash, state, pointer, notices)
                    state.menu?.let { PageMenuPanel(it, site.banner?.let(catalog::image), onPick = ::pick) }
                }
            }
        }
        root = FrameLayout(activity).apply {
            addView(webView) // FrameLayout children fill it by default
            addView(overlay)
        }
        fullscreen = Fullscreen(
            root, webView, overlay,
            onShown = {
                vm.watch.started(webView.url)
                webView.evaluateJavascript(scripts[Script.PAGE_META]) { vm.watch.pageMeta(it) }
            },
            onLeft = { webView.evaluateJavascript(scripts[Script.PAGE_META]) { vm.watch.ended(it) } },
        )
        actions = PageActions(webView, pointer, scripts, { fullscreen.view }, { webViewGone })
        activity.setContentView(root)
        activity.bind(vm, overlay, { fullscreen.view ?: webView }, host::say) { event ->
            when (event) {
                BrowserEvent.Close -> activity.finish()
                is BrowserEvent.Pointer -> {
                    actions.setPointer(event.on)
                    notices.pointer(event.on, vm.state.value.look)
                }
            }
        }
        window.hideSystemBars()
        webView.requestFocus()
        activity.lifecycleScope.launch { start(deps.settings.of(site).first(), address ?: site.url) }
    }

    private fun createWebView() = WebView(activity).apply {
        webViewClient = PageClient(guard, host)
        webChromeClient = PageChrome(guard, host)
        val bridge = PageBridge(
            ui = activity::runOnUiThread,
            onStarted = { pointer.hide() },
            onFocus = { focus = it },
            onFullscreen = { fullscreenFocus = it },
        )
        addJavascriptInterface(bridge, PageBridge.NAME)
        setDownloadListener { url, _, _, mimeType, _ ->
            if (mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                visit.played(url)
                actions.playMedia(url)
            } else {
                host.say(text(R.string.browser_no_downloads))
            }
        }
    }

    /** The site's options are known: set the page up and load it. */
    private fun start(options: SiteOptions, url: String) {
        if (webViewGone) return
        this.options = options
        guard.adBlock = options.blocklist
        webView.configure(site, options)
        webView.loadUrl(url)
    }

    // ---- keys ----------------------------------------------------------------------------------------------

    /** Handles a key of the activity; a key the page does not take goes to [pass]. */
    fun key(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val key = PageActions.keyOf(event.keyCode)
        return when {
            webViewGone || !down && event.action != KeyEvent.ACTION_UP -> pass(event)
            key == Key.BACK && event.isCanceled -> true
            else -> {
                if (down) guard.keyPressed()
                if (key == Key.OK) pollOk(event)
                val menuOpen = vm.state.value.menu != null
                val mode = Mode(fullscreen.view != null, pointerMode, menuOpen, webView.canGoBack(), pageFocus)
                perform(keys.on(key, down, mode), event)
            }
        }
    }

    /** While OK is held the router is asked every [POLL_MS] whether the hold switches the pointer. */
    private fun pollOk(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_UP) {
            okPoll?.cancel()
            okPoll = null
        } else if (okPoll == null) {
            val press = KeyEvent(event)
            okPoll = activity.lifecycleScope.launch {
                while (true) {
                    delay(POLL_MS)
                    keys.poll()?.let { perform(it, press) }
                }
            }
        }
    }

    private fun perform(action: KeyAction, event: KeyEvent): Boolean {
        when (action) {
            KeyAction.Pass -> return pass(event)
            KeyAction.Consume -> Unit
            KeyAction.OpenMenu -> vm.openMenu(OpenPage(webView.url, webView.title))
            KeyAction.CloseMenu -> vm.dismissMenu()
            KeyAction.TogglePointer -> vm.togglePointer()
            KeyAction.ExitFullscreen -> fullscreen.leave(notifyPage = true)
            KeyAction.HistoryBack -> webView.goBack()
            KeyAction.Close -> activity.finish()
            else -> actions.run(action, event)
        }
        return true
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    /** Called after the activity's own `onPause`; [finishing] is its `isFinishing`. */
    fun paused(finishing: Boolean) {
        if (webViewGone) return
        CookieManager.getInstance().flush()
        if (finishing) {
            release()
            return
        }
        webView.evaluateJavascript(scripts[Script.PAUSE_MEDIA], null)
        webView.onPause()
        webView.pauseTimers()
    }

    /** Called after the activity's own `onResume`. */
    fun resumed() {
        if (webViewGone) return
        webView.resumeTimers()
        webView.onResume()
        window.hideSystemBars()
    }

    /** Called before the activity's own `onDestroy`. */
    fun destroyed() {
        if (!webViewGone) release()
    }

    /** Frees the page and its renderer at once; a TV has little memory for a renderer nobody sees. */
    private fun release() {
        webViewGone = true
        webView.webViewRenderProcess?.terminate()
        root.removeView(webView)
        webView.destroy()
    }

    companion object {
        private const val POLL_MS = 50L

        /**
         * Builds the screen of [args] in [activity] and loads its page. Null when the catalog does not know the site;
         * the activity closes then.
         */
        fun open(
            activity: ComponentActivity,
            args: BrowserArgs,
            deps: BrowserDependencies,
            pass: (KeyEvent) -> Boolean,
        ): BrowserScreen? {
            val catalog = deps.catalog.value.catalog
            val site = catalog?.get(args.site)
            if (catalog == null || site == null) return null
            return BrowserScreen(activity, site, catalog, args, deps, pass).also { it.create() }
        }
    }
}
