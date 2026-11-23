package dev.pr0gramm.tv.ui.browser

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text

/**
 * The browser screen's side of the WebView clients: page, progress and fullscreen reports, and the visit it
 * records. Called on the UI thread; reads and changes the state of [screen].
 */
internal class BrowserHost(private val screen: BrowserScreen) : PageHost {
    override val inEmbeddedPlayer get() = screen.pageFocus == PageFocus.FRAME

    override fun pageShown(view: WebView, url: String) {
        screen.visit.loaded(url)
        // A new document: its script starts at "nothing focused" and reports only changes from there.
        screen.focus = PageFocus.NONE
        if (screen.splash) {
            screen.splash = false
            screen.pointer.show() // the pointer appears with the page, not over the splash
        }
        prepare(view)
    }

    override fun pageFinished(view: WebView) {
        screen.splash = false
        screen.vm.progress(FULL)
        prepare(view)
    }

    private fun prepare(view: WebView) =
        screen.actions.prepare(view, screen.options?.zoom?.percent ?: NO_ZOOM, screen.pointerMode)

    override fun progress(percent: Int) {
        screen.vm.progress(percent)
    }

    override fun open(url: String) {
        if (!screen.webViewGone) screen.webView.loadUrl(url)
    }

    override fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (!screen.fullscreen.show(view, callback)) return
        screen.pointer.hide()
        screen.window.hideSystemBars()
    }

    override fun hideFullscreen() = screen.fullscreen.leave(notifyPage = false)

    /** The app stays open: this screen closes, the start page shows again. */
    override fun rendererGone(view: WebView) {
        if (screen.webViewGone) return
        screen.webViewGone = true
        screen.root.removeView(view)
        view.destroy()
        say(text(R.string.browser_page_crashed))
        screen.vm.rendererGone()
    }

    override fun say(text: UiText) {
        screen.notices.show(text)
    }

    private companion object {
        const val FULL = 100
    }
}

/** The video fullscreen view of the page (`onShowCustomView`): over the page, under the [overlay]. */
internal class Fullscreen(
    private val root: ViewGroup,
    private val page: WebView,
    private val overlay: View,
    private val onShown: () -> Unit,
    private val onLeft: () -> Unit,
) {
    var view: View? = null
        private set
    private var callback: WebChromeClient.CustomViewCallback? = null

    /** False when a fullscreen view is shown already; the new one is turned down. */
    fun show(view: View, callback: WebChromeClient.CustomViewCallback): Boolean {
        if (this.view != null) {
            callback.onCustomViewHidden()
            return false
        }
        this.view = view
        this.callback = callback
        root.addView(view, root.indexOfChild(overlay), ViewGroup.LayoutParams(MATCH, MATCH))
        page.visibility = View.INVISIBLE
        view.requestFocus()
        onShown()
        return true
    }

    /** [notifyPage]: the app ends fullscreen (BACK), so the page must be told. Safe to call again. */
    fun leave(notifyPage: Boolean) {
        val shown = view ?: return
        val callback = callback
        root.removeView(shown)
        view = null
        this.callback = null
        page.visibility = View.VISIBLE
        page.requestFocus()
        onLeft()
        // After clearing the state: the page may answer with onHideCustomView, which then returns early.
        if (notifyPage) callback?.onCustomViewHidden()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    }
}
