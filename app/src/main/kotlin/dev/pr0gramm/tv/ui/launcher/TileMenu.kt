package dev.pr0gramm.tv.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.PANEL_KEYS
import dev.pr0gramm.tv.core.designsystem.PanelHead
import dev.pr0gramm.tv.core.designsystem.PanelRow
import dev.pr0gramm.tv.core.designsystem.SidePanel
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.domain.savedpages.DayClock
import dev.pr0gramm.tv.domain.savedpages.DayText
import dev.pr0gramm.tv.domain.savedpages.SavedLists
import dev.pr0gramm.tv.domain.savedpages.SavedSections
import dev.pr0gramm.tv.domain.savedpages.title
import java.time.LocalDate
import java.time.ZoneId

internal val Action.label: UiText
    get() = when (this) {
        is Action.Open, is Action.Launch -> text(R.string.menu_open)
        is Action.Install -> text(if (update) R.string.menu_update else R.string.menu_install)
        is Action.SetFavorite -> text(if (on) R.string.menu_favorite_on else R.string.menu_favorite_off)
        is Action.OpenSettings -> text(R.string.menu_settings)
        is Action.AddToMyList -> text(R.string.menu_add_my_list)
        is Action.Remove -> text(R.string.menu_remove_from, shelf.title)
    }

/** One row of a tile menu; [action] is what OK does on it, null for a heading. */
data class MenuLine(val row: PanelRow, val action: Action?)

/** The head and rows of the menu of a tile, as in seitenleisten.html. Pure. */
object TileMenuRows {
    /** The kicker names the kind of tile; the picture is the banner or the cover, the badge marks an update. */
    fun head(tile: Tile, catalog: CachedCatalog, today: LocalDate, zone: ZoneId): PanelHead = when (tile) {
        is Tile.Site -> PanelHead(
            kicker = text(R.string.tile_kicker_site),
            title = tile.site.name,
            subtitle = listOfNotNull(
                tile.site.category?.let(UiText::Plain),
                text(R.string.tile_favorite).takeIf { tile.options.favorite },
            ),
            picture = tile.site.banner?.let(catalog::image),
        )
        is Tile.Saved -> PanelHead(
            kicker = tile.shelf.title,
            title = tile.entry.title,
            subtitle = listOf(
                UiText.Plain(tile.site.name),
                DayText.of(tile.shelf, tile.entry.savedAt, today, zone),
            ),
            picture = tile.entry.image ?: tile.site.banner?.let(catalog::image),
        )
        is Tile.App -> PanelHead(
            kicker = text(R.string.tile_kicker_app),
            title = tile.entry.name,
            subtitle = listOfNotNull(appLine(tile.state)),
            picture = tile.entry.image?.let(catalog::image),
            badge = text(R.string.tile_badge_update).takeIf { tile.state == AppState.Update },
        )
    }

    /** The actions of [Home.menu]; a site also lists what is saved on it, below its actions. */
    fun lines(tile: Tile, saved: SavedLists, catalog: CachedCatalog, day: DayClock): List<MenuLine> {
        val actions = Home.menu(tile).map { MenuLine(PanelRow.Action(it.label), it) }
        if (tile !is Tile.Site) return actions
        val banner = tile.site.banner?.let(catalog::image)
        return actions + saves(tile, saved.on(tile.site.id), day, banner)
    }

    /**
     * The group of [tile]'s site, then each non-empty section: Weiterschauen, then Meine Liste. An entry without a
     * cover shows [fallback]. Nothing when [lists] is empty, so an empty group is never shown.
     */
    private fun saves(tile: Tile.Site, lists: SavedLists, day: DayClock, fallback: Any?): List<MenuLine> {
        if (lists.empty) return emptyList()
        val group = MenuLine(PanelRow.Group(text(R.string.panel_saved_on, tile.site.name)), null)
        return listOf(group) + SavedSections.of(lists, day).flatMap { section ->
            val heading = MenuLine(PanelRow.Section(section.shelf.title, section.entries.size), null)
            listOf(heading) + section.entries.map { (entry, saved) ->
                val action = Home.primary(Tile.Saved(section.shelf, entry, tile.site))
                MenuLine(PanelRow.Entry(entry.title, saved, entry.image ?: fallback), action)
            }
        }
    }

    private fun appLine(state: AppState): UiText? = when (state) {
        AppState.Update -> text(R.string.tile_update_available)
        AppState.Installed -> text(R.string.tile_installed_current)
        AppState.Missing, AppState.Failed -> text(R.string.tile_not_installed_lower)
        AppState.Waiting, is AppState.Running -> null
    }
}

/**
 * The options menu (AC8) of [tile]: a [SidePanel]. OK picks, Back is handled by the activity. The menu sits beside the
 * start page, not inside it, so it takes the [catalog] as a parameter instead of [LocalCatalog].
 */
@Composable
fun TileMenu(
    tile: Tile,
    saved: SavedLists,
    catalog: CachedCatalog,
    onPick: (Action) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val lines = TileMenuRows.lines(tile, saved, catalog, DayClock(today, zone))
    SidePanel(
        head = TileMenuRows.head(tile, catalog, today, zone),
        rows = lines.map { it.row },
        keys = PANEL_KEYS,
        onPick = { index -> lines[index].action?.let(onPick) },
        modifier = modifier,
        onClose = onClose,
    )
}
