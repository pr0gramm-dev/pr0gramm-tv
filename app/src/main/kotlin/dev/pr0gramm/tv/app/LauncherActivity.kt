package dev.pr0gramm.tv.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.pr0gramm.tv.BuildConfig
import dev.pr0gramm.tv.core.designsystem.PrTheme
import dev.pr0gramm.tv.data.appsettings.LanguageChoice
import dev.pr0gramm.tv.data.appsettings.PointerLook
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.domain.catalogsync.CatalogState
import dev.pr0gramm.tv.domain.catalogsync.catalog
import dev.pr0gramm.tv.ui.browser.BrowserArgs
import dev.pr0gramm.tv.ui.launcher.HomeScreen
import dev.pr0gramm.tv.ui.launcher.LauncherEvent
import dev.pr0gramm.tv.ui.launcher.LauncherViewModel
import dev.pr0gramm.tv.ui.launcher.NoCatalogButton
import dev.pr0gramm.tv.ui.launcher.NoCatalogScreen
import dev.pr0gramm.tv.ui.launcher.ServerDialog
import dev.pr0gramm.tv.ui.launcher.Tile
import dev.pr0gramm.tv.ui.launcher.TileMenu
import dev.pr0gramm.tv.ui.launcher.settings.CatalogStamp
import dev.pr0gramm.tv.ui.launcher.settings.GeneralInfo
import dev.pr0gramm.tv.ui.launcher.settings.GeneralPress
import dev.pr0gramm.tv.ui.launcher.settings.GeneralScreen
import dev.pr0gramm.tv.ui.launcher.settings.GeneralView
import dev.pr0gramm.tv.ui.launcher.settings.SettingsScreen
import dev.pr0gramm.tv.ui.launcher.settings.SettingsViewModel
import kotlinx.coroutines.launch

private const val HOME = "home"

/** The key of the shown [LauncherScreen] in the saved instance state. */
private const val PAGE = "page"

/** The start page and its subpages: Compose only. Opening a tile starts the browser. */
class LauncherActivity : ComponentActivity() {
    private val viewModel by viewModels<LauncherViewModel> {
        val launcher = (application as TvApplication).container.launcher
        viewModelFactory {
            initializer { LauncherViewModel(launcher) }
        }
    }

    private var page by mutableStateOf<LauncherScreen>(LauncherScreen.Home)
    private var menuTile by mutableStateOf<Tile?>(null)

    /** The app language that the settings show. A change outside the app recreates nothing, so [onResume] reads it. */
    private var language by mutableStateOf(LanguageChoice.SYSTEM)

