package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.data.savedpages.Shelf

/** The two shelves of saved pages: "Meine Liste" and "Weiterschauen". */
class Shelves(val myList: SavedShelf, val continueWatching: SavedShelf) {
    fun of(shelf: Shelf): SavedShelf = when (shelf) {
        Shelf.MY_LIST -> myList
        Shelf.CONTINUE_WATCHING -> continueWatching
    }
}
