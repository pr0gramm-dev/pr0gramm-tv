package dev.pr0gramm.tv.data.savedpages

import org.json.JSONException
import org.json.JSONObject

/** What `assets/js/page-meta.js` read from a page; the rules for an entry's title and description (AC7). */
class PageMeta private constructor(
    private val titles: List<String>,
    private val documentTitle: String?,
    private val ogDescription: String?,
    private val metaDescription: String?,
    /** The resolved `og:image`, http(s) only. */
    val image: String?,
    private val heading: String?,
) {
    /**
     * The page's `<h1>` unless it only repeats [fallback] (the site name), else the shortest `og:title`,
     * else the document title, else [fallback]. Each is cut at " | " and capped at [TITLE_MAX] code points.
     */
    fun title(fallback: String): String =
        heading?.let(::cleanTitle)?.takeIf { it.isNotEmpty() && !it.equals(fallback, ignoreCase = true) }
            ?: titles.map(::cleanTitle).filter { it.isNotEmpty() }.minByOrNull { it.length }
            ?: documentTitle?.let(::cleanTitle)?.takeIf { it.isNotEmpty() }
            ?: fallback

    /** `og:description`, else `<meta name="description">`; whitespace collapsed, [DESCRIPTION_MAX] code points. */
    fun description(): String? = listOfNotNull(ogDescription, metaDescription)
        .map { cap(collapse(it), DESCRIPTION_MAX) }
        .firstOrNull { it.isNotEmpty() }

    companion object {
        const val TITLE_MAX = 80
        const val DESCRIPTION_MAX = 160

        val EMPTY: PageMeta = PageMeta(emptyList(), null, null, null, null, null)

        private val SPACE = Regex("[\\s\\u00A0]+")

        /** The JSON page-meta.js returns. Never throws: anything unreadable is [EMPTY], wrong types are skipped. */
        fun parse(json: String?): PageMeta = json?.let(::objectOrNull)?.let(::read) ?: EMPTY

        private fun objectOrNull(json: String): JSONObject? = try {
            JSONObject(json)
        } catch (_: JSONException) {
            null
        }

        private fun read(root: JSONObject): PageMeta {
            val titles = root.optJSONArray("titles")
                ?.let { array -> (0 until array.length()).mapNotNull { array.opt(it) as? String } }
                .orEmpty()
            return PageMeta(
                titles = titles,
                documentTitle = root.text("title"),
                ogDescription = root.text("ogDescription"),
                metaDescription = root.text("description"),
                image = root.text("image")?.takeIf(::isWeb),
                heading = root.text("heading"),
            )
        }

        private fun JSONObject.text(name: String): String? = opt(name) as? String

        private fun isWeb(url: String) =
            url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

        private fun collapse(s: String) = s.replace(SPACE, " ").trim()

        private fun cleanTitle(s: String) = cap(s.replace(SPACE, " ").substringBefore(" | ").trim(), TITLE_MAX)

        private fun cap(s: String, max: Int): String =
            if (s.codePointCount(0, s.length) <= max) s else s.substring(0, s.offsetByCodePoints(0, max)).trimEnd()
    }
}
