package dev.pr0gramm.tv.ui.launcher

import androidx.activity.compose.ReportDrawnWhen
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import androidx.tv.material3.Text
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import kotlinx.coroutines.launch

@get:StringRes
private val RowKind.title: Int
    get() = when (this) {
        RowKind.CONTINUE_WATCHING -> R.string.shelf_continue_watching
        RowKind.MY_LIST -> R.string.shelf_my_list
        RowKind.SITES -> R.string.home_sites
        RowKind.APPS -> R.string.home_apps
    }

private val ARROWS = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/**
 * The start page (AC5, AC6): one column of header and blocks. The header scrolls away with the page; focus in the
 * first block scrolls back to the top, so one press of up reaches the gear. OK opens a tile; holding OK calls
 * [onMenu] (the options menu is wired by the activity; [menuOpen] while it is shown). Where the focus goes and comes
 * back to is [HomeFocus]'s. The tiles take their images from [catalog], the ready one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: LauncherViewModel,
    catalog: CachedCatalog,
    onMenu: (Tile) -> Unit,
    onSettings: () -> Unit,
    menuOpen: Boolean = false,
) {
    val state by viewModel.state.collectAsState()
    val focus = rememberHomeFocus()
    var anyFocused by remember { mutableStateOf(false) }
    var firstDrawn by remember { mutableStateOf(false) }
    val items = remember(state) { HomeBlocks.items(state.rows) }
    val first = state.rows.firstOrNull()?.tiles?.firstOrNull()
    val firstKind = state.rows.firstOrNull()?.kind
    val listState = rememberLazyListState()
    val strips = RowKind.entries.associateWith { rememberLazyListState() }
    // Height of the header and the first block's title: the first strip asks the page to show them too (AC6).
    val head = remember { HeadHeight() }
    // AC14: drawn once a tile has focus and the first tile's banner is on screen.
    ReportDrawnWhen { anyFocused && firstDrawn }
    focus.Restore(state.rows, menuOpen, listState, strips)

    val stage = LocalStage.current
    val tiles = remember(first, focus) {
        TileHandlers(
            first = first,
            focus = focus,
            onFocus = {
                if (!anyFocused) anyFocused = true
                focus.focused(it, viewModel.state.value.rows)
            },
            onOpen = viewModel::ok,
            onMenu = onMenu,
            onFirstDrawn = { firstDrawn = true },
        )
    }
    CompositionLocalProvider(LocalCatalog provides catalog, LocalBringIntoViewSpec provides rememberPageScroll()) {
        LazyColumn(
            Modifier.fillMaxSize().background(Night.Bg).pacedArrows(),
            listState,
            contentPadding = PaddingValues(bottom = stage.dp(PAGE_END)),
        ) {
            items(items, key = { it.key }, contentType = { it::class }) { item ->
                when (item) {
                    HomeItem.Header -> Header(
                        onSettings = {
                            focus.pressedGear()
                            onSettings()
                        },
                        gear = Modifier.focusRequester(focus.gear),
                        modifier = Modifier.onSizeChanged { head.header = it.height },
                    )
                    is HomeItem.Title -> BlockTitle(
                        item.kind,
                        if (item.kind == firstKind) Modifier.onSizeChanged { head.title = it.height } else Modifier,
                    )
                    is HomeItem.Strip -> Strip(
                        item.tiles,
                        tiles,
                        strips.getValue(item.kind),
                        head.takeIf { item.kind == firstKind },
                    )
                }
            }
        }
    }
}

/** The page keeps room around the focused tile for the block title above and the title tag and sub line below. */
@Composable
private fun rememberPageScroll(): BringIntoViewSpec {
    val stage = LocalStage.current
    val density = LocalDensity.current
    return remember(stage.scale, density) {
        with(density) { ShortScroll(stage.dp(SCROLL_BEFORE).toPx(), stage.dp(SCROLL_AFTER).toPx()) }
    }
}

/** A held arrow key moves the focus once per [MOVE_GAP_MS] ([MovePace]). */
@Composable
private fun Modifier.pacedArrows(): Modifier {
    val pace = remember { MovePace(MOVE_GAP_MS) }
    return onPreviewKeyEvent { event ->
        event.type == KeyEventType.KeyDown && event.key in ARROWS &&
            !pace.allow(event.nativeKeyEvent.eventTime, event.nativeKeyEvent.repeatCount > 0)
    }
}

