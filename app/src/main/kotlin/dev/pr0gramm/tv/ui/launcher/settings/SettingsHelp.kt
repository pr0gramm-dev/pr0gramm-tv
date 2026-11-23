package dev.pr0gramm.tv.ui.launcher.settings

import androidx.annotation.StringRes
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.appsettings.LanguageChoice
import dev.pr0gramm.tv.data.appsettings.PointerLook
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteOptions

/** One choice of a setting and what it does; [active] marks the one set now. */
data class HelpOption(val name: UiText, val text: UiText, val active: Boolean)

/** A named block of help text that is not a choice, e.g. one of the things a row acts on. */
data class HelpTopic(val name: UiText, val text: UiText)

/**
 * What a row changes: a short [intro], one block per [topics] entry, one block per [options] choice, then a
 * [note] (all optional). Only [options] get the square that marks a choice.
 */
data class Help(
    val intro: UiText? = null,
    val topics: List<HelpTopic> = emptyList(),
    val options: List<HelpOption> = emptyList(),
    val note: UiText? = null,
)

/** What the panel next to a settings list says about the focused row. */
data class RowHelp(val label: UiText, val help: Help, val scope: UiText)

/** What a settings row changes, in plain words for the panel next to the list. Pure. */
object SettingsHelp {
    fun site(setting: Setting, site: Site, options: SiteOptions): Help = when (setting) {
        Setting.CONTROL -> control(options.control == Control.POINTER)
        Setting.ZOOM -> Help(
            intro = text(R.string.help_zoom_intro),
            note = text(R.string.help_zoom_note, site.name, site.defaults.zoom.percent),
        )
        Setting.LAYOUT -> {
            val desktop = options.layout == Layout.DESKTOP
            Help(
                options = listOf(
                    option(R.string.settings_layout_normal, R.string.help_layout_normal, !desktop),
                    option(R.string.settings_layout_desktop, R.string.help_layout_desktop, desktop),
                ),
            )
        }
        Setting.BLOCKLIST -> Help(
            options = listOf(
                option(R.string.settings_on, R.string.help_blocklist_on, options.blocklist),
                option(R.string.settings_off, R.string.help_blocklist_off, !options.blocklist),
            ),
        )
        Setting.FAVORITE -> Help(
            options = listOf(
                option(R.string.settings_yes, R.string.help_favorite_yes, options.favorite),
                option(R.string.settings_no, R.string.help_favorite_no, !options.favorite),
            ),
            note = text(R.string.help_favorite_note),
        )
        Setting.RESET -> Help(intro = text(R.string.help_reset_intro), note = text(R.string.help_reset_note))
    }

    private fun control(pointer: Boolean) = Help(
        options = listOf(
            option(R.string.control_focus, R.string.help_control_focus, !pointer),
            option(R.string.control_pointer, R.string.help_control_pointer, pointer),
        ),
        note = text(R.string.help_control_note),
    )

    fun general(item: GeneralItem, pointer: PointerLook, language: LanguageChoice): Help = when {
        item == GeneralItem.POINTER -> pointer(pointer)
        item == GeneralItem.LANGUAGE -> language(language)
        item == GeneralItem.SERVER ->
            Help(intro = text(R.string.help_server_intro), note = text(R.string.help_server_note))
        item == GeneralItem.CONTROLS -> Help(
            intro = text(R.string.help_controls_intro),
            topics = listOf(HelpTopic(text(R.string.help_controls_topic), text(R.string.help_controls_topic_text))),
            note = text(R.string.help_controls_note),
        )
        item == GeneralItem.ABOUT -> Help(intro = text(R.string.help_about_intro))
        else -> Help(intro = text(R.string.help_check_intro), note = text(R.string.help_check_note))
    }

    private fun pointer(look: PointerLook) = Help(
        intro = text(R.string.help_pointer_intro),
        options = listOf(
            HelpOption(PointerLook.HAND.label, text(R.string.help_pointer_hand), look == PointerLook.HAND),
            HelpOption(PointerLook.GLOW.label, text(R.string.help_pointer_glow), look == PointerLook.GLOW),
        ),
    )

    /** One option per choice, in the order of [LanguageChoice.next]: System, English, Deutsch (AC6). */
    private fun language(choice: LanguageChoice) = Help(
        intro = text(R.string.help_language_intro),
        options = LanguageChoice.entries.map { HelpOption(it.shown, text(languageHelp(it)), it == choice) },
        note = text(R.string.help_language_note),
    )

    @StringRes
    private fun languageHelp(choice: LanguageChoice): Int = when (choice) {
        LanguageChoice.SYSTEM -> R.string.help_language_system
        LanguageChoice.ENGLISH -> R.string.help_language_english
        LanguageChoice.GERMAN -> R.string.help_language_german
    }

    private fun option(@StringRes name: Int, @StringRes what: Int, active: Boolean) =
        HelpOption(text(name), text(what), active)

    val GENERAL_SCOPE: UiText = text(R.string.help_general_scope)
}
