package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.domain.savedpages.Refusal
import dev.pr0gramm.tv.domain.savedpages.SaveResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveMessagesTest {
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

    private fun entry(n: Int) = SavedPage(site.id, "https://nova.example/v/$n", "Video $n", null, n.toLong(), null)

    @Test
    fun messagesAreTheGermanTextsOfTheOldApp() {
        val e = entry(1)
        fun say(result: SaveResult) = Strings.de(SaveMessages.of(result, site))
        assertEquals("„Video 1“ ist jetzt in Meine Liste", say(SaveResult.Saved(e)))
        assertEquals("„Video 1“ aus Meine Liste entfernt", say(SaveResult.Removed(e)))
        assertEquals("Diese Seite ist nicht in Meine Liste", say(SaveResult.Removed(null)))
        assertEquals("Diese Seite kann nicht gemerkt werden", say(SaveResult.Refused(Refusal.NOT_SAVABLE)))
        assertEquals("Das ist die Start-URL von Nova", say(SaveResult.Refused(Refusal.START_PAGE)))
        assertEquals("Meine Liste ist voll (50). Entferne erst einen Eintrag.", say(SaveResult.Refused(Refusal.FULL)))
    }

    @Test
    fun messagesInEnglish() {
        val e = entry(1)
        fun say(result: SaveResult) = Strings.en(SaveMessages.of(result, site))
        assertEquals("\"Video 1\" is now in My List", say(SaveResult.Saved(e)))
        assertEquals("Removed \"Video 1\" from My List", say(SaveResult.Removed(e)))
        assertEquals("This page is not in My List", say(SaveResult.Removed(null)))
        assertEquals("The app cannot save this page", say(SaveResult.Refused(Refusal.NOT_SAVABLE)))
        assertEquals("This page is the start URL of Nova", say(SaveResult.Refused(Refusal.START_PAGE)))
        assertEquals("My List is full (50). Remove an entry first.", say(SaveResult.Refused(Refusal.FULL)))
    }
}
