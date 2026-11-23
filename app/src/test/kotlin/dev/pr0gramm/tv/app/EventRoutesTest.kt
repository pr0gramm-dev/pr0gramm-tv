package dev.pr0gramm.tv.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.HttpFailure
import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.data.catalogsync.ServerAddress
import dev.pr0gramm.tv.data.catalogsync.ServerClient
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
import dev.pr0gramm.tv.data.releases.GithubReleases
import dev.pr0gramm.tv.data.savedpages.CoverSource
import dev.pr0gramm.tv.data.savedpages.PageMeta
import dev.pr0gramm.tv.data.savedpages.SavedPageStore
import dev.pr0gramm.tv.data.savedpages.Shelf
import dev.pr0gramm.tv.domain.appinstall.AppInstaller
import dev.pr0gramm.tv.domain.savedpages.SavedShelf
import dev.pr0gramm.tv.domain.savedpages.Visit
import dev.pr0gramm.tv.domain.selfupdate.SelfUpdate
import dev.pr0gramm.tv.domain.selfupdate.SelfUpdateReady
import dev.pr0gramm.tv.domain.selfupdate.UpdateStatus
import dev.pr0gramm.tv.ui.browser.BrowserEventSender
import dev.pr0gramm.tv.ui.browser.VideoWatched
import dev.pr0gramm.tv.ui.launcher.LauncherEventSender
import dev.pr0gramm.tv.ui.launcher.OpenedFromHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/**
 * Each route with the real sender and the real receiver; HTTP (catalog server and GitHub) and Android are the only
 * fakes. E1 is the install of
 * an app, E2 the checked own update going into the install queue, E3 the end of the own install going back to the
 * self-update, E5 a video watched in the browser going to "Weiterschauen", E6 a site or app opened from the start
 * page going to "zuletzt geöffnet".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventRoutesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private companion object {
        /** The clock of "zuletzt geöffnet" in these tests. */
        const val OPENED_AT = 42L
    }

    private val apk = "apk bytes ".repeat(100).toByteArray()
    private val kodiApk = "kodi bytes ".repeat(50).toByteArray()
    private val kodi = CatalogApp(
        "org.kodi",
        "Kodi",
        null,
        3,
        Apk(ServerPath.of("kodi.apk")!!, sha(kodiApk), kodiApk.size.toLong()),
    )

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val api = URI("https://api.github.com/repos/owner/repo/releases/latest")
    private val selfApk = URI("https://github.com/owner/repo/releases/download/v0.0.6/pr0gramm-tv.apk")

    /** The latest GitHub release: version 0.0.6 (versionCode 6) with its asset at [selfApk]. */
    private fun release() = """
        {"tag_name": "v0.0.6", "assets": [{"name": "pr0gramm-tv.apk", "size": ${apk.size},
         "digest": "sha256:${sha(apk)}", "browser_download_url": "$selfApk"}]}
    """.trimIndent()

    private class FakeHttp(private val responses: Map<URI, ByteArray>) : Http {
        val requests = mutableListOf<URI>()

        override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T {
            requests += url
            return read((responses[url] ?: throw HttpFailure(404)).inputStream())
        }
    }

    /** Android as far as the routes see it: an install puts the app's versionCode on the device. */
    private class FakeDevice(private val result: Failure? = null) : PackageInstallerPort {
        val versions = mutableMapOf<String, Long>()
        val installed = mutableListOf<ByteArray>()
        override val packageName = "dev.pr0gramm.tv.app"
        override val versionCode = 5L
        override val updatable = true
        override val mayInstall = true

        override suspend fun install(apk: File, pkg: String): Failure? {
            installed += apk.readBytes()
            versions[pkg] = 3
            return result
        }

        override fun installedVersion(pkg: String): Long? = versions[pkg]
    }

    private val site = Site(
        id = SiteId("a"),
        name = "Alpha",
        url = "https://a.example/",
        description = null,
        category = null,
        tags = emptyList(),
        color = null,
        allowInsecureContent = false,
        allow = emptySet(),
        defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false),
        banner = null,
    )

    private class NoCovers : CoverSource {
        override suspend fun fetch(imageUrl: String, pageUrl: String): ByteArray? = null
    }

    private val store = URI("https://tv.example/store/manifest.json")
    private val files = mapOf(
        api to release().toByteArray(),
        selfApk to apk,
        store.resolve("kodi.apk") to kodiApk,
    )

    /** The app's slices that the install routes connect, built as [AppContainer] builds them, in [scope]. */
    private inner class Setup(val http: FakeHttp, val device: FakeDevice, scope: CoroutineScope) {
        val server = ServerClient(ServerAddress.parse(store.toString())!!, http) { "en" }
        val installs = AppInstaller(server, device, scope) { tmp.newFile() }
        val installedApps = InstalledApps(device, flowOf(listOf(kodi.pkg)), scope)
        val updates = SelfUpdate.create(GithubReleases("owner/repo", http), device, scope, clock = { 0L })
        val continueWatching = SavedShelf(
            SavedPageStore.create(
                tmp.root.resolve("watching.json"),
                tmp.root.resolve("watching"),
                Shelf.CONTINUE_WATCHING,
                NoCovers(),
                scope,
            ),
            { setOf(site.id) },
        )
        val recentlyOpened = RecentlyOpened(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { tmp.root.resolve("recent.preferences_pb") },
            ),
            clock = { OPENED_AT },
        )
        val browser = BrowserEventSender()
        val launcher = LauncherEventSender()
        val routes = EventRoutes(
            installs,
            updates,
            installedApps,
            continueWatching,
            recentlyOpened,
            browser,
            launcher,
        )
    }

    /** Builds the [Setup] and, if [started], starts its routes. */
    private fun TestScope.setup(device: FakeDevice = FakeDevice(), started: Boolean = true): Setup {
        val setup = Setup(FakeHttp(files), device, backgroundScope)
        if (started) setup.routes.start(backgroundScope)
        runCurrent()
        return setup
    }

    private suspend fun AppInstaller.settled() = queue.first { it.running == null && it.waiting.isEmpty() }

    /** The status once the check has ended (not Idle, not on the way). */
    private suspend fun SelfUpdate.finished() =
        status.first { it !is UpdateStatus.Idle && it !is UpdateStatus.Downloading }

    @Test
    fun `an ended install reads the installed versions again (E1)`() = runTest {
        val env = setup()
        assertEquals(emptyMap<String, Long>(), env.installedApps.versions.value)

        env.installs.request(kodi)
        env.installs.settled()
        runCurrent()
        assertEquals(mapOf("org.kodi" to 3L), env.installedApps.versions.value)
    }

    @Test
    fun `an install that ends before the routes start waits for them (E1)`() = runTest {
        val env = setup(started = false)
        env.installs.request(kodi)
        env.installs.settled()
        runCurrent()
        assertEquals(emptyMap<String, Long>(), env.installedApps.versions.value)

        env.routes.start(backgroundScope)
        runCurrent()
        assertEquals(mapOf("org.kodi" to 3L), env.installedApps.versions.value)
    }

    @Test
    fun `a video watched in fullscreen puts its page in Weiterschauen (E5)`() = runTest {
        val env = setup()
        val page = Visit(site.url, fromStart = false).page("https://a.example/watch/1")
        env.browser.publish(VideoWatched(site, page, PageMeta.parse("""{"titles":["Folge 1"]}""")))
        runCurrent()
        env.continueWatching.entries.first().single().let {
            assertEquals("https://a.example/watch/1", it.url)
            assertEquals("Folge 1", it.title)
        }
    }

    @Test
    fun `a site or an app opened from the start page counts as used (E6)`() = runTest {
        val env = setup()
        env.launcher.publish(OpenedFromHome(OpenedKey.Site(site.id)))
        env.launcher.publish(OpenedFromHome(OpenedKey.App(kodi.pkg)))
        runCurrent()
        assertEquals(
            mapOf(OpenedKey.Site(site.id) to OPENED_AT, OpenedKey.App(kodi.pkg) to OPENED_AT),
            env.recentlyOpened.used.first { it.size == 2 },
        )
    }

    @Test
    fun `the checked apk waits until the start page is in front`() = runTest {
        val s = setup()
        s.updates.check()
        assertEquals(UpdateStatus.Ready, s.updates.finished())
        runCurrent()
        assertTrue(s.device.installed.isEmpty())
        assertEquals("dev.pr0gramm.tv.app", s.installs.queue.value.ready?.pkg)

        s.installs.foreground(true)
        s.installs.queue.first { it.ready == null && it.running == null }
        assertTrue(apk.contentEquals(s.device.installed.single()))
    }

    @Test
    fun `the checked own update goes into the install queue under the own package (E2)`() = runTest {
        val s = setup()
        val file = tmp.newFile()
        s.routes.on(SelfUpdateReady(7, file))
        val ready = s.installs.queue.value.ready
        assertEquals("dev.pr0gramm.tv.app", ready?.pkg)
        assertEquals(7L, ready?.versionCode)
        assertEquals(file, ready?.apk)
    }

    @Test
    fun `apps asked for before go first`() = runTest {
        val file = Apk(ServerPath.of("kodi.apk")!!, sha(kodiApk), kodiApk.size.toLong())
        val kodi = CatalogApp("org.kodi", "Kodi", null, 1, file)
        val s = setup()
        s.updates.check()
        s.updates.finished()
        s.installs.request(kodi)
        s.installs.foreground(true)
        s.installs.queue.first { it.ready == null && it.running == null && it.waiting.isEmpty() }
        assertEquals(listOf(kodiApk.toList(), apk.toList()), s.device.installed.map { it.toList() })
    }

    @Test
    fun `a version that failed to install is not downloaded again`() = runTest {
        val s = setup(device = FakeDevice(result = Failure.INSTALL_FAILED))
        s.installs.foreground(true)
        s.updates.check()
        s.updates.finished()
        s.installs.queue.first { it.failed.isNotEmpty() }
        s.updates.check(force = true)
        assertEquals(UpdateStatus.Failed(Failure.INSTALL_FAILED), s.updates.status.first { it is UpdateStatus.Failed })
        assertEquals(1, s.http.requests.count { it == selfApk })
        assertEquals(1, s.device.installed.size)
    }
}
