package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.manifest.ManifestProblem

/** How a [CatalogSync.refresh] ended. */
sealed interface SyncResult {
    /** The server answered or was asked less than an hour ago; [catalog] is the current one. */
    sealed interface Current : SyncResult {
        val catalog: CachedCatalog
    }

    /** Same version as stored, or checked less than an hour ago. Missing images may have been loaded. */
    data class Unchanged(override val catalog: CachedCatalog) : Current

    data class Switched(override val catalog: CachedCatalog) : Current

    /** No server, no network, an HTTP error or a local write error: the last state stays. */
    data object Unreachable : SyncResult

    /** The manifest is invalid with [problems] (never empty); the last state stays. */
    data class Rejected(val problems: List<ManifestProblem>) : SyncResult {
        init {
            require(problems.isNotEmpty()) { "a rejected manifest names at least one problem" }
        }
    }
}
