package dev.pr0gramm.tv.data.savedpages

import dev.pr0gramm.tv.data.manifest.SiteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SavedPageKeyTest {
    private fun key(url: String) = SavedPageKey.of(SiteId("nova"), url)

    @Test
    fun entryKeyIgnoresPlainFragmentAndTrailingSlash() {
        val k = key("https://nova.example/v/1")
        assertEquals(k, key("https://nova.example/v/1/"))
        assertEquals(k, key("https://nova.example/v/1#comments"))
        assertEquals(k, key("https://nova.example/v/1/#top"))
        assertEquals(key("https://nova.example/v?a=1"), key("https://nova.example/v/?a=1"))
        assertEquals(key("https://nova.example"), key("https://nova.example/"))
    }

    @Test
    fun entryKeyKeepsQueryRoutesAndSite() {
        assertNotEquals(key("https://nova.example/v"), key("https://nova.example/v?a=1"))
        assertNotEquals(key("https://nova.example/#/a"), key("https://nova.example/#/b"))
        assertNotEquals(key("https://nova.example/#!a"), key("https://nova.example/"))
        assertNotEquals(key("https://x.example/a"), SavedPageKey.of(SiteId("other"), "https://x.example/a"))
        assertEquals("nova https://nova.example/v", key("https://nova.example/v/").value)
    }
}
