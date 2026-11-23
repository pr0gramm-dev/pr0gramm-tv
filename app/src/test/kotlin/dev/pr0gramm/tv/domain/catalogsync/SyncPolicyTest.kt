package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.data.catalogsync.catalogOf
import dev.pr0gramm.tv.data.manifest.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules of the catalog sync: no Android, no files, no network (AC9). */
class SyncPolicyTest {
    private val cached = catalogOf()

    private fun manifest(version: Int): Manifest = cached.manifest.copy(version = version)

    @Test
    fun `without a catalog the server is asked at once`() {
        assertTrue(SyncPolicy.asksServer(null, force = false, answeredAt = 0, now = 1))
    }

    @Test
    fun `a forced refresh asks at once`() {
        assertTrue(SyncPolicy.asksServer(cached, force = true, answeredAt = 0, now = 1))
    }

    @Test
    fun `a server that never answered is asked`() {
        assertTrue(SyncPolicy.asksServer(cached, force = false, answeredAt = null, now = 1))
    }

    @Test
    fun `the server is asked again only after an hour`() {
        assertFalse(SyncPolicy.asksServer(cached, force = false, answeredAt = 0, now = HOUR - 1))
        assertTrue(SyncPolicy.asksServer(cached, force = false, answeredAt = 0, now = HOUR))
    }

    @Test
    fun `another version switches, a smaller one too`() {
        assertTrue(SyncPolicy.switches(null, manifest(1)))
        assertTrue(SyncPolicy.switches(cached, manifest(2)))
        assertTrue(SyncPolicy.switches(cached.copy(manifest = manifest(3)), manifest(2)))
    }

    @Test
    fun `the same version does not switch`() {
        assertFalse(SyncPolicy.switches(cached, manifest(cached.manifest.version)))
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
