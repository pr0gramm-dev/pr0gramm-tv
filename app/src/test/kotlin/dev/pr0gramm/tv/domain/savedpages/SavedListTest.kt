package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.savedpages.PageMeta
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.Shelf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedListTest {
    private val site = Site(
        id = SiteId("nova"),
        name = "Nova",
        url = "https://nova.example/",
        description = null,
        category = null,
        tags = emptyList(),
        color = null,
        allowInsecureContent = false,
        allow = emptySet(),
        defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false),
        banner = null,
    )

    private fun meta(vararg fields: Pair<String, Any?>) = PageMeta.parse(JSONObject(mapOf(*fields)).toString())

    private fun entry(n: Int, at: Long = n.toLong()) =
        SavedPage(site.id, "https://nova.example/v/$n", "Video $n", null, at, null)

    private val visible = setOf(site.id)

    private fun hidden(n: Int) =
        SavedPage(SiteId("gone"), "https://gone.example/v/$n", "Weg $n", null, n.toLong(), null)

    private fun key(url: String) = SavedPageKey.of(site.id, url)

    private fun page(url: String?, kind: Page.Kind = Page.Kind.NORMAL) = Page(url, kind)

    @Test
    fun startAndUnsavablePagesAreRefused() {
        val m = meta("title" to "x")
        val start = SavedList.draft(emptyList(), site, page("https://nova.example/", Page.Kind.START), m, 1)
        val video = SavedList.draft(emptyList(), site, page("https://nova.example/v", Page.Kind.UNSAVABLE), m, 1)
        val noUrl = SavedList.draft(emptyList(), site, page(null), m, 1)
        assertEquals(SaveResult.Refused(Refusal.START_PAGE), start)
        assertEquals(SaveResult.Refused(Refusal.NOT_SAVABLE), video)
        assertEquals(SaveResult.Refused(Refusal.NOT_SAVABLE), noUrl)
    }

    @Test
    fun draftTakesTitleAndDescriptionFromMeta() {
        val m = meta("titles" to listOf("Folge 3 | Nova"), "ogDescription" to "Eine  Folge")
        val result = SavedList.draft(emptyList(), site, page("https://nova.example/v/3"), m, 77)
        val expected = SavedPage(site.id, "https://nova.example/v/3", "Folge 3", "Eine Folge", 77, null)
        assertEquals(SaveResult.Saved(expected), result)
        val untitled = SavedList.draft(emptyList(), site, page("https://nova.example/v/4"), PageMeta.EMPTY, 1)
        assertEquals("Nova", (untitled as SaveResult.Saved).entry.title)
    }

    @Test
    fun fullListRefusesNewPagesButNotSavedOnes() {
        val full = (1..SavedList.MAX).map { entry(it) }
        val new = SavedList.draft(full, site, page("https://nova.example/v/99"), PageMeta.EMPTY, 100)
        val again = SavedList.draft(full, site, page("https://nova.example/v/7/"), PageMeta.EMPTY, 100)
        assertEquals(SaveResult.Refused(Refusal.FULL), new)
        assertEquals(key("https://nova.example/v/7"), (again as SaveResult.Saved).entry.key)
        val almost = SavedList.draft(full.drop(1), site, page("https://nova.example/v/99"), PageMeta.EMPTY, 1)
        assertTrue(almost is SaveResult.Saved)
    }

    @Test
    fun newEntriesGoInFrontAndResavingMovesToTheFront() {
        val list = listOf(entry(3), entry(2), entry(1))
        assertEquals(listOf(4, 3, 2, 1), SavedList.add(list, visible, entry(4)).map { it.savedAt.toInt() })
        val resaved = SavedList.add(list, visible, entry(1, at = 9).copy(url = "https://nova.example/v/1/#x"))
        assertEquals(listOf(9L, 3L, 2L), resaved.map { it.savedAt })
        assertEquals(SavedList.MAX, SavedList.add((1..SavedList.MAX).map { entry(it) }, visible, entry(99)).size)
    }

    @Test
    fun overflowDropsTheOldestHiddenEntryFirst() {
        val oldHidden = hidden(1)
        val list = listOf(hidden(2)) + (3..SavedList.MAX).map { entry(it) } + oldHidden
        val added = SavedList.add(list, visible, entry(99))
        assertEquals(SavedList.MAX, added.size)
        assertTrue(oldHidden !in added)
        assertTrue(hidden(2) in added)
        assertEquals(entry(99), added.first())
    }

    @Test
    fun overflowDropsTheOldestVisibleEntryWhenNothingIsHidden() {
        val list = (1..SavedList.MAX).map { entry(it) }.reversed()
        val added = SavedList.add(list, visible, entry(99))
        assertEquals(SavedList.MAX, added.size)
        assertTrue(entry(1) !in added)
    }

    @Test
    fun aShelfWithACapPerSiteDropsTheSitesOldestEntryOnly() {
        val other = SavedPage(SiteId("b"), "https://b.example/v/1", "B", null, 1, null)
        val list = listOf(entry(3), other, entry(2), entry(1))
        val added = SavedList.add(list, visible + other.site, entry(5), perSite = 3)
        assertEquals(listOf(entry(5), entry(3), other, entry(2)), added)
    }

    @Test
    fun weiterschauenKeepsFivePerSite() {
        assertEquals(5, Shelf.CONTINUE_WATCHING.perSite)
        val list = (1..5).map { entry(it) }.reversed()
        val added = SavedList.add(list, visible, entry(6), Shelf.CONTINUE_WATCHING.perSite)
        assertEquals((2..6).map { entry(it) }.reversed(), added)
    }

    @Test
    fun remove() {
        val other = SavedPage(SiteId("gone"), "https://gone.example/", "Weg", null, 5, null)
        val list = listOf(entry(2), other, entry(1))
        assertEquals(listOf(entry(2), other), SavedList.remove(list, key("https://nova.example/v/1/")))
        assertEquals(list, SavedList.remove(list, key("https://nova.example/nope")))
    }
}
