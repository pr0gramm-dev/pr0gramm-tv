package dev.pr0gramm.tv.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import dev.pr0gramm.tv.ui.browser.BrowserArgs
import dev.pr0gramm.tv.ui.browser.BrowserScreen

/**
 * The thin host of a [BrowserScreen]: it passes keys and lifecycle calls on in their order. Opened through [of]; an
 * intent without a site, or with a site the catalog does not know, closes it.
 */
class BrowserActivity : ComponentActivity() {
    private var screen: BrowserScreen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deps = (application as TvApplication).container.browser
        screen = BrowserArgs.read(intent)?.let { BrowserScreen.open(this, it, deps, ::passOn) }
        if (screen == null) finish()
    }

    // reason: lint false positive, androidx.core marks dispatchKeyEvent @RestrictTo; it is public API
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = screen?.key(event) ?: passOn(event)

    /** The activity's own key handling, for keys the page does not take. */
    @SuppressLint("RestrictedApi") // reason: the same lint false positive as on dispatchKeyEvent
    private fun passOn(event: KeyEvent): Boolean = super.dispatchKeyEvent(event)

    override fun onPause() {
        super.onPause()
        screen?.paused(isFinishing)
    }

    override fun onResume() {
        super.onResume()
        screen?.resumed()
    }

    override fun onDestroy() {
        screen?.destroyed()
        super.onDestroy()
    }

    companion object {
        /** The intent that opens [args]. */
        fun of(context: Context, args: BrowserArgs): Intent = args.put(Intent(context, BrowserActivity::class.java))
    }
}
