package dev.pr0gramm.tv.data.savedpages

/**
 * The two stored lists of saved pages: "Meine Liste" and "Weiterschauen". [dir] names the folder of the stored
 * images in the server's folder and [file] the stored list in its `datastore/`, both after the shelf.
 */
enum class Shelf(val dir: String, val file: String) {
    MY_LIST("my-list", "my-list.json"),
    CONTINUE_WATCHING("continue-watching", "continue-watching.json"),
}
