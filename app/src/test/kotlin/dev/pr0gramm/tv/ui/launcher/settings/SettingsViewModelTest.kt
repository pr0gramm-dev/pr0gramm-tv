package dev.pr0gramm.tv.ui.launcher.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val id = SiteId("a")
    private val defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false)
    private val site = Site(
        id = id,
        name = "Alpha",
        url = "https://a.example/",
        description = null,
        category = null,
        tags = emptyList(),
        color = null,
        allowInsecureContent = false,
        allow = emptySet(),
        defaults = defaults,
        banner = null,
    )

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.store(): DataStore<Preferences> {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            produceFile = { File(tmp.root, "s.preferences_pb") },
        )
    }

    /** The value of the row [setting] in German; [SettingsFormTest] checks the English texts. */
    private fun value(vm: SettingsViewModel, setting: Setting) =
        Strings.de(vm.state.value.rows.first { it.setting == setting }.value)

    @Test
    fun startsWithTheDefaults() = runTest {
        val vm = SettingsViewModel(site, SiteSettingsStore(store()), hasBlocklist = true)
        assertEquals("100 %", value(vm, Setting.ZOOM))
        assertEquals("Alpha", vm.state.value.title)
    }

    @Test
    fun stepChangesTheStateAndSurvivesANewViewModel() = runTest {
        val settings = SiteSettingsStore(store())
        val vm = SettingsViewModel(site, settings, hasBlocklist = true)
        vm.step(Setting.ZOOM)
        vm.step(Setting.FAVORITE)
        advanceUntilIdle()
        assertEquals("110 %", value(vm, Setting.ZOOM))
        assertEquals("Ja", value(vm, Setting.FAVORITE))
        assertEquals(Zoom.of(110), settings.of(site).first().zoom)
        val again = SettingsViewModel(site, settings, hasBlocklist = true)
        advanceUntilIdle()
        assertEquals("110 %", value(again, Setting.ZOOM))
    }

    @Test
    fun stepBackwardGoesDown() = runTest {
        val vm = SettingsViewModel(site, SiteSettingsStore(store()), hasBlocklist = true)
        vm.step(Setting.ZOOM, -1)
        advanceUntilIdle()
        assertEquals("90 %", value(vm, Setting.ZOOM))
    }

    @Test
    fun resetRestoresAllDefaults() = runTest {
        val vm = SettingsViewModel(site, SiteSettingsStore(store()), hasBlocklist = true)
        vm.step(Setting.ZOOM)
        vm.step(Setting.FAVORITE)
        advanceUntilIdle()
        vm.reset()
        advanceUntilIdle()
        assertEquals("100 %", value(vm, Setting.ZOOM))
        assertEquals("Nein", value(vm, Setting.FAVORITE))
    }

    @Test
    fun noAdblockRowWithoutBlocklist() = runTest {
        val vm = SettingsViewModel(site, SiteSettingsStore(store()), hasBlocklist = false)
        advanceUntilIdle()
        assertFalse(vm.state.value.rows.any { it.setting == Setting.BLOCKLIST })
    }
}
