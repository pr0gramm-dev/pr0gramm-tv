package dev.pr0gramm.tv.data.recentlyopened

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import dev.pr0gramm.tv.data.manifest.SiteId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * When a site or an app was last opened from the start page (anordnung.html, variant B), in epoch millis. Apps
 * started elsewhere (another launcher) are not seen. Saved entries are never used here: they open their site.
 */
class RecentlyOpened(
    private val store: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val used: Flow<Map<OpenedKey, Long>> = store.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { (key, value) ->
            val opened = keyOf(key.name)
            if (opened != null && value is Long) opened to value else null
        }.toMap()
    }

    /** [key] (a site or an app) was opened now. */
    suspend fun touch(key: OpenedKey) {
        store.edit { it[longPreferencesKey(nameOf(key))] = clock() }
    }

    companion object {
        // The stored names; changing them forgets every use on the TVs.
        private const val SITE = "site:"
        private const val APP = "app:"

        private fun nameOf(key: OpenedKey): String = when (key) {
            is OpenedKey.Site -> SITE + key.id.value
            is OpenedKey.App -> APP + key.pkg
        }

        private fun keyOf(name: String): OpenedKey? = when {
            name.startsWith(SITE) -> OpenedKey.Site(SiteId(name.removePrefix(SITE)))
            name.startsWith(APP) -> OpenedKey.App(name.removePrefix(APP))
            else -> null
        }

        /** The uses on the server whose data lives in [dir] (see `serverDir`). */
        fun forServer(
            dir: File,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): RecentlyOpened = create(File(dir, "datastore/recently-opened.preferences_pb"), scope)

        /** A corrupt file is replaced by an empty one: nothing counts as used. */
        fun create(
            file: File,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): RecentlyOpened = RecentlyOpened(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            ),
        )
    }
}
