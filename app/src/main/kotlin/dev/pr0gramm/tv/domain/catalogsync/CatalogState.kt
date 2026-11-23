package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.data.catalogsync.CachedCatalog

/** What the app can show: a [Ready] catalog, or none yet ([Absent]). */
sealed interface CatalogState {
    data class Ready(val catalog: CachedCatalog) : CatalogState

    /** No catalog to show. */
    sealed interface Absent : CatalogState

    /** The first refresh still runs. */
    data object Loading : Absent

    /** The last refresh brought no catalog, for [reason]. */
    data class Missing(val reason: Absence) : Absent
}

/** Why no catalog is there. */
enum class Absence { NO_SERVER, UNREACHABLE, REJECTED }

/** The catalog of a [CatalogState.Ready] state, null otherwise. */
val CatalogState.catalog: CachedCatalog?
    get() = (this as? CatalogState.Ready)?.catalog

/** The state at start: the [stored] catalog, else loading from the server, else missing for want of a server. */
internal fun initialState(stored: CachedCatalog?, hasServer: Boolean): CatalogState = when {
    stored != null -> CatalogState.Ready(stored)
    !hasServer -> CatalogState.Missing(Absence.NO_SERVER)
    else -> CatalogState.Loading
}

/** The state after [result]: a failed refresh keeps a ready catalog visible and names its reason otherwise. */
internal fun CatalogState.after(result: SyncResult): CatalogState = when (result) {
    is SyncResult.Current -> CatalogState.Ready(result.catalog)
    SyncResult.Unreachable -> failed(Absence.UNREACHABLE)
    is SyncResult.Rejected -> failed(Absence.REJECTED)
}

private fun CatalogState.failed(reason: Absence): CatalogState = when (this) {
    is CatalogState.Ready -> this
    is CatalogState.Absent -> CatalogState.Missing(reason)
}