    /** The server dialog (AC2, AC4) is open. */
    private var serverOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The screen paints its own background; the theme's would be one more full-screen layer per frame (AC14).
        window.setBackgroundDrawable(null)
        // A language switch recreates the activity: the open page stays (AC7).
        page = LauncherScreen.of(savedStateRegistry.consumeRestoredStateForKey(PAGE)?.getString(PAGE))
        savedStateRegistry.registerSavedStateProvider(PAGE) { Bundle().apply { putString(PAGE, page.key) } }
        // Without a saved address the dialog opens by itself; Back closes it and leaves the no-server notice (AC2).
        if (savedInstanceState == null) {
            serverOpen = (application as TvApplication).container.serverSetup.current == null
        }
        // A site or an app is in front now: what was opened from here counts as used (the order changes unseen).
        lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.left() },
        )
        setContent {
            PrTheme { Launcher() }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::handle)
            }
        }
    }

    /**
     * The start page is shown again (also after a site): the installed apps are read again, a ready own update may
     * install now, then look for a new catalog and an update, each at most hourly and apart from the other (AC7).
     */
    override fun onResume() {
        super.onResume()
        val container = (application as TvApplication).container
        container.installer.foreground(true)
        container.installedApps.refresh()
        language = container.appLanguage.current
        container.catalogSync.check()
        container.selfUpdate.check()
    }

    /** A site or another app comes to the front: the own update must not install now (AC11). */
    override fun onPause() {
        (application as TvApplication).container.installer.foreground(false)
        super.onPause()
    }

    @Composable
    private fun Launcher() {
        val menu = menuTile
        val state by viewModel.catalog.collectAsState()
        val catalog = state.catalog
        // The start page keeps its scroll positions and focused tile while a settings page is shown.
        val saved = rememberSaveableStateHolder()
        Box {
            when (val current = page) {
                LauncherScreen.Home -> when (val shown = state) {
                    is CatalogState.Ready -> saved.SaveableStateProvider(HOME) {
                        HomeScreen(
                            viewModel,
                            shown.catalog,
                            onMenu = ::openMenu,
                            onSettings = { page = LauncherScreen.General(GeneralView.LIST) },
                            menuOpen = menu != null,
                        )
                    }
                    is CatalogState.Absent -> NoCatalogScreen(
                        shown,
                        onButton = { button ->
                            when (button) {
                                NoCatalogButton.RETRY ->
                                    (application as TvApplication).container.catalogSync.check(force = true)
                                NoCatalogButton.ENTER_SERVER -> serverOpen = true
                            }
                        },
                        onSettings = { page = LauncherScreen.General(GeneralView.LIST) },
                    )
                }
                is LauncherScreen.Settings -> SettingsPage(current.site)
                is LauncherScreen.General -> GeneralPage(current.view)
            }
        }
        val savedLists by viewModel.saved.collectAsState()
        if (menu != null && catalog != null) {
            TileMenu(menu, savedLists, catalog, onPick = { action ->
                menuTile = null
                viewModel.act(action)
            }, onClose = { menuTile = null })
        }
        BackHandler(enabled = page != LauncherScreen.Home) {
            val sub = (page as? LauncherScreen.General)?.view?.let { it != GeneralView.LIST } == true
            page = if (sub) LauncherScreen.General(GeneralView.LIST) else LauncherScreen.Home
        }
        BackHandler(enabled = menu != null) { menuTile = null }
        if (serverOpen) {
            val setup = (application as TvApplication).container.serverSetup
            // Another address takes effect only in a new process (AC4).
            ServerDialog(
                shown = setup.current?.shown.orEmpty(),
                connect = setup::connect,
                onSaved = { changed ->
                    if (changed) ProcessRestart.restart(this@LauncherActivity) else serverOpen = false
                },
                onClose = { serverOpen = false },
            )
        }
    }

    @Composable
    private fun SettingsPage(id: SiteId) {
        val container = (application as TvApplication).container
        val state by container.catalogSync.catalog.collectAsState()
        val site = state.catalog?.get(id)
        // The site left the catalog (also while shown): back to the start page.
        if (site == null) {
            LaunchedEffect(id) { page = LauncherScreen.Home }
            return
        }
        val settingsModel = viewModel<SettingsViewModel>(
            key = "settings/${id.value}",
            factory = viewModelFactory {
                initializer { SettingsViewModel(site, container.siteSettings, container.hasBlocklist) }
            },
        )
        SettingsScreen(settingsModel)
    }

    @Composable
    private fun GeneralPage(view: GeneralView) {
        val container = (application as TvApplication).container
        val status by container.selfUpdate.status.collectAsState()
        val checking by container.selfUpdate.checking.collectAsState()
        val checkedAt by container.selfUpdate.checkedAt.collectAsState()
        val state by container.catalogSync.catalog.collectAsState()
        val pointer by container.appSettings.pointer.collectAsState(PointerLook.HAND)
        val info = GeneralInfo(
            status = status,
            checking = checking,
            appVersion = BuildConfig.VERSION_NAME,
            catalog = state.catalog?.manifest?.let { CatalogStamp(it.version, it.date) },
            server = container.serverSetup.current,
            checkedAt = checkedAt,
            pointer = pointer,
            language = language,
        )
        GeneralScreen(info, view, onPress = { press ->
            when (press) {
                GeneralPress.CHECK -> container.selfUpdate.check(force = true)
                GeneralPress.RETRY_UPDATE -> container.selfUpdate.retry()
                GeneralPress.ALLOW_INSTALLS -> allowInstalls()
                GeneralPress.OPEN_SERVER -> serverOpen = true
                GeneralPress.SWITCH_POINTER -> lifecycleScope.launch { container.appSettings.switchPointer() }
                GeneralPress.SWITCH_LANGUAGE -> language = container.appLanguage.switch()
                GeneralPress.OPEN_ABOUT -> page = LauncherScreen.General(GeneralView.ABOUT)
                GeneralPress.OPEN_CONTROLS -> page = LauncherScreen.General(GeneralView.CONTROLS)
                GeneralPress.NOTHING -> Unit
            }
        })
    }

    private fun allowInstalls() = startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
    )

    private fun openMenu(tile: Tile) {
        if (viewModel.menu(tile).isNotEmpty()) menuTile = tile
    }

    private fun launch(pkg: String) {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        intent?.let(::startActivity)
    }

    private fun handle(event: LauncherEvent) {
        when (event) {
            is LauncherEvent.Open -> event.target.let {
                startActivity(BrowserActivity.of(this, BrowserArgs(it.site, it.url, it.title, it.image)))
            }
            is LauncherEvent.Settings -> page = LauncherScreen.Settings(event.site)
            is LauncherEvent.Launch -> launch(event.pkg)
            LauncherEvent.AllowInstalls -> allowInstalls()
        }
    }
}
