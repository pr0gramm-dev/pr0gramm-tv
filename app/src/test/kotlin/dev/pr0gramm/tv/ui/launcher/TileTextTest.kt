package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.core.text.StringTable
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class TileTextTest {
    private val defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false)
    private val site = Site(
        SiteId("kk"), "Lumen", "https://kk.example/", null, null, emptyList(), null, false, emptySet(),
        defaults, null,
    )
    private val zone: ZoneId = ZoneOffset.UTC

    // Wednesday, 18 November 2026.
    private val today = LocalDate.of(2026, 11, 18)

    private fun millis(day: LocalDate, hour: Int = 12) = day.atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun saved(shelf: Shelf, title: String = "Dune: Part Two", day: LocalDate = today) =
        Tile.Saved(shelf, SavedPage(site.id, "https://kk.example/dune", title, null, millis(day), null), site)

    private fun app(state: AppState) =
        Tile.App(CatalogApp("org.kodi", "Kodi", null, 2, Apk(ServerPath.of("kodi.apk")!!, "ab".repeat(32), 10)), state)

    private fun sub(tile: Tile, table: StringTable) = TileText.sub(tile, today, zone)?.let(table::invoke)

    /** The sub line of [tile] in German and in English. */
    private fun sub(tile: Tile) = sub(tile, Strings.de) to sub(tile, Strings.en)

    @Test
    fun `a site has its name and a star when it is a favorite`() {
        val plain = Tile.Site(site, defaults)
        val favorite = Tile.Site(site, defaults.copy(favorite = true))

        assertEquals("Lumen", TileText.title(plain))
        assertFalse(TileText.star(plain))
        assertTrue(TileText.star(favorite))
        assertNull(TileText.sub(plain, today, zone))
        assertNull(TileText.source(favorite))
    }

    @Test
    fun `a site's sub line says why it stands in front`() {
        val yesterday = millis(today.minusDays(1))
        assertEquals("Favorit" to "Favorite", sub(Tile.Site(site, defaults.copy(favorite = true), usedAt = yesterday)))
        assertEquals(
            "zuletzt: gestern" to "last opened: yesterday",
            sub(Tile.Site(site, defaults, usedAt = yesterday)),
        )
    }

    @Test
    fun `an installed app used from here says when, other states win`() {
        val now = millis(today)
        assertEquals(
            "zuletzt: heute" to "last opened: today",
            sub(Tile.App(app(AppState.Installed).entry, AppState.Installed, usedAt = now)),
        )
        assertEquals(
            "Update verfügbar" to "Update available",
            sub(Tile.App(app(AppState.Update).entry, AppState.Update, usedAt = now)),
        )
    }

    @Test
    fun `an app's sub line names its state`() {
        assertEquals("Kodi", TileText.title(app(AppState.Missing)))
        assertFalse(TileText.star(app(AppState.Installed)))
        assertEquals("Nicht installiert" to "Not installed", sub(app(AppState.Missing)))
        assertEquals("Wartet …" to "Waiting…", sub(app(AppState.Waiting)))
        assertEquals("Wird geladen … 40 %" to "Downloading… 40%", sub(app(AppState.Running(40))))
        assertEquals("Fehler: Prüfung fehlgeschlagen" to "Error: file check failed", sub(app(AppState.Failed)))
        assertEquals("Installiert" to "Installed", sub(app(AppState.Installed)))
        assertEquals("Update verfügbar" to "Update available", sub(app(AppState.Update)))
    }

    @Test
    fun `an entry of Meine Liste says when it was saved and names its site`() {
        val tile = saved(Shelf.MY_LIST, day = LocalDate.of(2026, 11, 2))

        assertEquals("Dune: Part Two", TileText.title(tile))
        assertEquals("gemerkt am 02.11." to "saved Nov 2", sub(tile))
        assertEquals("Lumen", TileText.source(tile))
    }

    @Test
    fun `an entry without an own title shows only its time and names no site`() {
        // The title equals the site name, so the sub line does not repeat it (review item 5: no "start URL" mark).
        val tile = saved(Shelf.MY_LIST, title = "Lumen", day = LocalDate.of(2026, 11, 13))

        assertEquals("Lumen", TileText.title(tile))
        assertEquals("gemerkt am 13.11." to "saved Nov 13", sub(tile))
        assertNull(TileText.source(tile))
        assertEquals("heute" to "today", sub(saved(Shelf.CONTINUE_WATCHING, title = "Lumen")))
    }

    @Test
    fun `Weiterschauen says today, yesterday, the weekday within a week, else the date`() {
        fun watched(day: LocalDate) = sub(saved(Shelf.CONTINUE_WATCHING, day = day))

        assertEquals("heute" to "today", watched(today))
        assertEquals("gestern" to "yesterday", watched(today.minusDays(1)))
        assertEquals("Mo" to "Mon", watched(LocalDate.of(2026, 11, 16)))
        assertEquals("Do" to "Thu", watched(today.minusDays(6)))
        assertEquals("11.11." to "Nov 11", watched(today.minusDays(7)))
    }

    @Test
    fun `the day is taken in the given zone`() {
        val lateEvening = Tile.Saved(
            Shelf.CONTINUE_WATCHING,
            SavedPage(site.id, "https://kk.example/x", "X", null, millis(today.minusDays(1), hour = 23), null),
            site,
        )

        assertEquals("gestern", TileText.sub(lateEvening, today, zone)?.let(Strings.de::invoke))
        assertEquals("today", TileText.sub(lateEvening, today, ZoneOffset.ofHours(2))?.let(Strings.en::invoke))
    }
}
