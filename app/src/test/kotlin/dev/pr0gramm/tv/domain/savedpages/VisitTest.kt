package dev.pr0gramm.tv.domain.savedpages

import org.junit.Assert.assertEquals
import org.junit.Test

class VisitTest {
    private val start = "https://site.example/"

    @Test
    fun startUrlIsTheStartPageInAnyNormalizedForm() {
        val visit = Visit(start, fromStart = true)
        assertEquals(Page(start, Page.Kind.START), visit.page(start))
        assertEquals(Page.Kind.START, visit.page("https://site.example#top").kind)
    }

    @Test
    fun otherPagesAreNormal() {
        val visit = Visit(start, fromStart = true)
        visit.loaded(start)
        assertEquals(Page.Kind.NORMAL, visit.page("https://site.example/film/1").kind)
    }

    @Test
    fun firstLoadedPageAfterOpeningTheStartPageIsAlsoTheStartPage() {
        val visit = Visit(start, fromStart = true)
        visit.loaded("https://www.site.example/de/")
        visit.loaded("https://www.site.example/de/film/1")
        assertEquals(Page.Kind.START, visit.page("https://www.site.example/de").kind)
        assertEquals(Page.Kind.NORMAL, visit.page("https://www.site.example/de/film/1").kind)
    }

    @Test
    fun nonWebLoadsDoNotCommitTheStartPage() {
        val visit = Visit(start, fromStart = true)
        visit.loaded("about:blank")
        visit.loaded("https://www.site.example/de/")
        assertEquals(Page.Kind.START, visit.page("https://www.site.example/de/").kind)
    }

    @Test
    fun openingASavedPageKeepsOnlyTheConfiguredStart() {
        val visit = Visit(start, fromStart = false)
        visit.loaded("https://site.example/film/1")
        assertEquals(Page.Kind.NORMAL, visit.page("https://site.example/film/1").kind)
        assertEquals(Page.Kind.START, visit.page(start).kind)
    }

    @Test
    fun playedMediaUrlIsUnsavable() {
        val visit = Visit(start, fromStart = true)
        visit.loaded(start)
        visit.played("https://cdn.example/v.mp4")
        assertEquals(Page.Kind.UNSAVABLE, visit.page("https://cdn.example/v.mp4").kind)
        assertEquals(Page.Kind.NORMAL, visit.page("https://cdn.example/other.mp4").kind)
    }

    @Test
    fun missingOrNonWebUrlIsUnsavable() {
        val visit = Visit(start, fromStart = true)
        assertEquals(Page(null, Page.Kind.UNSAVABLE), visit.page(null))
        assertEquals(Page.Kind.UNSAVABLE, visit.page("about:blank").kind)
        assertEquals(Page.Kind.UNSAVABLE, visit.page("data:text/html,x").kind)
    }
}
