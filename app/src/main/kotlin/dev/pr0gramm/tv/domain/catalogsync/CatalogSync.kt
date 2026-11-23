package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.catalogsync.CatalogStorage
import dev.pr0gramm.tv.data.catalogsync.ServerAnswer
import dev.pr0gramm.tv.data.catalogsync.ServerClient
import dev.pr0gramm.tv.data.manifest.Manifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * The only writer of [catalog] and, through [CatalogStorage], of its files (AC3). A manifest whose version differs
 * from the stored one (a smaller one too, to take something back) applies at once and its images follow; the same
 * version only loads missing images ([SyncPolicy.switches]). After each [refresh] the state is [after] its result:
 * a ready catalog stays on a failure, no catalog names the reason. [check] runs a refresh in its own scope, apart
 * from the self-update (AC7).
 */
class CatalogSync internal constructor(
    private val storage: CatalogStorage,
    private val server: ServerClient?,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val imageBudget: Long = IMAGE_BUDGET,
) {
    /** Without a stored manifest the state starts [CatalogState.Absent] ([initialState]). */
    private val state = MutableStateFlow(initialState(storage.stored(), hasServer = server != null))
    val catalog: StateFlow<CatalogState> = state.asStateFlow()

    private val lock = Mutex()

    /** When the server last answered; null before that. */
    private var answeredAt: Long? = null

    /** The refresh that [check] started; null before the first one. */
    private var job: Job? = null

    /**
     * Starts a [refresh] unless one that [check] started is running. Its result shows in [catalog]. Call it from one
     * thread (the main thread).
     */
    fun check(force: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch { refresh(force) }
    }

    /**
     * Asks the server at most hourly unless [force] or no catalog is there ([SyncPolicy.asksServer]); an unreachable
     * server is asked again next time. Without a server nothing changes.
     */
    internal suspend fun refresh(force: Boolean): SyncResult = lock.withLock {
        if (server == null) return@withLock SyncResult.Unreachable
        val cached = state.value.catalog
        // SyncPolicy owns the rule "no catalog: ask the server", so a catalog is there when it does not ask.
        val result = if (SyncPolicy.asksServer(cached, force, answeredAt, clock())) {
            ask(server)
        } else {
            SyncResult.Unchanged(checkNotNull(cached))
        }
        state.value = state.value.after(result)
        result
    }

    private suspend fun ask(server: ServerClient): SyncResult {
        val answer = server.manifest()
        if (answer != ServerAnswer.Unreachable) answeredAt = clock()
        return when (answer) {
            ServerAnswer.Unreachable -> SyncResult.Unreachable
            is ServerAnswer.Invalid -> SyncResult.Rejected(answer.problems)
            is ServerAnswer.Valid -> apply(server, answer.manifest, answer.text)
        }
    }

    private suspend fun apply(server: ServerClient, manifest: Manifest, text: String): SyncResult {
        val current = state.value.catalog
        // SyncPolicy owns the rule "no catalog: switch", so a catalog is there when it does not switch.
        return if (SyncPolicy.switches(current, manifest)) {
            switchTo(server, manifest, text)
        } else {
            SyncResult.Unchanged(loadImages(server, checkNotNull(current)))
        }
    }

    private suspend fun switchTo(server: ServerClient, manifest: Manifest, text: String): SyncResult {
        val switched = try {
            storage.store(manifest, text)
        } catch (_: IOException) {
            return SyncResult.Unreachable
        }
        state.value = CatalogState.Ready(switched)
        storage.dropOthers(keep = switched)
        return SyncResult.Switched(loadImages(server, switched))
    }

    /**
     * Loads every image [catalog] lacks; each one that arrives is shown at once, a failed one is tried next time.
     * Returns [catalog] with the images loaded.
     */
    private suspend fun loadImages(server: ServerClient, catalog: CachedCatalog): CachedCatalog {
        var current = catalog
        var used = catalog.present.sumOf { catalog.file(it).length() }
        for (image in catalog.manifest.images - catalog.present) {
            if (storage.fetchImage(server, catalog, image)) {
                val size = catalog.file(image).length()
                if (used + size > imageBudget) {
                    // The budget ends the loading: this image goes, and the rest waits for the next start.
                    storage.drop(catalog, image)
                    break
                }
                used += size
                current = current.copy(present = current.present + image)
                state.value = CatalogState.Ready(current)
            }
        }
        return current
    }

    companion object {
        /**
         * The catalog in [storage]; [check] refreshes it in [scope]. No [server] (no saved address): [refresh] does
         * nothing.
         */
        fun create(
            storage: CatalogStorage,
            server: ServerClient?,
            scope: CoroutineScope,
            clock: () -> Long = System::currentTimeMillis,
            imageBudget: Long = IMAGE_BUDGET,
        ): CatalogSync = CatalogSync(storage, server, scope, clock, imageBudget)

        /**
         * The most image bytes the cache keeps: 100 MiB for the images of one catalog (review item 36).
         * One image is at most 5 MiB ([ServerClient.MAX_IMAGE_BYTES]).
         */
        const val IMAGE_BUDGET = 100L * 1024 * 1024
    }
}
