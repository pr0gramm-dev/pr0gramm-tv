package dev.pr0gramm.tv.data.recentlyopened

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.pr0gramm.tv.data.manifest.SiteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The stored names `site:<id>` and `app:<pkg>` are on the TVs already: they must never change (AC6b). */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyOpenedTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.store(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        produceFile = { File(tmp.root, "recent.preferences_pb") },
    )

    private suspend fun DataStore<Preferences>.stored() =
        data.first().asMap().entries.associate { (key, value) -> key.name to value }

    @Test
    fun storedNamesAreReadBack() = runTest {
        val store = store()
        store.edit {
            it[longPreferencesKey("site:a")] = 3L
            it[longPreferencesKey("app:org.kodi")] = 7L
            it[longPreferencesKey("other")] = 9L
            it[stringPreferencesKey("site:b")] = "not a time"
        }
        assertEquals(
            mapOf(OpenedKey.Site(SiteId("a")) to 3L, OpenedKey.App("org.kodi") to 7L),
            RecentlyOpened(store).used.first(),
        )
    }

    @Test
    fun touchWritesTheStoredNames() = runTest {
        val store = store()
        val recent = RecentlyOpened(store, clock = { 42L })
        recent.touch(OpenedKey.Site(SiteId("a")))
        recent.touch(OpenedKey.App("org.kodi"))
        assertEquals(mapOf("site:a" to 42L, "app:org.kodi" to 42L), store.stored())
    }

    @Test
    fun aServersUsesLiveInItsFolder() = runTest {
        val dir = File(tmp.root, "servers/0123456789abcdef")
        RecentlyOpened.forServer(dir, CoroutineScope(StandardTestDispatcher(testScheduler)))
            .touch(OpenedKey.App("org.kodi"))
        assertTrue(File(dir, "datastore/recently-opened.preferences_pb").isFile)
    }
}
