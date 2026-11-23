package dev.pr0gramm.tv.app

import android.content.Context
import dev.pr0gramm.tv.BuildConfig
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.http.UrlConnectionHttp
import dev.pr0gramm.tv.core.packageinstaller.AndroidPackageInstaller
import dev.pr0gramm.tv.data.appsettings.AndroidLocaleStore
import dev.pr0gramm.tv.data.appsettings.AppLanguage
import dev.pr0gramm.tv.data.appsettings.AppSettings
import dev.pr0gramm.tv.data.blocklist.Blocklist
import dev.pr0gramm.tv.data.catalogsync.CatalogStorage
import dev.pr0gramm.tv.data.catalogsync.SavedAddress
import dev.pr0gramm.tv.data.catalogsync.ServerClient
import dev.pr0gramm.tv.data.catalogsync.serverDir
import dev.pr0gramm.tv.data.installedapps.InstalledApps
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.recentlyopened.RecentlyOpened
import dev.pr0gramm.tv.data.releases.GithubReleases
import dev.pr0gramm.tv.data.savedpages.SavedPageStore
import dev.pr0gramm.tv.data.savedpages.Shelf
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import dev.pr0gramm.tv.domain.appinstall.AppInstaller
import dev.pr0gramm.tv.domain.catalogsync.CatalogSync
import dev.pr0gramm.tv.domain.catalogsync.ServerSetup
import dev.pr0gramm.tv.domain.catalogsync.catalog
import dev.pr0gramm.tv.domain.savedpages.SavedShelf
import dev.pr0gramm.tv.domain.savedpages.Shelves
import dev.pr0gramm.tv.domain.selfupdate.SelfUpdate
import dev.pr0gramm.tv.domain.selfupdate.UpdateFiles
import dev.pr0gramm.tv.ui.browser.BrowserDependencies
import dev.pr0gramm.tv.ui.browser.BrowserEventSender
import dev.pr0gramm.tv.ui.browser.BrowserShelves
import dev.pr0gramm.tv.ui.launcher.LauncherDependencies
import dev.pr0gramm.tv.ui.launcher.LauncherEventSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Manual dependency injection: one instance per process, created by [TvApplication]. It builds every slice and
 * connects their events through [EventRoutes] in its own process scope.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    private val http = UrlConnectionHttp()

    /**
     * The language of the app texts (`format_locale`, `en` or `de`) for `Accept-Language` to the server (AC16). Read
     * per request: a language switch keeps the process.
     */
    private val textLanguage: () -> String = { app.getString(R.string.format_locale) }

    /** The server address the user saved; http and https in every build. */
    val serverSetup: ServerSetup = ServerSetup.create(SavedAddress.create(app), http, textLanguage)

    /** Null while no address is saved: no catalog sync and no app installs. */
    private val server: ServerClient? = serverSetup.current?.let { ServerClient(it, http, textLanguage) }

    /**
     * The folder of the per-server data: `servers/<key>`, or `servers/none` while no address is saved. A switch
     * restarts the process, so this folder holds for the whole process (AC5).
     */
    private val serverDir = serverDir(app.filesDir, serverSetup.current)

    /** The build writes an empty blocklist.txt when it has no list. */
    val hasBlocklist: Boolean = app.assets.open(BLOCKLIST_ASSET).use { it.read() >= 0 }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Owner of the current catalog: the stored manifest from the server, else none until the server answers. */
    val catalogSync: CatalogSync = CatalogSync.create(CatalogStorage.forServer(serverDir), server, scope)
    val siteSettings: SiteSettingsStore = SiteSettingsStore.forServer(serverDir)

    /** The pointer look holds for every server. */
    val appSettings: AppSettings = AppSettings.create(app)

    /** The app language; Android stores it, not [appSettings] (AC13). */
    val appLanguage: AppLanguage = AppLanguage(AndroidLocaleStore(app))

    /** "Meine Liste" and "Weiterschauen"; entries of sites missing from the current catalog stay hidden. */
    val shelves: Shelves = Shelves(
        myList = shelf(Shelf.MY_LIST),
        continueWatching = shelf(Shelf.CONTINUE_WATCHING),
    )

    /** When the start page last opened each site and app; it orders "Seiten" and "Apps". Only E6 writes it. */
    private val recentlyOpened: RecentlyOpened = RecentlyOpened.forServer(serverDir)

    /** Null when the build has no blocklist. */
    private val blocklist: Blocklist? =
        if (hasBlocklist) Blocklist(open = { app.assets.open(BLOCKLIST_ASSET) }) else null

    private val device = AndroidPackageInstaller(app, updatable = BuildConfig.RELEASE_SIGNED)

    /**
     * Downloaded APKs wait here until the install session has them. The folder is emptied on each start, so a
     * process kill leaves no APK behind (review item 43).
     */
    private val apkDir = java.io.File(app.cacheDir, "apk").apply {
        deleteRecursively()
        mkdirs()
    }

    /** The one install queue for apps and the own update. */
    val installer: AppInstaller = AppInstaller(
        server,
        device,
        scope,
        workFile = { java.io.File.createTempFile("install", ".apk", apkDir) },
    )

    /** The installed versions of the current catalog's apps; refreshed when the start page returns. */
    val installedApps: InstalledApps = InstalledApps(
        device,
        catalogSync.catalog.map { it.catalog?.manifest?.apps.orEmpty().map(CatalogApp::pkg) }.distinctUntilChanged(),
        scope,
    )

    /** The own update from the GitHub releases of `BuildConfig.UPDATE_REPO`; it needs no server (AC7). */
    val selfUpdate: SelfUpdate = SelfUpdate.create(
        GithubReleases(BuildConfig.UPDATE_REPO, http),
        device,
        scope,
        files = UpdateFiles(
            work = { java.io.File.createTempFile("update", ".apk", apkDir) },
            failed = java.io.File(app.filesDir, "self-update-failed"),
        ),
    )

    /** The browser's events; the screen sends, [EventRoutes] collects. */
    private val browserSender = BrowserEventSender()

    /** What each browser screen gets; "Weiterschauen" only to read, it adds through [browserSender] (E5). */
    val browser: BrowserDependencies = BrowserDependencies(
        catalog = catalogSync.catalog,
        settings = siteSettings,
        looks = appSettings.pointer,
        shelves = BrowserShelves(myList = shelves.myList, continueWatching = shelves.continueWatching),
        blocklist = blocklist,
        sender = browserSender,
    )

    /** The start page's events; the start page sends, [EventRoutes] collects. */
    private val launcherSender = LauncherEventSender()

    /** What the start page gets; "zuletzt geöffnet" only to read, it adds through [launcherSender] (E6). */
    val launcher: LauncherDependencies = LauncherDependencies(
        catalog = catalogSync.catalog,
        settings = siteSettings,
        shelves = shelves,
        installer = installer,
        installed = installedApps.versions,
        used = recentlyOpened.used,
        sender = launcherSender,
    )

    private val eventRoutes = EventRoutes(
        installer,
        selfUpdate,
        installedApps,
        shelves.continueWatching,
        recentlyOpened,
        browserSender,
        launcherSender,
    )

    /**
     * Starts the process work: the event routes, then the blocklist load. Call it once, from `TvApplication.onCreate`.
     * An event sent before waits in its sender's buffer.
     */
    fun start() {
        eventRoutes.start(scope)
        blocklist?.start()
    }

    private fun shelf(shelf: Shelf) = SavedShelf(
        SavedPageStore.forServer(app, serverDir, shelf),
        { catalogSync.catalog.value.catalog?.ids.orEmpty() },
    )

    private companion object {
        const val BLOCKLIST_ASSET = "blocklist.txt"
    }
}
