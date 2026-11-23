package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.savedpages.Shelf

/** The name of this shelf for the user. Every menu takes it from here (review item 18). */
val Shelf.title: UiText
    get() = when (this) {
        Shelf.CONTINUE_WATCHING -> text(R.string.shelf_continue_watching)
        Shelf.MY_LIST -> text(R.string.shelf_my_list)
    }
