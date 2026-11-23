package dev.pr0gramm.tv.ui.launcher

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.data.catalogsync.catalogOf
import dev.pr0gramm.tv.data.installedapps.InstalledApps
import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.ServerPath
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.recentlyopened.OpenedKey
import dev.pr0gramm.tv.data.recentlyopened.RecentlyOpened
import dev.pr0gramm.tv.data.savedpages.CoverSource
import dev.pr0gramm.tv.data.savedpages.PageMeta
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.SavedPageStore
import dev.pr0gramm.tv.data.savedpages.Shelf
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import dev.pr0gramm.tv.domain.appinstall.AppInstaller
import dev.pr0gramm.tv.domain.catalogsync.CatalogState
import dev.pr0gramm.tv.domain.catalogsync.catalog
import dev.pr0gramm.tv.domain.savedpages.Page
import dev.pr0gramm.tv.domain.savedpages.SavedShelf
import dev.pr0gramm.tv.domain.savedpages.Shelves
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false)
    private val a = site("a", ServerPath.of("banners/a.png"))
    private val b = site("b", ServerPath.of("banners/b.png"))
    private val kodi = CatalogApp("org.kodi", "Kodi", null, 3, Apk(ServerPath.of("kodi.apk")!!, "ab".repeat(32), 10))
    private val catalog = MutableStateFlow<CatalogState>(CatalogState.Ready(catalogOf(a, b, apps = listOf(kodi))))

    private fun site(id: String, banner: ServerPath?) = Site(
        SiteId(id), id.uppercase(), "https://$id.example/", null, null, emptyList(), null, false, emptySet(),
        defaults, banner,
    )

    private class NoCovers : CoverSource {
        override suspend fun fetch(imageUrl: String, pageUrl: String): ByteArray? = null
    }

    private class FakeDevice(override val mayInstall: Boolean) : PackageInstallerPort {
        override val packageName = "dev.pr0gramm.tv.app"
        override val versionCode = 1L
        override val updatable = true

        override fun installedVersion(pkg: String): Long? = null

        override suspend fun install(apk: File, pkg: String): Failure? = null
    }

    private class Env(
        val vm: LauncherViewModel,
        val settings: SiteSettingsStore,
        val myList: SavedShelf,
        val watching: SavedShelf,
        val recent: RecentlyOpened,
    )

    /** DataStore writes on real IO: wait in real time, so a broken rule fails after a while instead of hanging. */
    private suspend fun <T> waitFor(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(WAIT_MS) { block() } }

    private companion object {
        const val WAIT_MS = 5_000L
    }

    private fun list(name: String, dispatcher: CoroutineDispatcher, shelf: Shelf): SavedShelf {
        val store = SavedPageStore.create(
            File(tmp.root, "$name.json"),
            File(tmp.root, "images-$name"),
            shelf,
            NoCovers(),
            CoroutineScope(dispatcher),
        )
        return SavedShelf(store, { catalog.value.catalog?.ids.orEmpty() }, { 1L })
    }

    private fun TestScope.env(mayInstall: Boolean = true, clock: () -> Long = { 42L }): Env {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = SiteSettingsStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher),
                produceFile = { File(tmp.root, "s.preferences_pb") },
            ),
        )
        val myList = list("list", dispatcher, Shelf.MY_LIST)
        val watching = list("watching", dispatcher, Shelf.CONTINUE_WATCHING)
        val device = FakeDevice(mayInstall)
        val installs = AppInstaller(null, device, backgroundScope)
        val recent = RecentlyOpened(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher),
                produceFile = { File(tmp.root, "r.preferences_pb") },
            ),
            clock = clock,
        )
        val packages = InstalledApps(device, emptyFlow(), backgroundScope)
        // What was opened goes to "zuletzt geöffnet" as the app routes it (E6).
        val sender = LauncherEventSender()
        CoroutineScope(dispatcher).launch { sender.events.collect { recent.touch(it.key) } }
        val shelves = Shelves(myList, watching)
        val deps = LauncherDependencies(catalog, settings, shelves, installs, packages.versions, recent.used, sender)
        // Not Main: a DataStore read back from real IO after the test hit a reset Dispatchers.Main and failed a later
        // test on CI. Not backgroundScope either, because advanceUntilIdle() does not wait for its tasks.
        val vm = LauncherViewModel(deps, CoroutineScope(SupervisorJob() + dispatcher))
        return Env(vm, settings, myList, watching, recent)
    }

    // DataStore reads its file on real IO, which advanceUntilIdle() does not wait for: wait for the tile itself.
    private suspend fun Env.siteTile(site: Site): Tile.Site = vm.state
        .map { state -> state.rows.flatMap { it.tiles }.filterIsInstance<Tile.Site>().firstOrNull { it.site == site } }
        .filterNotNull()
        .first()

    private fun page(url: String) = Page(url, Page.Kind.NORMAL)

    private fun meta() = PageMeta.parse("""{"title":"X"}""")

    @Test
    fun withoutCatalogTheStartPageIsEmptyUntilOneArrives() = runTest {
        val ready = catalog.value
        catalog.value = CatalogState.Loading
        val env = env()
        advanceUntilIdle()
        assertEquals(HomeState.Empty, env.vm.state.value)
        catalog.value = ready
        assertEquals(TileKey.Site(a.id), env.siteTile(a).key)
    }

    @Test
    fun favoriteChangesSettingsAndRows() = runTest {
        val env = env()
        advanceUntilIdle()
        env.vm.act(Action.SetFavorite(a, true))
        advanceUntilIdle()
        assertEquals(true, env.settings.of(a).first().favorite)
        assertEquals(TileKey.Site(a.id), env.vm.state.value.rows.first { it.kind == RowKind.SITES }.tiles.first().key)
        env.vm.act(Action.SetFavorite(a, false))
        advanceUntilIdle()
        assertEquals(false, env.settings.of(a).first().favorite)
    }

    @Test
    fun removeDropsTheEntryOfItsShelf() = runTest {
        val env = env()
        advanceUntilIdle()
        env.watching.save(a, page("https://a.example/x"), meta())
        env.myList.save(a, page("https://a.example/x"), meta())
        advanceUntilIdle()
        val key = SavedPageKey.of(a.id, "https://a.example/x")
        env.vm.act(Action.Remove(Shelf.CONTINUE_WATCHING, key))
        advanceUntilIdle()
        assertEquals(false, env.watching.current().any { it.key == key })
        assertEquals(true, env.myList.current().any { it.key == key })
        env.vm.act(Action.Remove(Shelf.MY_LIST, key))
        advanceUntilIdle()
        assertEquals(false, env.myList.current().any { it.key == key })
    }

    @Test
    fun addToMyListAdoptsTheWatchedPage() = runTest {
        val env = env()
        advanceUntilIdle()
        env.watching.save(a, page("https://a.example/x"), meta())
        val watched = env.watching.entries.first().single()
        env.vm.act(Action.AddToMyList(watched))
        advanceUntilIdle()
        assertEquals(true, env.myList.current().any { it.key == watched.key })
        assertEquals(true, env.watching.current().any { it.key == watched.key })
    }

    @Test
    fun aSiteOpenedCountsAsUsedOnceTheStartPageIsLeft() = runTest {
        val env = env()
        advanceUntilIdle()
        env.vm.ok(env.siteTile(b))
        advanceUntilIdle()
        // Still in place while the start page shows: nothing jumps before the site opens.
        assertEquals(null, env.siteTile(b).usedAt)
        env.vm.left()
        val sites = waitFor {
            env.vm.state.map { s -> s.rows.first { it.kind == RowKind.SITES }.tiles }
                .first { (it.first() as Tile.Site).usedAt != null }
        }
        assertEquals(listOf(TileKey.Site(b.id), TileKey.Site(a.id)), sites.map { it.key })
        assertEquals(42L, (sites.first() as Tile.Site).usedAt)
    }

    @Test
    fun anAppLaunchedCountsAsUsedOnceTheStartPageIsLeft() = runTest {
        val env = env()
        advanceUntilIdle()
        env.vm.act(Action.Launch("org.kodi"))
        advanceUntilIdle()
        assertEquals(emptyMap<OpenedKey, Long>(), env.recent.used.first())
        env.vm.left()
        val used = waitFor { env.recent.used.first { it.isNotEmpty() } }
        assertEquals(mapOf<OpenedKey, Long>(OpenedKey.App("org.kodi") to 42L), used)
    }

    @Test
    fun leavingAgainWithoutOpeningTouchesNothing() = runTest {
        var now = 0L
        val env = env(clock = { ++now })
        advanceUntilIdle()
        env.vm.left()
        env.vm.ok(env.siteTile(a))
        advanceUntilIdle()
        env.vm.left()
        val first = waitFor { env.recent.used.first { it.isNotEmpty() } }
        assertEquals(mapOf<OpenedKey, Long>(OpenedKey.Site(a.id) to 1L), first)
        // Screen saver, TV off, Home pressed on the start page: the site was not opened again.
        env.vm.left()
        advanceUntilIdle()
        assertEquals(first, env.recent.used.first())
        assertEquals(1L, now)
    }

    @Test
    fun actionsSendEvents() = runTest {
        val env = env()
        advanceUntilIdle()
        val tile = env.siteTile(a)
        env.vm.ok(tile)
        env.vm.act(Action.OpenSettings(a))
        env.vm.act(Action.Launch("org.kodi"))
        advanceUntilIdle()
        val events = (1..3).map { env.vm.events.first() }
        assertEquals(
            listOf(
                LauncherEvent.Open(OpenTarget(a.id, null)),
                LauncherEvent.Settings(a.id),
                LauncherEvent.Launch("org.kodi"),
            ),
            events,
        )
    }

    @Test
    fun installQueuesTheAppAndShowsItWaitingOrRunning() = runTest {
        val env = env()
        advanceUntilIdle()
        env.vm.act(Action.Install(kodi))
        advanceUntilIdle()
        val app = env.vm.state
            // The state starts empty while the DataStores load on real IO: wait for the apps row.
            .mapNotNull { s -> s.rows.firstOrNull { it.kind == RowKind.APPS }?.tiles?.single() as? Tile.App }
            .first { it.state != AppState.Missing }
        // No server in this test: the install fails.
        assertEquals(AppState.Failed, app.state)
    }

    @Test
    fun installWithoutPermissionSendsAllowInstalls() = runTest {
        val env = env(mayInstall = false)
        advanceUntilIdle()
        env.vm.act(Action.Install(kodi))
        advanceUntilIdle()
        assertEquals(LauncherEvent.AllowInstalls, env.vm.events.first())
    }
}
