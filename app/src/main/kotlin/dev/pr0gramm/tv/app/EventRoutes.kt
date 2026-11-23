package dev.pr0gramm.tv.app

import dev.pr0gramm.tv.data.installedapps.InstalledApps
import dev.pr0gramm.tv.data.recentlyopened.RecentlyOpened
import dev.pr0gramm.tv.domain.appinstall.AppInstaller
import dev.pr0gramm.tv.domain.appinstall.InstallEvent
import dev.pr0gramm.tv.domain.appinstall.InstallJob
import dev.pr0gramm.tv.domain.appinstall.InstallOutcome
import dev.pr0gramm.tv.domain.savedpages.SavedShelf
import dev.pr0gramm.tv.domain.selfupdate.SelfUpdate
import dev.pr0gramm.tv.domain.selfupdate.SelfUpdateReady
import dev.pr0gramm.tv.ui.browser.BrowserEventSender
import dev.pr0gramm.tv.ui.browser.VideoWatched
import dev.pr0gramm.tv.ui.launcher.LauncherEventSender
import dev.pr0gramm.tv.ui.launcher.OpenedFromHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The one table from a slice's event to another slice's command (AC10). Only this class collects events; it knows no
 * `Context`. [start] collects each sender once in the process scope; each `on` is one route.
 */
@Suppress("LongParameterList") // reason: one parameter per sender or receiver of a route; the table names them all
class EventRoutes(
    private val installer: AppInstaller,
    private val selfUpdate: SelfUpdate,
    private val installedApps: InstalledApps,
    private val continueWatching: SavedShelf,
    private val recentlyOpened: RecentlyOpened,
    private val browser: BrowserEventSender,
    private val launcher: LauncherEventSender,
) {
    fun start(scope: CoroutineScope) {
        scope.launch { installer.events.collect { on(it) } }
        scope.launch { selfUpdate.events.collect { on(it) } }
        scope.launch { browser.events.collect { on(it) } }
        scope.launch { launcher.events.collect { on(it) } }
    }

    internal fun on(event: InstallEvent) {
        when (event) {
            is InstallEvent.Finished -> {
                // E1: an install ended, so the installed versions are read again.
                installedApps.refresh()
                // E3: the end of the own install goes back to the self-update.
                val self = event.job as? InstallJob.Self ?: return
                selfUpdate.installEnded(self.versionCode, failed = event.outcome == InstallOutcome.FAILED)
            }
        }
    }

    /** E2: the checked own update waits in the install queue for the start page. */
    internal fun on(event: SelfUpdateReady) = installer.selfReady(event.versionCode, event.apk)

    /** E5: a video watched in fullscreen puts its page in "Weiterschauen"; a refused page stays out, as before. */
    internal suspend fun on(event: VideoWatched) {
        continueWatching.save(event.site, event.page, event.meta)
    }

    /** E6: a site or an app opened from the start page goes to "zuletzt geöffnet". */
    internal suspend fun on(event: OpenedFromHome) = recentlyOpened.touch(event.key)
}
