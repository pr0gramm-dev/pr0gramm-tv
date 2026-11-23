package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.domain.savedpages.Refusal
import dev.pr0gramm.tv.domain.savedpages.SaveResult
import dev.pr0gramm.tv.domain.savedpages.SavedList

/** Every text "My List" shows after saving or removing a page. */
object SaveMessages {
    fun of(result: SaveResult, site: Site): UiText = when (result) {
        is SaveResult.Saved -> text(R.string.save_added, result.entry.title)
        is SaveResult.Removed ->
            result.entry?.let { text(R.string.save_removed, it.title) } ?: text(R.string.save_not_in_list)
        is SaveResult.Refused -> when (result.reason) {
            Refusal.NOT_SAVABLE -> text(R.string.save_not_savable)
            Refusal.START_PAGE -> text(R.string.save_start_page, site.name)
            Refusal.FULL -> text(R.string.save_full, SavedList.MAX)
        }
    }
}
