package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.manifest.Manifest

/** When [CatalogSync] asks the server and which manifest replaces the catalog. Pure: no Android, files or network. */
internal object SyncPolicy {
    private const val HOUR_MS = 60 * 60 * 1000L

    /**
     * True without a [cached] catalog, with [force], or when the server last answered ([answeredAt], null for never)
     * an hour or more before [now].
     */
    fun asksServer(cached: CachedCatalog?, force: Boolean, answeredAt: Long?, now: Long): Boolean =
        cached == null || force || answeredAt == null || now - answeredAt >= HOUR_MS

    /** True when [manifest] has another version than [current], a smaller one too (to take something back). */
    fun switches(current: CachedCatalog?, manifest: Manifest): Boolean = current?.manifest?.version != manifest.version
}
