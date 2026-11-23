package dev.pr0gramm.tv.ui.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The focus of the start page: where it is, and the one place that puts it back.
 *
 * It remembers the gear or a tile ([key], the tile's list key) and the tile's place ([spot]) over the tile menu, the
 * settings pages, a site, another app and a recreated activity. A move of the user only records the new tile
 * ([focused]); scrolling to it is the job of the page's [ShortScroll]. [Restore] puts the focus back only when the
 * page is shown again, when the tile menu closes, or when the remembered tile left its place (removed through the
 * menu, or moved to the front by its use). Until then Android may hand the focus to another tile by itself, which
 * does not count as the user's choice.
 */
@Stable
internal class HomeFocus(key: String?, spot: FocusSpot?) {
    var key: String? = key
        private set
    var spot: FocusSpot? = spot
        private set
    val gear = FocusRequester()
    private val requesters = HashMap<String, FocusRequester>()

    /** The tile that has the focus now, whoever put it there. */
    private var current: String? = null

    /** False while [Restore] has not put the focus back yet: Android's own focus moves do not count meanwhile. */
    private var settled = false

    /** The requester of [tile], for its `focusRequester` modifier. */
    fun requester(tile: Tile): FocusRequester = requesters.getOrPut(tile.key.listKey) { FocusRequester() }

    /**
     * [tile] got the focus; [rows] are the newest rows. While the remembered tile is gone or has moved, the focus
     * Android moves on by itself does not count: [Restore] picks the tile at its place, or follows it.
     */
    fun focused(tile: Tile, rows: List<Row>) {
        current = tile.key.listKey
        val last = key
        val elsewhere = last != null && last != GEAR && tile.key.listKey != last && spotOf(rows, last) != spot
        if (!settled || elsewhere) return
        record(tile, rows)
    }

    /** Only a press counts: Android also hands the gear the focus by itself (first focusable of the window). */
    fun pressedGear() {
        key = GEAR
    }

    /** [Restore] is done: [tile] (null: the gear, or nothing to focus) is the one to come back to from now on. */
    fun settle(tile: Tile?, rows: List<Row>) {
        if (tile != null) record(tile, rows)
        settled = true
    }

    private fun record(tile: Tile, rows: List<Row>) {
        key = tile.key.listKey
        spot = spotOf(rows, tile.key.listKey)
    }

    /** The remembered tile is no longer where it was in [rows]: removed, or moved by its use. */
    private fun displaced(rows: List<Row>): Boolean {
        val last = key ?: return false
        return last != GEAR && spot != null && spotOf(rows, last) != spot
    }

    /**
     * Puts the focus back on the gear or the remembered tile (else the one now at its place, else the first) when
     * the page is shown, when the tile menu ([menuOpen]) closes, and when the remembered tile left its place in
     * [rows]. A tile out of view on the [page] or in its strip ([strips]) is scrolled to first.
     */
    @Composable
    fun Restore(rows: List<Row>, menuOpen: Boolean, page: LazyListState, strips: Map<RowKind, LazyListState>) {
        var shown by remember { mutableIntStateOf(0) }
        LifecycleResumeEffect(Unit) {
            shown++
            onPauseOrDispose {}
        }
        val latest by rememberUpdatedState(rows)
        // Changes only when the remembered tile left its place; a new tile state (install progress) changes nothing.
        val moved: Any = remember(rows) { if (displaced(rows)) rows else Unit }
        LaunchedEffect(shown, menuOpen, rows.isEmpty(), moved) {
            settled = false
            if (menuOpen || rows.isEmpty()) return@LaunchedEffect
            val tile = if (key == GEAR) null else target(latest, key, spot)
            withTimeoutOrNull(RESTORE_MS) {
                if (tile == null) focusSoon(gear) else put(tile, latest, page, strips)
            }
            settle(tile, latest)
        }
    }

    /** Focuses [tile], which stands in [rows]; scrolls the [page] and its strip ([strips]) to it when needed. */
    private suspend fun put(tile: Tile, rows: List<Row>, page: LazyListState, strips: Map<RowKind, LazyListState>) {
        val listKey = tile.key.listKey
        val at = spotOf(rows, listKey) ?: return
        val strip = strips.getValue(at.kind)
        val requester = requester(tile)
        if (current == listKey) {
            // Still focused, but moved in its strip: a focused tile stays composed, and focusing it scrolls nothing.
            strip.reveal(at.index)
        } else if (!requester.tryFocus()) {
            // Not composed: its block or its place in the strip is out of view. Scroll there without waiting for a
            // layout (a strip never shown has none yet), then give the tile a few frames to appear.
            val item = HomeBlocks.stripIndex(rows, at.kind)
            if (page.layoutInfo.visibleItemsInfo.none { it.index == item }) page.requestScrollToItem(item - 1)
            if (strip.layoutInfo.visibleItemsInfo.none { it.index == at.index }) strip.requestScrollToItem(at.index)
            focusSoon(requester)
        }
    }

    companion object {
        private const val GEAR = "gear"

        /**
         * The tile to focus: the one with key [last] if it is still there, else the one now at its place [spot] in
         * its block (it was removed), else the first.
         */
        fun target(rows: List<Row>, last: String?, spot: FocusSpot? = null): Tile? {
            val tiles = rows.flatMap { it.tiles }
            val byKey = tiles.firstOrNull { it.key.listKey == last }
            val block = spot?.let { at -> rows.firstOrNull { it.kind == at.kind }?.tiles }
            val bySpot = if (spot != null && !block.isNullOrEmpty()) block[minOf(spot.index, block.lastIndex)] else null
            return byKey ?: bySpot ?: tiles.firstOrNull()
        }

        /** Where the tile with [listKey] stands, or null. */
        fun spotOf(rows: List<Row>, listKey: String): FocusSpot? = rows.firstNotNullOfOrNull { row ->
            row.tiles.indexOfFirst { it.key.listKey == listKey }.takeIf { it >= 0 }?.let { FocusSpot(row.kind, it) }
        }

        val Saver = listSaver<HomeFocus, Any?>(
            save = { listOf(it.key, it.spot?.kind?.name, it.spot?.index) },
            restore = { (key, kind, index) ->
                val spot = if (kind is String && index is Int) FocusSpot(RowKind.valueOf(kind), index) else null
                HomeFocus(key as String?, spot)
            },
        )
    }
}

@Composable
internal fun rememberHomeFocus(): HomeFocus = rememberSaveable(saver = HomeFocus.Saver) { HomeFocus(null, null) }

/** Place [index] in the block [kind]. */
data class FocusSpot(val kind: RowKind, val index: Int)

/**
 * Scrolls the least, at the pace of every other scroll, so item [index] stands in full with the strip's margin; an
 * item not laid out is put at the start instead.
 */
private suspend fun LazyListState.reveal(index: Int) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        requestScrollToItem(index)
        return
    }
    // Item offsets start after the content padding, which is the strip's margin.
    val margin = info.beforeContentPadding.toFloat()
    val distance = scrollDistance(
        item.offset + margin,
        item.size.toFloat(),
        info.viewportSize.width.toFloat(),
        margin,
        margin,
    )
    if (distance != 0f) animateScrollBy(distance, tween(SCROLL_MS, easing = LinearEasing))
}

/** Focuses [requester] within a few frames: a tile scrolled to is composed in the next layout pass. */
private suspend fun focusSoon(requester: FocusRequester) {
    repeat(FRAMES) {
        if (requester.tryFocus()) return
        withFrameNanos {}
    }
}

/** A tile that is not composed (scrolled away) cannot take the focus yet: false then. */
private fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

private const val FRAMES = 3

/** Restoring never blocks the user's own moves for longer than this. */
private const val RESTORE_MS = 1000L
