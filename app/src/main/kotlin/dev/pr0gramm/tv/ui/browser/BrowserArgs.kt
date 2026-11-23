package dev.pr0gramm.tv.ui.browser

import android.content.Intent
import dev.pr0gramm.tv.data.manifest.SiteId
import java.io.File

/**
 * What the browser opens: [site] at [url] (its start page when null); [title] and [image] are an entry's, for the
 * splash. The one place that knows the extras of the browser's intent.
 */
data class BrowserArgs(val site: SiteId, val url: String?, val title: String? = null, val image: File? = null) {
    /** Writes the extras into [intent] and returns it. */
    fun put(intent: Intent): Intent = intent
        .putExtra(EXTRA_SITE, site.value)
        .putExtra(EXTRA_URL, url)
        .putExtra(EXTRA_TITLE, title)
        .putExtra(EXTRA_IMAGE, image?.path)

    companion object {
        private const val EXTRA_SITE = "site"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_IMAGE = "image"

        /** Null when [intent] names no site. */
        fun read(intent: Intent): BrowserArgs? = intent.getStringExtra(EXTRA_SITE)?.let {
            BrowserArgs(
                site = SiteId(it),
                url = intent.getStringExtra(EXTRA_URL),
                title = intent.getStringExtra(EXTRA_TITLE),
                image = intent.getStringExtra(EXTRA_IMAGE)?.let(::File),
            )
        }
    }
}
