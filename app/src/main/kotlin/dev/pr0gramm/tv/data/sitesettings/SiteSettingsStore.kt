package dev.pr0gramm.tv.data.sitesettings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Owner of all per-site state (favorite included). Only deviations from `Site.defaults` are stored. Settings of
 * sites missing from the catalog stay in the file, so they are back when the site returns (AC4).
 */
class SiteSettingsStore(private val store: DataStore<Preferences>) {
    /** Every site of [sites] with overrides applied. */
    fun all(sites: List<Site>): Flow<Map<SiteId, SiteOptions>> =
        store.data.map { prefs -> sites.associate { it.id to SiteOverrides.decode(it.defaults, prefs, it.id) } }

    fun of(site: Site): Flow<SiteOptions> =
        store.data.map { SiteOverrides.decode(site.defaults, it, site.id) }

    /** Stores only fields that differ from the site's defaults. */
    suspend fun update(site: Site, change: (SiteOptions) -> SiteOptions) {
        store.edit { prefs ->
            SiteOverrides.encode(
                site.defaults,
                change(SiteOverrides.decode(site.defaults, prefs, site.id)),
                site.id,
                prefs,
            )
        }
    }

    /** Back to the site's defaults. */
    suspend fun reset(site: Site) {
        store.edit { SiteOverrides.clear(site.id, it) }
    }

    companion object {
        /** The settings of the server whose data lives in [dir] (see `serverDir`). */
        fun forServer(
            dir: File,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): SiteSettingsStore = create(File(dir, "datastore/site-settings.preferences_pb"), scope)

        /** A corrupt file is replaced by an empty one, which means all defaults. */
        fun create(
            file: File,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): SiteSettingsStore = SiteSettingsStore(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            ),
        )
    }
}
