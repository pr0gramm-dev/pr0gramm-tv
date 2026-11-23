package dev.pr0gramm.tv.ui.launcher.settings

import dev.pr0gramm.tv.core.text.StringTable
import dev.pr0gramm.tv.core.text.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlsTest {
    private val de = Strings.de
    private val en = Strings.en

    private fun place(name: String, table: StringTable = de) = Controls.places.single { table(it.name) == name }

    private fun KeyPlace.text(key: String, held: Boolean = false, table: StringTable = de) =
        table(lines.single { table(it.key) == key && it.held == held }.text).replace("*", "")

    @Test
    fun placesAreStartPageFocusPointerAndFullscreen() {
        assertEquals(
            listOf("Startseite", "Seite mit Fokus", "Seite mit Maus", "Video im Vollbild"),
            Controls.places.map { de(it.name) },
        )
        assertEquals(
            listOf("Home screen", "Page with Focus", "Page with Pointer", "Video in full screen"),
            Controls.places.map { en(it.name) },
        )
    }

    @Test
    fun everyPlaceNamesTheGearAndBack() {
        assertEquals("⚙\uFE0E Zahnrad", de(Controls.GEAR))
        assertEquals("⚙\uFE0E Gear", en(Controls.GEAR))
        for ((table, back) in listOf(de to "Zurück", en to "Back")) {
            Controls.places.forEach { place ->
                assertTrue(table(place.name), place.lines.any { it.key == Controls.GEAR })
                assertTrue(table(place.name), place.lines.any { table(it.key) == back && !it.held })
            }
        }
    }

    @Test
    fun startPageGearAndHeldOkOpenTheTileMenu() {
        val home = place("Startseite")
        assertEquals("Menü der Kachel", home.text(de(Controls.GEAR)))
        assertEquals("Menü der Kachel, auch ohne Zahnrad", home.text("OK", held = true))
        assertEquals("App verlassen", home.text("Zurück"))
        val english = place("Home screen", en)
        assertEquals("Tile menu", english.text(en(Controls.GEAR), table = en))
        assertEquals("Tile menu, also without Gear", english.text("OK", held = true, table = en))
        assertEquals("Leave the app", english.text("Back", table = en))
    }

    @Test
    fun onAPageHeldOkSwitchesToTheOtherControlAndHeldBackGoesHome() {
        assertEquals("zu Maus wechseln", place("Seite mit Fokus").text("OK", held = true))
        assertEquals("zu Fokus wechseln", place("Seite mit Maus").text("OK", held = true))
        listOf("Seite mit Fokus", "Seite mit Maus", "Video im Vollbild").forEach {
            assertEquals("zur Startseite", place(it).text("Zurück", held = true))
        }
        assertEquals("Switch to Pointer", place("Page with Focus", en).text("OK", held = true, table = en))
        assertEquals("Switch to Focus", place("Page with Pointer", en).text("OK", held = true, table = en))
        listOf("Page with Focus", "Page with Pointer", "Video in full screen").forEach {
            assertEquals("Go to the home screen", place(it, en).text("Back", held = true, table = en))
        }
    }

    @Test
    fun fullscreenSeeksByTheSecondsOfTheKeyRouter() {
        assertEquals("10 Sekunden zurück / vor", place("Video im Vollbild").text("◀ ▶"))
        assertEquals("10 seconds back / forward", place("Video in full screen", en).text("◀ ▶", table = en))
    }

    @Test
    fun hintAndFootInBothLanguages() {
        assertEquals("Die Tasten der Fernbedienung, nach Ort.", de(Controls.HINT))
        assertEquals("The remote keys at each place in the app.", en(Controls.HINT))
        assertTrue(de(Controls.FOOT), de(Controls.FOOT).startsWith("Das ⚙\uFE0E Zahnrad gibt es"))
        assertTrue(en(Controls.FOOT), en(Controls.FOOT).endsWith("Back closes this screen."))
    }

    @Test
    fun brightMarksComeInPairs() {
        for (table in listOf(de, en)) {
            Controls.places.flatMap { it.lines }.map { table(it.text) }.forEach {
                assertEquals(it, 0, it.count { c -> c == '*' } % 2)
            }
        }
    }
}
