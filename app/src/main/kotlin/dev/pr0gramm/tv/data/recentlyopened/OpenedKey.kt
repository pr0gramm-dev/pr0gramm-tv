package dev.pr0gramm.tv.data.recentlyopened

import dev.pr0gramm.tv.data.manifest.SiteId

/** What [RecentlyOpened] keeps a time for: a site or an app opened from the start page. */
sealed interface OpenedKey {
    data class Site(val id: SiteId) : OpenedKey

    data class App(val pkg: String) : OpenedKey
}
