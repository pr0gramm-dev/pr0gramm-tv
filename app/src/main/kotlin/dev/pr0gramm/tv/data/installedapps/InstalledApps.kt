package dev.pr0gramm.tv.data.installedapps

import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Which of the manifest's apps are installed, and in which versionCode (AC9). Only packages that are installed are in
 * [versions]. The list of apps comes from [wanted] (the current catalog's packages); a new list is looked up at once,
 * so an app a new catalog adds shows its real state without waiting for the start page to return.
 */
class InstalledApps(private val device: PackageInstallerPort, wanted: Flow<Collection<String>>, scope: CoroutineScope) {
    private val state = MutableStateFlow<Map<String, Long>>(emptyMap())
    val versions: StateFlow<Map<String, Long>> = state.asStateFlow()

    @Volatile
    private var apps: Collection<String> = emptyList()

    private val lock = Any()

    init {
        scope.launch {
            wanted.collect {
                apps = it
                refresh()
            }
        }
    }

    /** Asks the device for the apps again: on every return to the start page and after each install. */
    fun refresh() {
        // The lock keeps two refreshes from overwriting each other with an older result.
        synchronized(lock) {
            state.value = apps.mapNotNull { pkg -> device.installedVersion(pkg)?.let { pkg to it } }.toMap()
        }
    }
}
