package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.data.savedpages.SavedPageKey

/**
 * What one browser session has seen, for "Meine Liste" (AC7): which URLs count as the start page and which
 * played as media. URLs are compared in the normalized form of [SavedPageKey.normalize].
 *
 * [start] is the site's configured URL. [fromStart]: the session opened the start page (not a saved entry);
 * then the first web page that loads is a start page too, because sites redirect their entry URL
 * (`https://site.example/` → `https://www.site.example/de/`).
 */
class Visit(start: String, private val fromStart: Boolean) {
    private val starts = mutableSetOf(key(start))
    private val videos = mutableSetOf<String>()
    private var committed = false

    /** A page became visible (`onPageCommitVisible`). */
    fun loaded(url: String) {
        if (fromStart && !committed && isWeb(url)) {
            committed = true
            starts += key(url)
        }
    }

    /** [url] is media the app plays itself (a video offered as download): not a page to save. */
    fun played(url: String) {
        videos += key(url)
    }

    /** [url] as "Meine Liste" sees it: UNSAVABLE without a web URL or for played media, START, else NORMAL. */
    fun page(url: String?): Page {
        val key = url?.takeIf(::isWeb)?.let(::key)
        val kind = when {
            key == null || key in videos -> Page.Kind.UNSAVABLE
            key in starts -> Page.Kind.START
            else -> Page.Kind.NORMAL
        }
        return Page(url, kind)
    }

    private companion object {
        fun key(url: String) = SavedPageKey.normalize(url)

        fun isWeb(url: String) =
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    }
}
