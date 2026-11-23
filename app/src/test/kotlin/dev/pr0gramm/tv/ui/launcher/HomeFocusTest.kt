package dev.pr0gramm.tv.ui.launcher

import androidx.compose.runtime.saveable.SaverScope
import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.ServerPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeFocusTest {
    private fun app(n: Int) = Tile.App(
        CatalogApp("app.$n", "App $n", null, 1, Apk(ServerPath.of("$n.apk")!!, "ab".repeat(32), 10)),
        AppState.Installed,
    )

    private val tiles = (1..4).map(::app)

    @Test
    fun `a tile moved by its last use keeps the focus over the one Android picks meanwhile`() {
        val before = listOf(Row(RowKind.APPS, tiles))
        val focus = HomeFocus(null, null).apply { settle(null, before) }
        focus.focused(tiles[2], before)
        assertEquals(FocusSpot(RowKind.APPS, 2), focus.spot)

        // The third app was used: it moves to the front, and Android hands the focus to another tile.
        val after = listOf(Row(RowKind.APPS, listOf(tiles[2], tiles[0], tiles[1], tiles[3])))
        focus.focused(tiles[3], after)
        assertEquals(tiles[2].key.listKey, focus.key)

        // Once restored there, the focus follows the user again.
        focus.settle(tiles[2], after)
        focus.focused(tiles[0], after)
        assertEquals(tiles[0].key.listKey, focus.key)
    }

    @Test
    fun `a removed tile keeps the focus until restore puts it on the tile at its place`() {
        val before = listOf(Row(RowKind.APPS, tiles))
        val focus = HomeFocus(null, null).apply { settle(tiles[1], before) }

        // The second app was removed through the menu; Android focuses a neighbour by itself.
        val after = listOf(Row(RowKind.APPS, tiles - tiles[1]))
        focus.focused(tiles[0], after)
        assertEquals(tiles[1].key.listKey, focus.key)
        assertEquals(tiles[2], HomeFocus.target(after, focus.key, focus.spot))
    }

    @Test
    fun `no move counts before the first restore`() {
        val rows = listOf(Row(RowKind.APPS, tiles))
        val focus = HomeFocus(null, null)
        focus.focused(tiles[3], rows)
        assertNull(focus.key)
    }

    @Test
    fun `the focus survives a recreated activity`() {
        val rows = listOf(Row(RowKind.APPS, tiles))
        val focus = HomeFocus(null, null).apply { settle(tiles[3], rows) }
        val saved = with(HomeFocus.Saver) { SaverScope { true }.save(focus) }!!
        val restored = HomeFocus.Saver.restore(saved)!!
        assertEquals(tiles[3].key.listKey, restored.key)
        assertEquals(FocusSpot(RowKind.APPS, 3), restored.spot)

        val none = with(HomeFocus.Saver) { SaverScope { true }.save(HomeFocus(null, null)) }!!
        val empty = HomeFocus.Saver.restore(none)!!
        assertNull(empty.key)
        assertNull(empty.spot)
    }
}