/** Measured height (px) of the header and of the first block's title, the room above the first strip. */
private class HeadHeight {
    var header = 0
    var title = 0
    val total: Int get() = header + title
}

/** What the tiles of every block do; [focus] gives each its requester, [first] reports drawn. */
private class TileHandlers(
    val first: Tile?,
    val focus: HomeFocus,
    val onFocus: (Tile) -> Unit,
    val onOpen: (Tile) -> Unit,
    val onMenu: (Tile) -> Unit,
    val onFirstDrawn: () -> Unit,
) {
    @Composable
    fun Card(tile: Tile, modifier: Modifier = Modifier, onFocused: () -> Unit = {}) {
        val isFirst = tile.key == first?.key
        TileCard(
            tile,
            onFocus = {
                onFocus(tile)
                onFocused()
            },
            onTap = { if (Home.opensMenu(tile)) onMenu(tile) else onOpen(tile) },
            onHold = { onMenu(tile) },
            modifier = modifier,
            focusRequester = focus.requester(tile),
            onDrawn = if (isFirst) onFirstDrawn else ({}),
        )
    }
}

@Composable
private fun BlockTitle(kind: RowKind, modifier: Modifier = Modifier) {
    val stage = LocalStage.current
    Text(
        stringResource(kind.title),
        fontSize = stage.sp(ROW_TITLE_SIZE),
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = ROW_TITLE_TRACKING.em,
        color = Night.Text,
        modifier = modifier.padding(
            start = stage.dp(SAFE_X),
            top = stage.dp(BLOCK_GAP),
            bottom = stage.dp(ROW_TITLE_GAP),
        ),
    )
}

/**
 * One block's tiles in a line that scrolls sideways; the focused tile keeps the safe margin to both edges. In the
 * first block ([head] not null) a focused tile also asks the page to show the header above it (AC6): one request,
 * so the page scrolls to the top in the same pace as every other scroll. Left on the first and right on the last
 * tile stay put; without that, right on a short first strip reaches the gear above it.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Strip(tiles: List<Tile>, handlers: TileHandlers, state: LazyListState, head: HeadHeight?) {
    val stage = LocalStage.current
    val density = LocalDensity.current
    val side = remember(stage.scale, density) { with(density) { stage.dp(SAFE_X).toPx() } }
    val scroll = remember(side) { ShortScroll(side, side) }
    val top = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    val showTop: () -> Unit = {
        if (head != null) {
            val rect = Rect(0f, -head.total.toFloat(), size.width.toFloat(), size.height.toFloat())
            scope.launch { top.bringIntoView(rect) }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides scroll) {
        LazyRow(
            modifier = Modifier
                .bringIntoViewRequester(top)
                .onSizeChanged { size = it }
                .focusProperties { onExit = { if (requestedFocusDirection in SIDEWAYS) cancelFocusChange() } }
                .focusGroup(),
            state = state,
            contentPadding = PaddingValues(horizontal = stage.dp(SAFE_X), vertical = stage.dp(ROW_PAD)),
            horizontalArrangement = Arrangement.spacedBy(stage.dp(TILE_GAP)),
        ) {
            items(tiles, key = { it.key.listKey }) { handlers.Card(it, onFocused = showTop) }
        }
    }
}

private val SIDEWAYS = setOf(FocusDirection.Left, FocusDirection.Right)

internal const val SAFE_X = 96
private const val ROW_PAD = 18
private const val BLOCK_GAP = 24
private const val ROW_TITLE_SIZE = 36
private const val ROW_TITLE_TRACKING = -0.02f
private const val ROW_TITLE_GAP = 2
private const val TILE_GAP = 28

// Room kept free above (block title) and below (title tag, sub line and some air) the focused frame, stage px.
private const val SCROLL_BEFORE = 96
private const val SCROLL_AFTER = 128

// Room under the last row, so its title tag does not sit on the screen edge.
private const val PAGE_END = 72
