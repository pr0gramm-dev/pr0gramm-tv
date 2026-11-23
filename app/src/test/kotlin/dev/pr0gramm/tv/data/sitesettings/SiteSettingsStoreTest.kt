package dev.pr0gramm.tv.data.sitesettings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
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

@OptIn(ExperimentalCoroutinesApi::class)
class SiteSettingsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val a = SiteId("a")
    private val b = SiteId("b")
    private val defaultsA = options(zoom = 100)
    private val defaultsB = options(zoom = 80, control = Control.POINTER)
    private val siteA = site(a, defaultsA)
    private val siteB = site(b, defaultsB)
    private val sites = listOf(siteA, siteB)

    private fun options(zoom: Int, control: Control = Control.FOCUS) = SiteOptions(
        control = control,
        zoom = Zoom.of(zoom)!!,
        layout = Layout.NORMAL,
        blocklist = true,
        favorite = false,
    )

    private fun site(id: SiteId, defaults: SiteOptions) = Site(
        id = id,
        name = id.value,
        url = "https://${id.value}.example/",
        description = null,
        category = null,
        tags = emptyList(),
        color = null,
        allowInsecureContent = false,
        allow = emptySet(),
        defaults = defaults,
        banner = null,
    )

    private fun TestScope.store(file: File = File(tmp.root, "s.preferences_pb")): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            produceFile = { file },
        )

    private suspend fun DataStore<Preferences>.keys() = data.first().asMap().keys.map { it.name }.sorted()

    @Test
    fun withoutStoredValuesEverySiteHasItsDefaults() = runTest {
        val settings = SiteSettingsStore(store())
        assertEquals(mapOf(a to defaultsA, b to defaultsB), settings.all(sites).first())
    }

    @Test
    fun onlyDeviationsAreStored() = runTest {
        val store = store()
        val settings = SiteSettingsStore(store)
        settings.update(siteA) { it.copy(zoom = Zoom.of(120)!!, favorite = true) }
        assertEquals(listOf("a/favorite", "a/zoom"), store.keys())
        assertEquals(Zoom.of(120), settings.of(siteA).first().zoom)
        assertEquals(defaultsB, settings.of(siteB).first())
    }

    @Test
    fun valueEqualToDefaultDeletesTheKey() = runTest {
        val store = store()
        val settings = SiteSettingsStore(store)
        settings.update(siteA) { it.copy(zoom = Zoom.of(120)!!, favorite = true) }
        settings.update(siteA) { it.copy(zoom = defaultsA.zoom) }
        assertEquals(listOf("a/favorite"), store.keys())
        settings.update(siteA) { it.copy(favorite = false) }
        assertEquals(emptyList<String>(), store.keys())
    }

    @Test
    fun invalidStoredValuesAreIgnored() = runTest {
        val store = store()
        store.edit {
            it[intPreferencesKey("a/zoom")] = 999
            it[stringPreferencesKey("a/control")] = "teleport"
            it[stringPreferencesKey("a/favorite")] = "yes"
            it[intPreferencesKey("b/zoom")] = 120
        }
        val settings = SiteSettingsStore(store)
        assertEquals(mapOf(a to defaultsA, b to defaultsB.copy(zoom = Zoom.of(120)!!)), settings.all(sites).first())
    }

    @Test
    fun settingsOfRemovedSitesAreKeptAndComeBack() = runTest {
        val store = store()
        val gone = site(SiteId("gone"), defaultsA)
        store.edit {
            it[intPreferencesKey("gone/zoom")] = 120
            it[stringPreferencesKey("gone/control")] = "POINTER"
            it[intPreferencesKey("a/zoom")] = 120
        }
        val settings = SiteSettingsStore(store)
        assertEquals(setOf(a, b), settings.all(sites).first().keys)
        assertEquals(listOf("a/zoom", "gone/control", "gone/zoom"), store.keys())
        assertEquals(Zoom.of(120), settings.of(gone).first().zoom)
        assertEquals(Control.POINTER, settings.of(gone).first().control)
    }

    @Test
    fun corruptFileMeansDefaults() = runTest {
        val file = File(tmp.root, "c.preferences_pb").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)) }
        val settings = SiteSettingsStore.create(file, CoroutineScope(StandardTestDispatcher(testScheduler)))
        assertEquals(mapOf(a to defaultsA, b to defaultsB), settings.all(sites).first())
    }

    @Test
    fun aServersSettingsLiveInItsFolder() = runTest {
        val dir = File(tmp.root, "servers/0123456789abcdef")
        val settings = SiteSettingsStore.forServer(dir, CoroutineScope(StandardTestDispatcher(testScheduler)))
        settings.update(siteA) { it.copy(favorite = true) }
        assertTrue(File(dir, "datastore/site-settings.preferences_pb").isFile)
    }

    @Test
    fun resetRestoresTheDefaultsOfOneSite() = runTest {
        val store = store()
        val settings = SiteSettingsStore(store)
        settings.update(siteA) { it.copy(zoom = Zoom.of(120)!!, favorite = true, layout = Layout.DESKTOP) }
        settings.update(siteB) { it.copy(favorite = true) }
        settings.reset(siteA)
        assertEquals(listOf("b/favorite"), store.keys())
        assertEquals(defaultsA, settings.of(siteA).first())
    }
}
