package dev.pr0gramm.tv.ui.launcher.settings

import androidx.annotation.StringRes
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.remotekeys.SEEK_S
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text

/** One key at one place and what it does there; [held] means pressed for 600 ms. `*words*` are shown brighter. */
data class KeyLine(val key: UiText, val text: UiText, val held: Boolean = false)

/** The keys at one place of the app, e.g. the home screen or a page with the control Focus. */
data class KeyPlace(val name: UiText, val lines: List<KeyLine>)

/** The key table of the page "Remote keys": every key at every place, in the order of the remote. Pure. */
object Controls {
    /** The gear of the TCL remote; U+FE0E in the resource asks for the text glyph, not the coloured emoji. */
    val GEAR: UiText = text(R.string.controls_gear)

    /** Key symbols and the key name OK are the same in every language: they are printed on the remote. */
    private val ARROWS = UiText.Plain("◀ ▲ ▼ ▶")
    private val OK = UiText.Plain("OK")
    private val BACK = text(R.string.controls_key_back)

    val places: List<KeyPlace> = listOf(
        KeyPlace(
            text(R.string.controls_place_home),
            listOf(
                KeyLine(ARROWS, text(R.string.controls_select_tile)),
                KeyLine(OK, text(R.string.controls_open_tile)),
                KeyLine(GEAR, text(R.string.controls_tile_menu)),
                KeyLine(OK, text(R.string.controls_tile_menu_held), held = true),
                KeyLine(BACK, text(R.string.controls_leave_app)),
            ),
        ),
        page(
            R.string.controls_place_page_focus,
            arrows = R.string.controls_focus_arrows,
            ok = R.string.controls_focus_ok,
            other = R.string.control_pointer,
        ),
        page(
            R.string.controls_place_page_pointer,
            arrows = R.string.controls_pointer_arrows,
            ok = R.string.controls_pointer_ok,
            other = R.string.control_focus,
        ),
        KeyPlace(
            text(R.string.controls_place_fullscreen),
            listOf(
                KeyLine(UiText.Plain("▲ ▼"), text(R.string.controls_volume)),
                KeyLine(UiText.Plain("◀ ▶"), text(R.string.controls_seek, SEEK_S)),
                KeyLine(OK, text(R.string.controls_play_pause)),
                KeyLine(GEAR, text(R.string.controls_page_menu)),
                KeyLine(BACK, text(R.string.controls_leave_fullscreen)),
                KeyLine(BACK, text(R.string.controls_home_plain), held = true),
            ),
        ),
    )

    val HINT: UiText = text(R.string.controls_hint)
    val FOOT: UiText = text(R.string.controls_foot)

    /** A page in the control [name]d by the place; OK held switches to the control [other]. */
    private fun page(@StringRes name: Int, @StringRes arrows: Int, @StringRes ok: Int, @StringRes other: Int) =
        KeyPlace(
            text(name),
            listOf(
                KeyLine(ARROWS, text(arrows)),
                KeyLine(OK, text(ok)),
                KeyLine(OK, text(R.string.controls_switch_to, text(other)), held = true),
                KeyLine(GEAR, text(R.string.controls_page_menu_full)),
                KeyLine(BACK, text(R.string.controls_page_back)),
                KeyLine(BACK, text(R.string.controls_home_marked), held = true),
            ),
        )
}
