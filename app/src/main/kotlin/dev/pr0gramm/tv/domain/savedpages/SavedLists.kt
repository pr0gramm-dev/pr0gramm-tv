package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.savedpages.SavedPage
import java.time.LocalDate
import java.time.ZoneId

/** The day that [DayText] words are measured from: [today] in [zone]. */
data class DayClock(val today: LocalDate, val zone: ZoneId)

/** Saved entries, each list newest first. */
data class SavedLists(val continueWatching: List<SavedPage>, val myList: List<SavedPage>) {
    val empty: Boolean get() = continueWatching.isEmpty() && myList.isEmpty()

    /** The entries of [site] only. */
    fun on(site: SiteId): SavedLists =
        SavedLists(continueWatching.filter { it.site == site }, myList.filter { it.site == site })
}
