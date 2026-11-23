package dev.pr0gramm.tv.ui.launcher.settings

import androidx.annotation.StringRes
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom

enum class Setting { CONTROL, ZOOM, LAYOUT, BLOCKLIST, FAVORITE, RESET }

/** How a row shows its [SettingsRow.value]: as text, as a switch, as ◀ value ▶, or as a red action without value. */
enum class ItemStyle { TEXT, SWITCH, STEPPER, DANGER }

/** One row; [value] is the readable value, [on] the state of a switch, [help] what the row changes. */
data class SettingsRow(
    val setting: Setting,
    val label: UiText,
    val value: UiText,
    val style: ItemStyle = ItemStyle.TEXT,
    val on: Boolean = false,
    val help: Help = Help(),
)

data class SettingsState(val title: String, val rows: List<SettingsRow>)

/** The rows of the settings screen (AC4) and what one step on a row does. Pure. */
object SettingsForm {
    fun rows(site: Site, options: SiteOptions, hasBlocklist: Boolean): SettingsState {
        val pointer = options.control == Control.POINTER
        val rows = buildList {
            val control = if (pointer) R.string.control_pointer else R.string.control_focus
            add(SettingsRow(Setting.CONTROL, text(R.string.settings_control), text(control)))
            val zoom = text(R.string.settings_size_value, options.zoom.percent)
            add(SettingsRow(Setting.ZOOM, text(R.string.settings_size), zoom, ItemStyle.STEPPER))
            val desktop = options.layout == Layout.DESKTOP
            val layout = if (desktop) R.string.settings_layout_desktop else R.string.settings_layout_normal
            add(SettingsRow(Setting.LAYOUT, text(R.string.settings_layout), text(layout)))
            if (hasBlocklist) add(switch(Setting.BLOCKLIST, R.string.settings_blocklist, options.blocklist, ON_OFF))
            add(switch(Setting.FAVORITE, R.string.settings_favorite, options.favorite, YES_NO))
            add(SettingsRow(Setting.RESET, text(R.string.settings_reset), NO_VALUE, ItemStyle.DANGER))
        }
        return SettingsState(site.name, rows.map { it.copy(help = SettingsHelp.site(it.setting, site, options)) })
    }

    /** The value of a [ItemStyle.DANGER] row, which shows none. */
    private val NO_VALUE = UiText.Plain("")

    /** The values of a switch row: the text when on, the text when off. */
    private val ON_OFF = R.string.settings_on to R.string.settings_off
    private val YES_NO = R.string.settings_yes to R.string.settings_no

    private fun switch(setting: Setting, @StringRes label: Int, on: Boolean, values: Pair<Int, Int>) =
        SettingsRow(setting, text(label), text(if (on) values.first else values.second), ItemStyle.SWITCH, on)

    /** [direction] only matters for ZOOM (+1 up, -1 down, stops at the ends); every other row toggles. */
    fun step(site: Site, options: SiteOptions, setting: Setting, direction: Int): SiteOptions = when (setting) {
        Setting.CONTROL ->
            options.copy(control = if (options.control == Control.FOCUS) Control.POINTER else Control.FOCUS)
        Setting.ZOOM -> options.copy(zoom = stepZoom(site, options.zoom, direction))
        Setting.LAYOUT ->
            options.copy(layout = if (options.layout == Layout.NORMAL) Layout.DESKTOP else Layout.NORMAL)
        Setting.BLOCKLIST -> options.copy(blocklist = !options.blocklist)
        Setting.FAVORITE -> options.copy(favorite = !options.favorite)
        Setting.RESET -> site.defaults
    }

    private fun stepZoom(site: Site, current: Zoom, direction: Int): Zoom {
        val steps = Zoom.steps(site.defaults.zoom)
        val next = if (direction >= 0) {
            steps.firstOrNull { it.percent > current.percent }
        } else {
            steps.lastOrNull { it.percent < current.percent }
        }
        return next ?: current
    }
}
