package dev.pr0gramm.tv.data.savedpages

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageMetaTest {
    private fun meta(vararg fields: Pair<String, Any?>) = PageMeta.parse(JSONObject(mapOf(*fields)).toString())

    @Test
    fun headingWinsOverOgTitleAndDocumentTitle() {
        val m = meta("heading" to "Folge 1", "titles" to listOf("Serie – Folge 1"), "title" to "Doc")
        assertEquals("Folge 1", m.title("Nova"))
    }

    @Test
    fun headingEqualToTheFallbackIsIgnored() {
        val m = meta("heading" to " nova ", "titles" to listOf("Ein langer Titel", "Kurz"))
        assertEquals("Kurz", m.title("Nova"))
    }

    @Test
    fun shortestOgTitleWinsAndPipeSuffixIsCut() {
        val m = meta("titles" to listOf("Film | Nova Streaming", "Der ganze Film mit Untertitel", ""))
        assertEquals("Film", m.title("Nova"))
    }

    @Test
    fun documentTitleThenFallback() {
        assertEquals("Doc", meta("titles" to listOf(" | x"), "title" to "Doc | Nova").title("Nova"))
        assertEquals("Nova", meta("title" to "   ").title("Nova"))
        assertEquals("Nova", PageMeta.EMPTY.title("Nova"))
    }

    @Test
    fun titleWhitespaceIsCollapsedIncludingNoBreakSpace() {
        assertEquals("A B C", meta("heading" to "  A\n\t B\u00A0\u00A0C ").title("Nova"))
    }

    @Test
    fun titleIsCappedAt80CodePointsAndTrimmed() {
        val emoji = "\uD83C\uDFAC" // one code point, two chars
        val long = emoji.repeat(79) + " " + "x".repeat(10)
        val title = meta("heading" to long).title("Nova")
        assertEquals(emoji.repeat(79), title)
        assertEquals(80, meta("heading" to "y".repeat(100)).title("Nova").length)
    }

    // ----- description and image -----

    @Test
    fun ogDescriptionWinsOverMetaDescription() {
        assertEquals("og", meta("ogDescription" to "og", "description" to "meta").description())
        assertEquals("meta", meta("ogDescription" to " \n ", "description" to " meta ").description())
        assertNull(meta("title" to "x").description())
    }

    @Test
    fun descriptionIsCollapsedAndCappedAt160() {
        assertEquals("a b", meta("description" to "a \n\n b").description())
        assertEquals(160, meta("description" to "d".repeat(300)).description()!!.length)
    }

    @Test
    fun imageOnlyHttpOrHttps() {
        assertEquals("https://cdn.example/a.jpg", meta("image" to "https://cdn.example/a.jpg").image)
        assertEquals("HTTP://cdn.example/a.jpg", meta("image" to "HTTP://cdn.example/a.jpg").image)
        assertNull(meta("image" to "data:image/png;base64,AAAA").image)
        assertNull(meta("image" to 42).image)
    }

    @Test
    fun parseNeverThrowsAndSkipsWrongTypes() {
        for (json in listOf(null, "", "null", "\"text\"", "[1,2]", "{broken")) {
            val m = PageMeta.parse(json)
            assertEquals("Nova", m.title("Nova"))
            assertNull(m.description())
            assertNull(m.image)
        }
        assertEquals("Ok", meta("titles" to listOf(1, null, "Ok"), "title" to 5).title("Nova"))
    }
}
