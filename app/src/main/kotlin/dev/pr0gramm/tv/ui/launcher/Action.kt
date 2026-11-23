package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.Shelf

/** What OK or a menu entry does; every action carries its target (AC8). */
sealed interface Action {
    data class Open(val target: OpenTarget) : Action

    /** [update]: the app is installed already and the manifest has a newer version. */
    data class Install(val app: CatalogApp, val update: Boolean = false) : Action

    data class Launch(val pkg: String) : Action

    data class SetFavorite(val site: Site, val on: Boolean) : Action

    data class AddToMyList(val entry: SavedPage) : Action

    data class Remove(val shelf: Shelf, val key: SavedPageKey) : Action

    data class OpenSettings(val site: Site) : Action
}
