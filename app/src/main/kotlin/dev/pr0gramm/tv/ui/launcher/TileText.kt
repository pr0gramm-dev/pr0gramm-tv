package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.domain.savedpages.DayText
import java.time.LocalDate
import java.time.ZoneId

/** The words under a tile's frame, like `final.html`: the title tag and the grey sub line. */
object TileText {
    /** The name in the title tag; an entry without an own title carries its site's name. */
    fun title(tile: Tile): String = when (tile) {
        is Tile.Site -> tile.site.name
        is Tile.App -> tile.entry.name
        is Tile.Saved -> tile.entry.title
    }

    /** A favorite site shows a star before its name. */
    fun star(tile: Tile): Boolean = tile is Tile.Site && tile.options.favorite

    /**
     * The sub line: why a site stands in front ("Favorite", "last opened: yesterday"; none for the rest). Apps:
     * their state, for an installed one used from here when ("last opened: today"). Saved entries: when they were
     * saved ("saved Nov 13" in My List, "today" in Continue Watching). [today] and [zone] place the times.
     */
    fun sub(tile: Tile, today: LocalDate, zone: ZoneId): UiText? = when (tile) {
        is Tile.Site -> when {
            tile.options.favorite -> text(R.string.tile_favorite)
            tile.usedAt != null -> lastOpened(tile.usedAt, today, zone)
            else -> null
        }
        is Tile.App -> when {
            tile.state == AppState.Installed && tile.usedAt != null -> lastOpened(tile.usedAt, today, zone)
            else -> state(tile.state)
        }
        is Tile.Saved -> DayText.of(tile.shelf, tile.entry.savedAt, today, zone)
    }

    /** An entry with an own title names its site at the start of the sub line ("Lumen · gestern"). */
    fun source(tile: Tile): String? = (tile as? Tile.Saved)?.takeIf(::ownTitle)?.site?.name

    /** The page had no title of its own: the entry carries the site's name (see `PageMeta.title`). */
    private fun ownTitle(tile: Tile.Saved): Boolean = tile.entry.title != tile.site.name

    private fun lastOpened(millis: Long, today: LocalDate, zone: ZoneId): UiText =
        text(R.string.tile_last_opened, DayText.relative(millis, today, zone))

    private fun state(state: AppState): UiText = when (state) {
        AppState.Missing -> text(R.string.tile_not_installed)
        AppState.Waiting -> text(R.string.tile_waiting)
        is AppState.Running -> text(R.string.tile_downloading, state.percent)
        AppState.Failed -> text(R.string.tile_check_failed)
        AppState.Installed -> text(R.string.tile_installed)
        AppState.Update -> text(R.string.tile_update_available)
    }
}
