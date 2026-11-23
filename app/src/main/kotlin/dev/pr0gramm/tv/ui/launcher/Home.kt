package dev.pr0gramm.tv.ui.launcher

import androidx.compose.runtime.Immutable
import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.recentlyopened.OpenedKey
import dev.pr0gramm.tv.data.recentlyopened.RecentlyOpened
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.Shelf
import dev.pr0gramm.tv.domain.appinstall.InstallQueue
import dev.pr0gramm.tv.domain.savedpages.SavedList

/** The blocks of the start page, top to bottom (AC5). */
enum class RowKind { CONTINUE_WATCHING, MY_LIST, SITES, APPS }

/** Never empty: [Home.build] drops empty rows. */
data class Row(val kind: RowKind, val tiles: List<Tile>)

/** [rows] in display order. */
@Immutable
data class HomeState(val rows: List<Row>) {
    companion object {
        val Empty = HomeState(emptyList())
    }
}

/** What the start page shows besides the catalog's sites: the two lists and the state of the apps. */
data class HomeInput(
    val options: Map<SiteId, SiteOptions>,
    val continueWatching: List<SavedPage>,
    val myList: List<SavedPage>,
    val installed: Map<String, Long>,
    val queue: InstallQueue,
    /** [RecentlyOpened.used]: when each site and app was last opened from the start page. */
    val used: Map<OpenedKey, Long>,
)

/**
 * The pure rules of the start page (AC5, AC8, AC9). Sites: favorites, then the last used, then the rest; apps: the
 * last used first (anordnung.html, variant B).
 */
object Home {
    /** Sorts unused tiles behind used ones; the sort is stable, so they keep the manifest's order. */
    private const val NEVER = Long.MIN_VALUE

    /**
     * Weiterschauen shows this many entries per site; [Shelf.CONTINUE_WATCHING] stores more, so a removed tile is
     * refilled.
     */
    private const val CONTINUE_WATCHING_SHOWN_PER_SITE = 3

    /** Entries come newest first; those of sites the catalog lacks are left out. */
    fun build(catalog: CachedCatalog, input: HomeInput): HomeState {
        val sites = catalog.sites.associateBy { it.id }
        fun saved(shelf: Shelf, entries: List<SavedPage>) =
            entries.mapNotNull { e -> sites[e.site]?.let { Tile.Saved(shelf, e, it) } }

        val siteTiles = catalog.sites.map {
            Tile.Site(it, input.options.getValue(it.id), input.used[OpenedKey.Site(it.id)])
        }
        val (favorites, others) = siteTiles.partition { it.options.favorite }
        val apps = catalog.manifest.apps.map {
            Tile.App(it, appState(it, input), input.used[OpenedKey.App(it.pkg)])
        }
        val (installed, missing) = apps.partition { it.entry.pkg in input.installed }
        val rows = listOf(
            // The newest entries of each site: the list is newest first, so the row is too.
            Row(
                RowKind.CONTINUE_WATCHING,
                saved(Shelf.CONTINUE_WATCHING, input.continueWatching).firstPerSite(CONTINUE_WATCHING_SHOWN_PER_SITE),
            ),
            Row(RowKind.MY_LIST, saved(Shelf.MY_LIST, input.myList).take(SavedList.MAX)),
            Row(RowKind.SITES, favorites + others.sortedByDescending { it.usedAt ?: NEVER }),
            Row(RowKind.APPS, (missing + installed).sortedByDescending { it.usedAt ?: NEVER }),
        ).filter { it.tiles.isNotEmpty() }
        return HomeState(rows)
    }

    /** The first [max] tiles of each site, in the given order. Stable, no sorting. */
    private fun List<Tile.Saved>.firstPerSite(max: Int): List<Tile.Saved> {
        val seen = mutableMapOf<SiteId, Int>()
        return filter { tile ->
            val n = seen.getOrDefault(tile.site.id, 0) + 1
            seen[tile.site.id] = n
            n <= max
        }
    }

    private fun appState(app: CatalogApp, input: HomeInput): AppState {
        val queue = input.queue
        val version = input.installed[app.pkg]
        return when {
            queue.running?.pkg == app.pkg -> AppState.Running(queue.percent)
            queue.waiting.any { it.pkg == app.pkg } -> AppState.Waiting
            queue.failed[app.pkg] == app.versionCode -> AppState.Failed
            version == null -> AppState.Missing
            version < app.versionCode -> AppState.Update
            else -> AppState.Installed
        }
    }

    /**
     * Whether a short OK opens the tile menu instead of [primary]: an app that is not installed (or failed) gets its
     * menu, so "Installieren" is a deliberate choice there, not the first press.
     */
    fun opensMenu(tile: Tile): Boolean =
        tile is Tile.App && (tile.state == AppState.Missing || tile.state == AppState.Failed)

    /** What OK does; null while an app waits or loads. */
    fun primary(tile: Tile): Action? = when (tile) {
        is Tile.Site -> Action.Open(OpenTarget(tile.site.id, null))
        is Tile.Saved -> Action.Open(
            OpenTarget(tile.site.id, tile.entry.url, TileText.source(tile)?.let { tile.entry.title }, tile.entry.image),
        )
        is Tile.App -> when (tile.state) {
            AppState.Waiting, is AppState.Running -> null
            AppState.Missing, AppState.Failed -> Action.Install(tile.entry)
            AppState.Installed, AppState.Update -> Action.Launch(tile.entry.pkg)
        }
    }

    /** The options menu of a tile (AC8); the first entry is [primary] where there is one. */
    fun menu(tile: Tile): List<Action> = when (tile) {
        is Tile.Site -> listOf(
            Action.Open(OpenTarget(tile.site.id, null)),
            Action.SetFavorite(tile.site, !tile.options.favorite),
            Action.OpenSettings(tile.site),
        )
        is Tile.Saved -> listOfNotNull(
            primary(tile),
            Action.AddToMyList(tile.entry).takeIf { tile.shelf == Shelf.CONTINUE_WATCHING },
            Action.Remove(tile.shelf, tile.entry.key),
        )
        is Tile.App -> when (tile.state) {
            AppState.Update -> listOf(Action.Install(tile.entry, update = true), Action.Launch(tile.entry.pkg))
            else -> listOfNotNull(primary(tile))
        }
    }
}
