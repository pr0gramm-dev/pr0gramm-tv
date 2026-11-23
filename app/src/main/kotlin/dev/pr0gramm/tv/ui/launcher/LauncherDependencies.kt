package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.data.recentlyopened.OpenedKey
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import dev.pr0gramm.tv.domain.appinstall.AppInstaller
import dev.pr0gramm.tv.domain.catalogsync.CatalogState
import dev.pr0gramm.tv.domain.savedpages.Shelves
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * What a [LauncherViewModel] needs from the rest of the app. [installer] takes the install requests; [installed]
 * (the installed versions) and [used] ("zuletzt geöffnet") are only to read. The start page adds to [used] through
 * [sender] (E6).
 */
@Suppress("LongParameterList") // reason: one field per dependency of the start page; grouping them hides nothing
class LauncherDependencies(
    val catalog: StateFlow<CatalogState>,
    val settings: SiteSettingsStore,
    val shelves: Shelves,
    val installer: AppInstaller,
    val installed: Flow<Map<String, Long>>,
    val used: Flow<Map<OpenedKey, Long>>,
    val sender: LauncherEventSender,
)
