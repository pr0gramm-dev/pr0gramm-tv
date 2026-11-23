package dev.pr0gramm.tv.ui.launcher

/** One item of the start page's single column: the header, a block's title, or a line of its tiles. */
sealed interface HomeItem {
    /** Key for lazy lists. */
    val key: String

    data object Header : HomeItem {
        override val key: String get() = "header"
    }

    data class Title(val kind: RowKind) : HomeItem {
        override val key: String get() = "title:$kind"
    }

    /** A block's tiles in one line that scrolls sideways. */
    data class Strip(val kind: RowKind, val tiles: List<Tile>) : HomeItem {
        override val key: String get() = "strip:$kind"
    }
}

/**
 * Lays the rows out as items of one lazy column (AC5, AC6): the header scrolls away with the page; every block is a
 * title and one endless strip (anordnung.html, variant B).
 */
object HomeBlocks {
    fun items(rows: List<Row>): List<HomeItem> = buildList {
        add(HomeItem.Header)
        for (row in rows) {
            add(HomeItem.Title(row.kind))
            add(HomeItem.Strip(row.kind, row.tiles))
        }
    }

    /** Index in [items] of the strip of block [kind]: the header comes first, then a title and a strip per block. */
    fun stripIndex(rows: List<Row>, kind: RowKind): Int = 2 + 2 * rows.indexOfFirst { it.kind == kind }
}
