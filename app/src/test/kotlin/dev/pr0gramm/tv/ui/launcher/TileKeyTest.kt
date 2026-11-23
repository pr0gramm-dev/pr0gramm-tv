package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.Shelf
import org.junit.Assert.assertEquals
import org.junit.Test

class TileKeyTest {
    // Lazy lists keep item keys in a Bundle, so the list key must be a plain String.
    @Test
    fun listKeysAreStringsAndDistinctPerKind() {
        val page = SavedPageKey.of(SiteId("nova"), "https://nova.example/a")
        val keys = listOf(
            TileKey.Site(SiteId("nova")),
            TileKey.Saved(Shelf.MY_LIST, page),
            TileKey.Saved(Shelf.CONTINUE_WATCHING, page),
            TileKey.App("org.kodi"),
        ).map { it.listKey }

        assertEquals(4, keys.toSet().size)
        assertEquals("site:nova", keys[0])
        assertEquals("app:org.kodi", keys[3])
    }
}
