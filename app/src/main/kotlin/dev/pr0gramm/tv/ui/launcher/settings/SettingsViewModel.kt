package dev.pr0gramm.tv.ui.launcher.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State and input of one site's settings screen. Changes apply at once and are stored by [SiteSettingsStore]. */
class SettingsViewModel(
    private val site: Site,
    private val settings: SiteSettingsStore,
    private val hasBlocklist: Boolean,
) : ViewModel() {
    val state: StateFlow<SettingsState> = settings.of(site)
        .map { SettingsForm.rows(site, it, hasBlocklist) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsForm.rows(site, site.defaults, hasBlocklist))

    fun step(setting: Setting, direction: Int = 1) {
        viewModelScope.launch { settings.update(site) { SettingsForm.step(site, it, setting, direction) } }
    }

    fun reset() {
        viewModelScope.launch { settings.reset(site) }
    }
}
