package dev.pr0gramm.tv.data.appsettings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** How the pointer of pointer mode looks; the settings page names it (`PointerLook.label` in ui.launcher.settings). */
enum class PointerLook { HAND, GLOW }

/**
 * Owner of the settings that hold for the whole app (the page "Einstellungen"); per-site state is
 * `SiteSettingsStore`.
 */
class AppSettings(private val store: DataStore<Preferences>) {
    /** [PointerLook.HAND] until changed; an unknown stored value counts as the hand too. */
    val pointer: Flow<PointerLook> = store.data.map { it.pointer() }

    /** Hand becomes glow and glow becomes hand. */
    suspend fun switchPointer() {
        store.edit { prefs ->
            prefs[POINTER] = (if (prefs.pointer() == PointerLook.HAND) PointerLook.GLOW else PointerLook.HAND).name
        }
    }

    private fun Preferences.pointer() =
        PointerLook.entries.firstOrNull { it.name == this[POINTER] } ?: PointerLook.HAND

    companion object {
        private val POINTER = stringPreferencesKey("pointer")

        fun create(context: Context): AppSettings = global(context.filesDir)

        /** The settings in `<filesDir>/datastore/`, outside `servers/`: they hold for every server (AC5). */
        fun global(
            filesDir: File,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): AppSettings = create(File(filesDir, "datastore/app-settings.preferences_pb"), scope)

        /** A corrupt file is replaced by an empty one, which means all defaults. */
        fun create(
            file: File,
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        ): AppSettings = AppSettings(
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { file },
            ),
        )
    }
}
