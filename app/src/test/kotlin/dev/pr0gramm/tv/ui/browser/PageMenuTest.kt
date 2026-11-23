package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.core.designsystem.PanelRow
import dev.pr0gramm.tv.core.designsystem.line
import dev.pr0gramm.tv.core.text.StringTable
import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.savedpages.SavedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class PageMenuTest {
    private val site = Site(
        id = SiteId("a"),
        name = "Alpha",
        url = "https://a.example/",
        description = null,
        category = null,
        tags = emptyList(),
        color = null,
        allowInsecureContent = false,
        allow = emptySet(),
        defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false),
        banner = null,
    )
    private val today = LocalDate.of(2026, 11, 19)
    private val day = 86_400_000L
    private val todayMs = today.toEpochDay() * day

    private fun entry(path: String, site: SiteId = this.site.id, at: Long = todayMs) =
        SavedPage(site, "https://${site.value}.example/$path", "Titel $path", null, at, null)

    private fun menu(
        url: String? = "https://a.example/open",
        title: String? = "Folge 5",
        watching: List<SavedPage> = emptyList(),
        myList: List<SavedPage> = emptyList(),
    ) = PageMenu.of(site, OpenPage(url, title), pointer = false, continueWatching = watching, myList = myList)

    private fun PageMenu.rows() = lines(today, ZoneOffset.UTC).map { it.row }

    private fun PageMenu.rows(table: StringTable) = rows().map(table::line)

    @Test
    fun withNothingSavedTheMenuHasOnlyTheActionsAndNoHome() {
        assertEquals(listOf("Zu Meine Liste hinzufügen", "Zu Maus wechseln"), menu().rows(Strings.de))
        assertEquals(listOf("Add to My List", "Switch to Pointer"), menu().rows(Strings.en))
    }

    @Test
    fun theHeadNamesTheOpenPageAndTheSite() {
        assertEquals("Folge 5", menu().title)
        assertEquals("auf Alpha", menu().subtitle?.let(Strings.de::invoke))
        assertEquals("on Alpha", menu().subtitle?.let(Strings.en::invoke))
        assertEquals("Alpha", menu(title = "Alpha").title)
        assertEquals("Alpha", menu(title = null).title)
        assertNull(menu(title = " ").subtitle)
    }

    @Test
    fun savedEntriesOfTheSiteFollowNewestFirstWithoutTheOpenPageOrOtherSites() {
        val watching = listOf(entry("open"), entry("w2"), entry("x", SiteId("b")), entry("w1", at = todayMs - day))
        val myList = listOf(entry("m1", at = todayMs - 6 * day))
        val menu = menu(watching = watching, myList = myList)
        assertEquals(
            listOf(
                "Zu Meine Liste hinzufügen",
                "Zu Maus wechseln",
                "# Gespeichert auf Alpha | OK öffnet hier im Browser",
                "## Weiterschauen 2",
                "Titel w2 · heute",
                "Titel w1 · gestern",
                "## Meine Liste 1",
                "Titel m1 · gemerkt am 13.11.",
            ),
            menu.rows(Strings.de),
        )
        assertEquals(
            listOf(
                "Add to My List",
                "Switch to Pointer",
                "# Saved on Alpha | OK opens the page here",
                "## Continue Watching 2",
                "Titel w2 · today",
                "Titel w1 · yesterday",
                "## My List 1",
                "Titel m1 · saved Nov 13",
            ),
            menu.rows(Strings.en),
        )
    }

    @Test
    fun anEmptySectionIsLeftOut() {
        val rows = menu(myList = listOf(entry("m1"))).rows()
        assertEquals(listOf("## Meine Liste 1"), rows.filterIsInstance<PanelRow.Section>().map(Strings.de::line))
    }

    @Test
    fun aPickNamesTheItemOrTheEntryAndHeadersPickNothing() {
        val w = entry("w")
        val lines = menu(watching = listOf(w)).lines(today, ZoneOffset.UTC)
        assertEquals(PagePick.Item(PageMenuItem.ADD), lines[0].pick)
        assertEquals(PagePick.Item(PageMenuItem.POINTER_ON), lines[1].pick)
        assertNull(lines[2].pick)
        assertNull(lines[3].pick)
        assertEquals(PagePick.Open(w), lines[4].pick)
    }

    @Test
    fun aSavedPageOffersRemoveAndThePointerItemFollowsTheMode() {
        val open = entry("x")
        val m = PageMenu.of(site, OpenPage(open.url, "X"), true, continueWatching = emptyList(), myList = listOf(open))
        assertEquals(listOf(PageMenuItem.REMOVE, PageMenuItem.POINTER_OFF), m.items)
        assertEquals(listOf("Aus Meine Liste entfernen", "Zu Fokus wechseln"), m.rows(Strings.de))
        assertEquals(listOf("Remove from My List", "Switch to Focus"), m.rows(Strings.en))
        assertEquals(emptyList<SavedPage>(), m.myList)
    }
}
