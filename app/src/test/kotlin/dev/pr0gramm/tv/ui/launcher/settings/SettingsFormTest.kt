package dev.pr0gramm.tv.ui.launcher.settings

import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.StringTable
import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormTest {
    private fun site(zoom: Int = 100) = Site(
        id = SiteId("a"),
        name = "Alpha",
        url = "https://a.example/",
        description = null,
        category = null,
        tags = emptyList(),
        color = null,
        allowInsecureContent = false,
        allow = emptySet(),
        defaults = SiteOptions(Control.FOCUS, Zoom.of(zoom)!!, Layout.NORMAL, true, false),
        banner = null,
    )

    private fun rows(site: Site, options: SiteOptions = site.defaults, hasBlocklist: Boolean = true) =
        SettingsForm.rows(site, options, hasBlocklist)

    private fun step(site: Site, options: SiteOptions, setting: Setting, direction: Int = 1) =
        SettingsForm.step(site, options, setting, direction)

    @Test
    fun rowsFollowAc4InOrderAndTitleIsTheSiteName() {
        val state = rows(site())
        assertEquals("Alpha", state.title)
        assertEquals(
            listOf(
                Setting.CONTROL,
                Setting.ZOOM,
                Setting.LAYOUT,
                Setting.BLOCKLIST,
                Setting.FAVORITE,
                Setting.RESET,
            ),
            state.rows.map { it.setting },
        )
    }

    @Test
    fun noAdblockRowWithoutBlocklist() {
        val settings = rows(site(), hasBlocklist = false).rows.map { it.setting }
        assertFalse(Setting.BLOCKLIST in settings)
        assertEquals(5, settings.size)
    }

    @Test
    fun rowLabelsInBothLanguages() {
        val labels = rows(site()).rows.map { it.label }
        assertEquals(
            listOf("Steuerung", "Größe", "Ansicht", "Werbeblocker", "Favorit", "Alles zurücksetzen"),
            labels.map(Strings.de::invoke),
        )
        assertEquals(
            listOf("Control", "Size", "Layout", "Ad blocker", "Favorite", "Reset all"),
            labels.map(Strings.en::invoke),
        )
    }

    @Test
    fun rowValuesAreReadable() {
        val o = site().defaults.copy(control = Control.POINTER, layout = Layout.DESKTOP, blocklist = false)
        val state = rows(site(), o.copy(favorite = true))
        fun values(table: StringTable) = state.rows.associate { it.setting to table(it.value) }
        val de = values(Strings.de)
        assertEquals("Maus", de[Setting.CONTROL])
        assertEquals("100 %", de[Setting.ZOOM])
        assertEquals("Computer", de[Setting.LAYOUT])
        assertEquals("Aus", de[Setting.BLOCKLIST])
        assertEquals("Ja", de[Setting.FAVORITE])
        val en = values(Strings.en)
        assertEquals("Pointer", en[Setting.CONTROL])
        assertEquals("100%", en[Setting.ZOOM])
        assertEquals("Computer", en[Setting.LAYOUT])
        assertEquals("Off", en[Setting.BLOCKLIST])
        assertEquals("Yes", en[Setting.FAVORITE])
        val focus = rows(site()).rows.associate { it.setting to it.value }
        assertEquals("Fokus", Strings.de(focus.getValue(Setting.CONTROL)))
        assertEquals("Focus", Strings.en(focus.getValue(Setting.CONTROL)))
        assertEquals("No", Strings.en(focus.getValue(Setting.FAVORITE)))
    }

    @Test
    fun rowsShowAsSwitchStepperOrDangerAndExplainThemselves() {
        val styles = rows(site()).rows.associate { it.setting to it.style }
        assertEquals(ItemStyle.TEXT, styles[Setting.CONTROL])
        assertEquals(ItemStyle.STEPPER, styles[Setting.ZOOM])
        assertEquals(ItemStyle.SWITCH, styles[Setting.BLOCKLIST])
        assertEquals(ItemStyle.DANGER, styles[Setting.RESET])
        val switches = rows(site(), site().defaults.copy(favorite = true)).rows.associate { it.setting to it.on }
        assertTrue(switches.getValue(Setting.FAVORITE))
        for (table in listOf(Strings.de, Strings.en)) {
            assertTrue(rows(site()).rows.all { table.words(it.help).isNotBlank() })
        }
        val zoomHelp = rows(site(zoom = 120)).rows.first { it.setting == Setting.ZOOM }.help
        val de = Strings.de.words(zoomHelp)
        val en = Strings.en.words(zoomHelp)
        assertTrue(de, "Vorgabe für Alpha: 120\u00A0%." in de)
        assertTrue(en, "Press ◀ ▶ to change the size in steps. Default for Alpha: 120%." in en)
    }

    @Test
    fun everyOptionNamesItsOwnHelpText() {
        fun pairs(setting: Setting) = rows(site()).rows.first { it.setting == setting }.help.options.map {
            (it.name as UiText.Res).id to (it.text as UiText.Res).id
        }
        assertEquals(
            listOf(
                R.string.settings_yes to R.string.help_favorite_yes,
                R.string.settings_no to R.string.help_favorite_no,
            ),
            pairs(Setting.FAVORITE),
        )
        assertEquals(
            listOf(
                R.string.settings_on to R.string.help_blocklist_on,
                R.string.settings_off to R.string.help_blocklist_off,
            ),
            pairs(Setting.BLOCKLIST),
        )
        assertEquals(
            listOf(
                R.string.control_focus to R.string.help_control_focus,
                R.string.control_pointer to R.string.help_control_pointer,
            ),
            pairs(Setting.CONTROL),
        )
    }

    @Test
    fun helpMarksTheChoiceThatIsSetNow() {
        val pointer = site().defaults.copy(control = Control.POINTER)
        val choices = rows(site(), pointer).rows.first { it.setting == Setting.CONTROL }.help.options
        assertEquals(listOf("Fokus" to false, "Maus" to true), choices.map { Strings.de(it.name) to it.active })
        assertEquals(listOf("Focus" to false, "Pointer" to true), choices.map { Strings.en(it.name) to it.active })
    }

    @Test
    fun helpTextsInBothLanguages() {
        val help = rows(site()).rows.associate { it.setting to it.help }
        val reset = help.getValue(Setting.RESET)
        assertEquals(
            "Setzt alle Einstellungen dieser Seite auf die Vorgaben zurück. " +
                "Meine Liste, Weiterschauen und Anmeldungen auf der Seite bleiben.",
            Strings.de.words(reset),
        )
        assertEquals(
            "Sets all settings of this site back to the defaults. " +
                "My List, Continue Watching and your logins on the site do not change.",
            Strings.en.words(reset),
        )
        val favorite = help.getValue(Setting.FAVORITE)
        assertTrue("Steht im Block „Seiten“ vorne und trägt einen Stern." in Strings.de.words(favorite))
        assertTrue("The site is at the front of the Sites row and has a star." in Strings.en.words(favorite))
        val layout = Strings.en.words(help.getValue(Setting.LAYOUT))
        assertTrue(layout, "Normal The site shows its layout for Android. Computer The site thinks" in layout)
        val blocklist = Strings.de.words(help.getValue(Setting.BLOCKLIST))
        assertTrue(blocklist, "An Nichts von bekannten Werbe- und Tracker-Adressen wird geladen" in blocklist)
    }

    @Test
    fun everyChoiceHelpMarksExactlyTheValueTheRowShows() {
        val s = site()
        for (setting in listOf(Setting.LAYOUT, Setting.BLOCKLIST, Setting.FAVORITE, Setting.CONTROL)) {
            for (options in listOf(s.defaults, step(s, s.defaults, setting))) {
                val row = rows(s, options).rows.first { it.setting == setting }
                val active = row.help.options.filter { it.active }
                assertEquals("$setting $options", 1, active.size)
                assertEquals("$setting $options", row.value, active.single().name)
            }
        }
    }

    @Test
    fun zoomStepsByTenAndStopsAtTheEnds() {
        val s = site()
        assertEquals(110, step(s, s.defaults, Setting.ZOOM, 1).zoom.percent)
        assertEquals(90, step(s, s.defaults, Setting.ZOOM, -1).zoom.percent)
        val top = s.defaults.copy(zoom = Zoom.of(150)!!)
        assertEquals(150, step(s, top, Setting.ZOOM, 1).zoom.percent)
        val bottom = s.defaults.copy(zoom = Zoom.of(50)!!)
        assertEquals(50, step(s, bottom, Setting.ZOOM, -1).zoom.percent)
    }

    @Test
    fun zoomStepsIncludeAnOffGridDefault() {
        val s = site(zoom = 85)
        val up = step(s, s.defaults.copy(zoom = Zoom.of(80)!!), Setting.ZOOM, 1)
        assertEquals(85, up.zoom.percent)
        assertEquals(90, step(s, up, Setting.ZOOM, 1).zoom.percent)
        assertEquals(80, step(s, up, Setting.ZOOM, -1).zoom.percent)
    }

    @Test
    fun controlAndLayoutToggle() {
        val s = site()
        assertEquals(Control.POINTER, step(s, s.defaults, Setting.CONTROL).control)
        assertEquals(Control.FOCUS, step(s, step(s, s.defaults, Setting.CONTROL), Setting.CONTROL).control)
        assertEquals(Layout.DESKTOP, step(s, s.defaults, Setting.LAYOUT).layout)
        assertFalse(step(s, s.defaults, Setting.BLOCKLIST).blocklist)
    }

    @Test
    fun favoriteToggles() {
        val s = site()
        val fav = step(s, s.defaults, Setting.FAVORITE)
        assertTrue(fav.favorite)
        assertFalse(step(s, fav, Setting.FAVORITE).favorite)
    }

    @Test
    fun resetGivesTheDefaults() {
        val s = site()
        val changed = s.defaults.copy(zoom = Zoom.of(60)!!, favorite = true)
        assertEquals(s.defaults, step(s, changed, Setting.RESET))
    }
}
