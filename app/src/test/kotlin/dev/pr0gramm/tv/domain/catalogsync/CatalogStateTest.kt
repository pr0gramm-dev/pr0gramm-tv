package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.data.catalogsync.catalogOf
import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import dev.pr0gramm.tv.data.manifest.ServerPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogStateTest {
    private val old = catalogOf()
    private val new = catalogOf(
        apps = listOf(CatalogApp("org.kodi", "Kodi", null, 1, Apk(ServerPath.of("kodi.apk")!!, "cd".repeat(32), 1))),
    )

    private val absent = listOf(CatalogState.Loading) +
        Absence.entries.map { CatalogState.Missing(it) }

    @Test
    fun `a stored catalog is ready at once`() {
        assertEquals(CatalogState.Ready(old), initialState(old, hasServer = true))
        assertEquals(CatalogState.Ready(old), initialState(old, hasServer = false))
    }

    @Test
    fun `without a stored catalog and a server the catalog is missing`() {
        assertEquals(CatalogState.Missing(Absence.NO_SERVER), initialState(null, hasServer = false))
    }

    @Test
    fun `without a stored catalog but with a server the catalog is loading`() {
        assertEquals(CatalogState.Loading, initialState(null, hasServer = true))
    }

    @Test
    fun `a current result makes its catalog ready from every state`() {
        for (state in absent + CatalogState.Ready(old)) {
            assertEquals(CatalogState.Ready(new), state.after(SyncResult.Unchanged(new)))
            assertEquals(CatalogState.Ready(new), state.after(SyncResult.Switched(new)))
        }
    }

    private val rejected = SyncResult.Rejected(listOf(ManifestProblem("", ManifestProblem.Kind.ROOT_NOT_OBJECT)))

    @Test
    fun `a failed refresh keeps a ready catalog visible`() {
        val ready = CatalogState.Ready(old)
        assertEquals(ready, ready.after(SyncResult.Unreachable))
        assertEquals(ready, ready.after(rejected))
    }

    @Test
    fun `a failed refresh without a catalog names the reason`() {
        for (state in absent) {
            assertEquals(CatalogState.Missing(Absence.UNREACHABLE), state.after(SyncResult.Unreachable))
            assertEquals(CatalogState.Missing(Absence.REJECTED), state.after(rejected))
        }
    }

    @Test
    fun `only a ready state has a catalog`() {
        assertEquals(old, CatalogState.Ready(old).catalog)
        for (state in absent) assertNull(state.catalog)
    }
}
