package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.core.designsystem.PanelRow
import dev.pr0gramm.tv.core.designsystem.line
import dev.pr0gramm.tv.core.designsystem.subtitle
import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.ServerPath
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.Shelf
import dev.pr0gramm.tv.domain.savedpages.DayClock
import dev.pr0gramm.tv.domain.savedpages.SavedLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TileMenuRowsTest {
    private val site = Site(
        id = SiteId("a"),
        name = "Alpha",
        url = "https://a.example/",
        description = null,
        category = "Anime",
        tags = emptyList(),
        color = null,
        allowInsecureContent = false,
        allow = emptySet(),
        defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false),
        banner = null,
    )
    private val today = LocalDate.of(2026, 11, 19)
    private val zone = ZoneOffset.UTC

    private fun entry(path: String, site: SiteId = this.site.id) = SavedPage(
        site,
        "https://${site.value}.example/$path",
        "Titel $path",
        null,
        MILLIS_TODAY,
        null,
    )

    private fun lines(tile: Tile, saved: SavedLists = SavedLists(emptyList(), emptyList())) =
        TileMenuRows.lines(tile, saved, catalog(), DayClock(today, zone))

    private fun catalog() = dev.pr0gramm.tv.data.catalogsync.CachedCatalog(
        dev.pr0gramm.tv.data.manifest.Manifest(1, today, listOf(site), emptyList()),
        java.io.File("/data/catalog/1"),
        emptySet(),
    )

    private val file = ServerPath.of("kodi.apk")!!

    @Test
    fun siteMenuListsWhatIsSavedOnItAfterItsActions() {
        val mine = entry("m1")
        val other = entry("o1", SiteId("b"))
        val rows = lines(
            Tile.Site(site, SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, false, false)),
            SavedLists(continueWatching = listOf(entry("w1")), myList = listOf(mine, other)),
        )
        assertEquals(
            listOf(
                "Öffnen",
                "Als Favorit",
                "Einstellungen",
                "# Gespeichert auf Alpha | ",
                "## Weiterschauen 1",
                "Titel w1 · heute",
                "## Meine Liste 1",
                "Titel m1 · gemerkt am 19.11.",
            ),
            rows.map { Strings.de.line(it.row) },
        )
        assertEquals(
            listOf(
                "Open",
                "Make favorite",
                "Settings",
                "# Saved on Alpha | ",
                "## Continue Watching 1",
                "Titel w1 · today",
                "## My List 1",
                "Titel m1 · saved Nov 19",
            ),
            rows.map { Strings.en.line(it.row) },
        )
        assertEquals(Action.Open(OpenTarget(site.id, "https://a.example/m1", "Titel m1", null)), rows.last().action)
    }

    @Test
    fun siteWithoutSavedPagesHasNoGroup() {
        val rows = lines(Tile.Site(site, SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, false, false)))
        assertEquals(3, rows.size)
        assertNull(rows.find { it.row is PanelRow.Group })
    }

    @Test
    fun siteHeadNamesTheKindAndTheFavorite() {
        val tile = Tile.Site(site, SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, false, true))
        val head = TileMenuRows.head(tile, catalog(), today, zone)
        assertEquals("Seite", Strings.de(head.kicker))
        assertEquals("Site", Strings.en(head.kicker))
        assertEquals("Anime · Favorit", Strings.de.subtitle(head))
        assertEquals("Anime · Favorite", Strings.en.subtitle(head))
        assertNull(head.badge)
    }

    @Test
    fun anAppWithAnUpdateInstallsFirstAndShowsTheBadge() {
        val app = CatalogApp("org.kodi", "Kodi", null, 2, Apk(file, "ab".repeat(32), 10))
        val tile = Tile.App(app, AppState.Update)
        val rows = lines(tile)
        assertEquals(listOf("Aktualisieren", "Öffnen"), rows.map { Strings.de.line(it.row) })
        assertEquals(listOf("Update", "Open"), rows.map { Strings.en.line(it.row) })
        assertEquals(Action.Install(app, update = true), rows.first().action)
        val head = TileMenuRows.head(tile, catalog(), today, zone)
        assertEquals("Update", head.badge?.let(Strings.de::invoke))
        assertEquals("Update", head.badge?.let(Strings.en::invoke))
        assertEquals("Update verfügbar", Strings.de.subtitle(head))
        assertEquals("Update available", Strings.en.subtitle(head))
    }

    @Test
    fun aSavedTileHeadNamesItsShelfAndSite() {
        val tile = Tile.Saved(Shelf.CONTINUE_WATCHING, entry("w1"), site)
        val head = TileMenuRows.head(tile, catalog(), today, zone)
        assertEquals("Weiterschauen", Strings.de(head.kicker))
        assertEquals("Continue Watching", Strings.en(head.kicker))
        assertEquals("Alpha · heute", Strings.de.subtitle(head))
        assertEquals("Alpha · today", Strings.en.subtitle(head))
    }

    private companion object {
        /** Noon on 19 November 2026, UTC: the day of [today]. */
        const val MILLIS_TODAY = 1_795_089_600_000L
    }
}
