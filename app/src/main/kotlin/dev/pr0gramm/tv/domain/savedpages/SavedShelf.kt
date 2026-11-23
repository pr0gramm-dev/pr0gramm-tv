package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.savedpages.Edit
import dev.pr0gramm.tv.data.savedpages.PageMeta
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.SavedPageStore
import dev.pr0gramm.tv.data.savedpages.Shelf
import kotlinx.coroutines.flow.Flow

/** The read side of a shelf: its pages, newest first, hidden ones included. */
interface ShelfView {
    /** Collecting it removes image files nobody refers to. */
    val entries: Flow<List<SavedPage>>

    /** The list as stored now; unlike [entries] it removes no files. */
    suspend fun current(): List<SavedPage>
}

/**
 * The use cases of one shelf ("Meine Liste" or "Weiterschauen") over its [store]. Entries of sites missing from the
 * current catalog ([catalogSites] gives its sites, read at each use) stay stored but hidden (AC4). A rule is checked
 * once before the slow cover download and again inside [SavedPageStore.update], against the list it stores.
 */
class SavedShelf(
    private val store: SavedPageStore,
    private val catalogSites: () -> Set<SiteId>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ShelfView {
    val shelf: Shelf get() = store.shelf

    override val entries: Flow<List<SavedPage>> get() = store.entries

    override suspend fun current(): List<SavedPage> = store.current()

    /**
     * Refuses video pages, the start page and a new page on a full list. A page already in the list moves
     * to the front. A cover that is missing, over 5 MB, not JPEG/PNG/WebP or fails to load is left out.
     */
    suspend fun save(site: Site, page: Page, meta: PageMeta): SaveResult {
        val first = SavedList.draft(visible(store.current(), site), site, page, meta, clock())
        if (first !is SaveResult.Saved) return first
        val cover = meta.image?.let { store.cover(it, first.entry.url) }
        return store.update { current, covers ->
            // The list may have changed while the cover loaded.
            val draft = SavedList.draft(visible(current, site), site, page, meta, clock())
            if (draft !is SaveResult.Saved) return@update Edit(current, draft)
            val entry = draft.entry.copy(image = cover?.let { covers.write(draft.entry.key, it) })
            Edit(SavedList.add(current, catalogSites(), entry, shelf.perSite), SaveResult.Saved(entry))
        }
    }

    /**
     * Takes [entry] of another shelf in, with a copy of its image. Refused when the list is full and the
     * entry is new.
     */
    suspend fun adopt(entry: SavedPage): SaveResult {
        val cover = entry.image?.let { store.cover(it) }
        return store.update { current, covers ->
            if (!SavedList.canAdopt(visible(current), entry)) {
                return@update Edit(current, SaveResult.Refused(Refusal.FULL))
            }
            val image = cover?.let { covers.write(entry.key, it) }
            val adopted = entry.copy(image = image, savedAt = clock())
            Edit(SavedList.add(current, catalogSites(), adopted, shelf.perSite), SaveResult.Saved(adopted))
        }
    }

    /** Also deletes the entry's image file. */
    suspend fun remove(key: SavedPageKey): SaveResult.Removed = store.update { current, _ ->
        Edit(SavedList.remove(current, key), SaveResult.Removed(current.firstOrNull { it.key == key }))
    }

    /**
     * The entries of catalog sites; with [saving] on a shelf with a cap per site, not that site's own: a new one
     * drops its oldest, so "Weiterschauen" never refuses a site that already has entries.
     */
    private fun visible(list: List<SavedPage>, saving: Site? = null): List<SavedPage> {
        val sites = catalogSites()
        val capped = shelf.perSite < SavedList.MAX
        return list.filter { it.site in sites && !(capped && it.site == saving?.id) }
    }
}
