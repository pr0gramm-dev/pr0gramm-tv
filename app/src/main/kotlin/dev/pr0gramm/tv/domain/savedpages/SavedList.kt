package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.savedpages.PageMeta
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.Shelf

/** The page the browser shows, as far as "Meine Liste" cares. */
data class Page(val url: String?, val kind: Kind) {
    enum class Kind { NORMAL, START, UNSAVABLE }
}

enum class Refusal { NOT_SAVABLE, START_PAGE, FULL }

sealed interface SaveResult {
    data class Saved(val entry: SavedPage) : SaveResult

    data class Removed(val entry: SavedPage?) : SaveResult

    data class Refused(val reason: Refusal) : SaveResult
}

/**
 * At most this many entries per site on the shelf, a new one drops the site's oldest ("Weiterschauen" keeps the
 * last 5 of each site).
 */
val Shelf.perSite: Int
    get() = when (this) {
        Shelf.MY_LIST -> SavedList.MAX
        Shelf.CONTINUE_WATCHING -> CONTINUE_WATCHING_PER_SITE
    }

private const val CONTINUE_WATCHING_PER_SITE = 5

/** How long a video plays in fullscreen before its page counts for "Weiterschauen" (E5). */
const val CONTINUE_WATCHING_AFTER_MS = 10_000L

/**
 * The pure rules of one shelf, "Meine Liste" or "Weiterschauen" (AC7): newest first, at most [MAX] entries, one
 * entry per [SavedPageKey], at most [perSite] entries per site. Entries of sites missing from the catalog are kept but
 * hidden (AC4): they do not count as full, and go first when the shelf overflows. `visible` is the set of sites the
 * catalog has.
 */
internal object SavedList {
    const val MAX = 50

    /**
     * The entry saving [page] of [site] would add to the visible [entries] (without image), or why it is refused:
     * video pages and pages without a web URL, the start page, and a new page when [MAX] entries are visible.
     * A page already in the list is never refused for being full; [add] moves it to the front.
     */
    fun draft(entries: List<SavedPage>, site: Site, page: Page, meta: PageMeta, now: Long): SaveResult {
        val url = page.url
        return when {
            url == null || page.kind == Page.Kind.UNSAVABLE -> SaveResult.Refused(Refusal.NOT_SAVABLE)
            page.kind == Page.Kind.START -> SaveResult.Refused(Refusal.START_PAGE)
            entries.size >= MAX && entries.none { it.key == SavedPageKey.of(site.id, url) } ->
                SaveResult.Refused(Refusal.FULL)
            else -> SaveResult.Saved(SavedPage(site.id, url, meta.title(site.name), meta.description(), now, null))
        }
    }

    /**
     * [entry] in front; an entry with the same key is replaced. Never longer than [MAX]: the oldest hidden entries
     * go first, then the oldest visible ones. Of [entry]'s site at most [perSite] entries stay, the newest.
     */
    fun add(
        entries: List<SavedPage>,
        visible: Set<SiteId>,
        entry: SavedPage,
        perSite: Int = MAX,
    ): List<SavedPage> {
        val rest = entries.filter { it.key != entry.key }
        val oldest = rest.filter { it.site == entry.site }.drop(perSite - 1)
        val all = listOf(entry) + rest.filter { e -> oldest.none { it === e } }
        var surplus = all.size - MAX
        if (surplus <= 0) return all
        val dropped = all.filter { it.site !in visible }.takeLast(surplus).toMutableList()
        surplus -= dropped.size
        dropped += all.filter { it.site in visible }.takeLast(surplus.coerceAtLeast(0))
        return all.filter { e -> dropped.none { it === e } }
    }

    fun remove(entries: List<SavedPage>, key: SavedPageKey): List<SavedPage> = entries.filter { it.key != key }

    /** [entry] of another shelf may join the visible [entries]: it is in them already, or they are not full. */
    fun canAdopt(entries: List<SavedPage>, entry: SavedPage): Boolean =
        entries.size < MAX || entries.any { it.key == entry.key }
}
