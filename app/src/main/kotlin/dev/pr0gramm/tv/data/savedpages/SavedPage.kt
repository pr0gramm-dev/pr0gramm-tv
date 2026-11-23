package dev.pr0gramm.tv.data.savedpages

import dev.pr0gramm.tv.data.manifest.SiteId
import java.io.File

/** Identity of a saved page: the site plus its normalized URL. Two URLs with the same key are one entry. */
@JvmInline
value class SavedPageKey private constructor(val value: String) {
    companion object {
        fun of(site: SiteId, url: String): SavedPageKey = SavedPageKey("${site.value} ${normalize(url)}")

        /**
         * Drops a plain `#fragment` (keeps `#/` and `#!` routes of single-page apps) and the trailing `/` of
         * the path, so `https://a.example/x/#top` and `https://a.example/x` are the same page. The browser uses it
         * to compare pages of one visit.
         */
        fun normalize(url: String): String {
            val hash = url.indexOf('#')
            val route = hash >= 0 && (url.startsWith("#/", hash) || url.startsWith("#!", hash))
            val page = if (hash >= 0 && !route) url.substring(0, hash) else url
            val pathEnd = page.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) page.length else it }
            val scheme = page.indexOf("://")
            val hostStart = if (scheme >= 0) scheme + "://".length else 0
            return if (pathEnd > hostStart && page[pathEnd - 1] == '/') {
                page.removeRange(pathEnd - 1, pathEnd)
            } else {
                page
            }
        }
    }
}

/** One saved page of a [Shelf]. [image] is the stored cover, null when there is none. */
data class SavedPage(
    val site: SiteId,
    val url: String,
    val title: String,
    val description: String?,
    val savedAt: Long,
    val image: File?,
) {
    val key: SavedPageKey get() = SavedPageKey.of(site, url)
}
