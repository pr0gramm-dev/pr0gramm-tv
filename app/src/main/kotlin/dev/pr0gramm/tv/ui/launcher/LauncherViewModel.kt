package dev.pr0gramm.tv.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pr0gramm.tv.data.recentlyopened.OpenedKey
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import dev.pr0gramm.tv.domain.appinstall.AppInstaller
import dev.pr0gramm.tv.domain.appinstall.Request
import dev.pr0gramm.tv.domain.catalogsync.CatalogState
import dev.pr0gramm.tv.domain.catalogsync.catalog
import dev.pr0gramm.tv.domain.savedpages.SavedLists
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State and input of the start page. Every change goes to [SiteSettingsStore], one of the two lists or
 * [AppInstaller]; a site or an app opened from here goes out through [sender] (E6). The rows are derived again from
 * their flows, from the last uses and from the current catalog. Without a catalog the start page is [HomeState.Empty].
 * [scope] becomes `viewModelScope`; tests pass one that ends with the test, so no read on real IO comes back to Main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModel(
    deps: LauncherDependencies,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    val catalog: StateFlow<CatalogState> = deps.catalog
    private val settings = deps.settings
    private val shelves = deps.shelves
    private val installs = deps.installer
    private val sender = deps.sender

    val state: StateFlow<HomeState> = catalog.flatMapLatest { catalogState ->
        val current = catalogState.catalog ?: return@flatMapLatest flowOf(HomeState.Empty)
        // combine takes at most five typed flows: the first five give a HomeInput that only lacks the last use.
        val input = combine(
            settings.all(current.sites),
            shelves.continueWatching.entries,
            shelves.myList.entries,
            deps.installed,
            installs.queue,
        ) { options, watched, saved, installed, queue ->
            fun(used: Map<OpenedKey, Long>) = HomeInput(options, watched, saved, installed, queue, used)
        }
        combine(input, deps.used) { withUsed, times -> Home.build(current, withUsed(times)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeState.Empty)

    private val channel = Channel<LauncherEvent>(Channel.BUFFERED)

    /** The site or app opened last; it counts as used once the start page is left ([left]), so nothing jumps before. */
    private var opened: OpenedKey? = null
    val events: Flow<LauncherEvent> = channel.receiveAsFlow()

    fun menu(tile: Tile): List<Action> = Home.menu(tile)

    /** The saved entries of both shelves, for the tile menus; always the current lists. */
    val saved: StateFlow<SavedLists> = combine(shelves.continueWatching.entries, shelves.myList.entries) { w, m ->
        SavedLists(w, m)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SavedLists(emptyList(), emptyList()))

    /** OK on a tile; does nothing while an app waits or loads. */
    fun ok(tile: Tile) {
        Home.primary(tile)?.let(::act)
    }

    fun act(action: Action) {
        viewModelScope.launch {
            when (action) {
                is Action.Open -> {
                    opened = OpenedKey.Site(action.target.site)
                    channel.send(LauncherEvent.Open(action.target))
                }
                is Action.Install -> {
                    val request = installs.request(action.app)
                    if (request == Request.NEEDS_PERMISSION) channel.send(LauncherEvent.AllowInstalls)
                }
                is Action.Launch -> {
                    opened = OpenedKey.App(action.pkg)
                    channel.send(LauncherEvent.Launch(action.pkg))
                }
                is Action.SetFavorite -> settings.update(action.site) { it.copy(favorite = action.on) }
                is Action.AddToMyList -> shelves.myList.adopt(action.entry)
                is Action.Remove -> shelves.of(action.shelf).remove(action.key)
                is Action.OpenSettings -> channel.send(LauncherEvent.Settings(action.site.id))
            }
        }
    }

    /** The start page is no longer visible (a site or an app is in front): what was opened counts as used now. */
    fun left() {
        val key = opened ?: return
        opened = null
        sender.publish(OpenedFromHome(key))
    }
}
