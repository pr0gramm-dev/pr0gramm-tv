package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.Shelf

/** One non-empty shelf of saved pages in a menu: each entry with the words for when it was saved ([DayText]). */
data class SavedSection(val shelf: Shelf, val entries: List<DatedPage>)

/** A saved page and the words for its day, for example "yesterday" or "saved Nov 13". */
data class DatedPage(val entry: SavedPage, val day: UiText)

/** The sections that list the saved pages of one site in a menu (seitenleisten.html). Pure; the texts are the ui's. */
object SavedSections {
    /** Each non-empty shelf: Weiterschauen, then Meine Liste. Nothing when [lists] is empty. */
    fun of(lists: SavedLists, day: DayClock): List<SavedSection> = listOf(
        Shelf.CONTINUE_WATCHING to lists.continueWatching,
        Shelf.MY_LIST to lists.myList,
    ).filter { (_, entries) -> entries.isNotEmpty() }.map { (shelf, entries) ->
        SavedSection(shelf, entries.map { DatedPage(it, DayText.of(shelf, it.savedAt, day.today, day.zone)) })
    }
}
