package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.data.appsettings.PointerLook
import dev.pr0gramm.tv.data.blocklist.Blocklist
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import dev.pr0gramm.tv.domain.catalogsync.CatalogState
import dev.pr0gramm.tv.domain.savedpages.SavedShelf
import dev.pr0gramm.tv.domain.savedpages.ShelfView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** What a [BrowserScreen] needs from the rest of the app. [blocklist] is null when the build has none. */
class BrowserDependencies(
    val catalog: StateFlow<CatalogState>,
    val settings: SiteSettingsStore,
    val looks: Flow<PointerLook>,
    val shelves: BrowserShelves,
    val blocklist: Blocklist?,
    val sender: BrowserEventSender,
)

/**
 * The shelves as the browser sees them: [myList] ("Meine Liste") to change, [continueWatching] ("Weiterschauen")
 * only to read. The screen adds to "Weiterschauen" through [BrowserEventSender] (E5).
 */
class BrowserShelves(val myList: SavedShelf, val continueWatching: ShelfView)
