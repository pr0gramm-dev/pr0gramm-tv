package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.Shelf
import java.io.File

/**
 * The site to open and the page in it; [url] is null for the site's start URL. For an entry, [title] (its own title)
 * and [image] (its picture) are what the splash shows while the page loads; a site shows its banner.
 */
data class OpenTarget(val site: SiteId, val url: String?, val title: String? = null, val image: File? = null)

/** One-shot results of tile actions, handled by the activity. */
sealed interface LauncherEvent {
    data class Open(val target: OpenTarget) : LauncherEvent

    data class Settings(val site: SiteId) : LauncherEvent

    /** Start the installed app [pkg]. */
    data class Launch(val pkg: String) : LauncherEvent

    /** Open the Android setting that lets this app install packages. */
    data object AllowInstalls : LauncherEvent
}

/** Identity of a tile. */
sealed interface TileKey {
    /**
     * Key for lazy lists and the saved focus: both keep it in a Bundle, which only takes plain types. Not stored on
     * disk (`RecentlyOpened` has its own names), so its format may change.
     */
    val listKey: String

    data class Site(val id: SiteId) : TileKey {
        override val listKey: String get() = "site:${id.value}"
    }

    data class Saved(val shelf: Shelf, val key: SavedPageKey) : TileKey {
        override val listKey: String get() = "${shelf.dir}:${key.value}"
    }

    data class App(val pkg: String) : TileKey {
        override val listKey: String get() = "app:$pkg"
    }
}

/** Where an app of the manifest stands on this device (AC9). */
sealed interface AppState {
    data object Missing : AppState

    data object Waiting : AppState

    data class Running(val percent: Int) : AppState

    data object Failed : AppState

    data object Installed : AppState

    /** Installed, and the manifest has a newer versionCode: the tile shows the "Update" badge. */
    data object Update : AppState
}

sealed interface Tile {
    val key: TileKey

    /** [usedAt]: when it was last opened from the start page, null if never. */
    data class Site(
        val site: dev.pr0gramm.tv.data.manifest.Site,
        val options: SiteOptions,
        val usedAt: Long? = null,
    ) : Tile {
        override val key: TileKey get() = TileKey.Site(site.id)
    }

    data class Saved(val shelf: Shelf, val entry: SavedPage, val site: dev.pr0gramm.tv.data.manifest.Site) : Tile {
        override val key: TileKey get() = TileKey.Saved(shelf, entry.key)
    }

    /** [usedAt]: when it was last started from the start page, null if never. */
    data class App(val entry: CatalogApp, val state: AppState, val usedAt: Long? = null) : Tile {
        override val key: TileKey get() = TileKey.App(entry.pkg)
    }
}
