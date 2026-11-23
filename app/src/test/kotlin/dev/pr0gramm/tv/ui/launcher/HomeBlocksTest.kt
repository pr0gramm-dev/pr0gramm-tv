package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.ServerPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBlocksTest {
    private fun app(n: Int) = Tile.App(
        CatalogApp("app.$n", "App $n", null, 1, Apk(ServerPath.of("$n.apk")!!, "ab".repeat(32), 10)),
        AppState.Missing,
    )

    private fun row(kind: RowKind, count: Int) = Row(kind, (1..count).map(::app))

    @Test
    fun `the header comes first, then each block with its title`() {
        val items = HomeBlocks.items(listOf(row(RowKind.CONTINUE_WATCHING, 2), row(RowKind.APPS, 1)))
        assertEquals(
            listOf(
                HomeItem.Header,
                HomeItem.Title(RowKind.CONTINUE_WATCHING),
                HomeItem.Strip(RowKind.CONTINUE_WATCHING, row(RowKind.CONTINUE_WATCHING, 2).tiles),
                HomeItem.Title(RowKind.APPS),
                HomeItem.Strip(RowKind.APPS, row(RowKind.APPS, 1).tiles),
            ),
            items,
        )
    }

    @Test
    fun `sites and apps are one endless strip each`() {
        val strips = HomeBlocks.items(listOf(row(RowKind.SITES, 9))).filterIsInstance<HomeItem.Strip>()
        assertEquals(listOf(9), strips.map { it.tiles.size })
    }

    @Test
    fun `my list scrolls as one strip`() {
        val items = HomeBlocks.items(listOf(row(RowKind.MY_LIST, 30)))
        assertEquals(30, items.filterIsInstance<HomeItem.Strip>().single().tiles.size)
    }

    @Test
    fun `item keys are unique`() {
        val items = HomeBlocks.items(
            listOf(
                row(RowKind.CONTINUE_WATCHING, 1),
                row(RowKind.MY_LIST, 1),
                row(RowKind.SITES, 9),
                row(RowKind.APPS, 5),
            ),
        )
        assertEquals(items.size, items.map { it.key }.toSet().size)
    }

    @Test
    fun `the focus comes back to the last focused tile while it is on the page`() {
        val rows = listOf(row(RowKind.CONTINUE_WATCHING, 2), row(RowKind.APPS, 6))
        val sixth = rows[1].tiles[5].key.listKey
        assertEquals(sixth, HomeFocus.target(rows, sixth)?.key?.listKey)
        assertEquals(rows[0].tiles[0], HomeFocus.target(rows, null))
        assertEquals(rows[0].tiles[0], HomeFocus.target(rows, "app:gone"))
        assertEquals(null, HomeFocus.target(emptyList(), sixth))
    }

    @Test
    fun `a removed tile hands the focus to the one now at its place`() {
        val before = listOf(row(RowKind.CONTINUE_WATCHING, 2), Row(RowKind.MY_LIST, (11..13).map(::app)))
        val third = before[1].tiles[2].key.listKey
        val spot = HomeFocus.spotOf(before, third)
        assertEquals(FocusSpot(RowKind.MY_LIST, 2), spot)
        val removedSecond = listOf(before[0], Row(RowKind.MY_LIST, before[1].tiles.take(2)))
        val second = before[1].tiles[1].key.listKey
        val spotSecond = HomeFocus.spotOf(before, second)
        val withoutSecond = listOf(before[0], Row(RowKind.MY_LIST, before[1].tiles - before[1].tiles[1]))
        assertEquals(before[1].tiles[2], HomeFocus.target(withoutSecond, second, spotSecond))
        assertEquals(before[1].tiles[1], HomeFocus.target(removedSecond, third, spot))
        assertEquals(before[0].tiles[0], HomeFocus.target(listOf(before[0]), third, spot))
    }

    @Test
    fun `the strip of a block is found by its kind`() {
        val rows = listOf(row(RowKind.CONTINUE_WATCHING, 2), row(RowKind.APPS, 6))
        val items = HomeBlocks.items(rows)
        assertEquals(HomeItem.Strip(RowKind.APPS, rows[1].tiles), items[HomeBlocks.stripIndex(rows, RowKind.APPS)])
        val watching = HomeBlocks.stripIndex(rows, RowKind.CONTINUE_WATCHING)
        assertEquals(HomeItem.Strip(RowKind.CONTINUE_WATCHING, rows[0].tiles), items[watching])
    }

    @Test
    fun `no blocks leave only the header`() {
        assertTrue(HomeBlocks.items(emptyList()) == listOf(HomeItem.Header))
    }
}
