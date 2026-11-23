package dev.pr0gramm.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.pr0gramm.tv.core.remotekeys.MENU_KEY
import dev.pr0gramm.tv.core.remotekeys.OK_KEYS
import dev.pr0gramm.tv.core.text.UiText

/**
 * The menu panel from the right over a dimmed screen, used by the tile menu and the page menu. The [head] stays;
 * the [rows] and the [keys] line below them scroll, and the selected row is kept in view ([PanelRows.reveal]).
 * One node takes the focus and moves the selection itself over the selectable rows, so the focus cannot leave the
 * panel. A pick reports the row's index to [onPick] and needs an OK press that starts here: the OK key still held
 * from the hold that opened the menu picks nothing. Back is left to the screen; the gear key ([MENU_KEY]) calls
 * [onClose] on release.
 */
@Composable
fun SidePanel(
    head: PanelHead?,
    rows: List<PanelRow>,
    keys: UiText,
    onPick: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
) {
    val stage = LocalStage.current
    val requester = remember { FocusRequester() }
    val selection = remember(rows) { Selection(rows) }
    val list = rememberLazyListState()
    LaunchedEffect(Unit) { requester.requestFocus() }
    LaunchedEffect(rows, selection.selected) { list.reveal(PanelRows.reveal(rows, selection.selected)) }
    Box(
        modifier
            .fillMaxSize()
            .background(Night.Bg.copy(alpha = SCRIM))
            .focusRequester(requester)
            .onPreviewKeyEvent { event -> selection.key(event, onPick, onClose) }
            .focusable(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(stage.dp(PANEL_WIDTH))
                .background(Night.Surface1)
                .drawBehind { drawRect(Night.Text, size = Size(stage.dp(EDGE).toPx(), size.height)) }
                .padding(
                    start = stage.dp(PANEL_PAD_X),
                    end = stage.dp(PANEL_PAD_X),
                    top = stage.dp(PANEL_PAD_TOP),
                    bottom = stage.dp(PANEL_PAD_BOTTOM),
                ),
        ) {
            if (head != null) PanelHeadView(head)
            LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(stage.dp(ROW_GAP))) {
                itemsIndexed(rows) { index, row -> PanelRowView(row, index == selection.selected) }
                item { PanelKeys(keys) }
            }
        }
    }
}

/** The selected row of [rows] and what a key does to it; it starts on the first selectable row. */
private class Selection(private val rows: List<PanelRow>) {
    var selected by mutableIntStateOf(rows.indexOfFirst { it.selectable }.coerceAtLeast(0))
        private set
    private var pressed = false

    /** True when the panel used [event]; Back is left to the screen. */
    fun key(event: KeyEvent, onPick: (Int) -> Unit, onClose: () -> Unit): Boolean {
        if (event.key == Key.Back) return false
        val down = event.type == KeyEventType.KeyDown
        when {
            down && event.key == Key.DirectionDown -> selected = PanelRows.step(rows, selected, +1)
            down && event.key == Key.DirectionUp -> selected = PanelRows.step(rows, selected, -1)
            event.key == MENU_KEY -> if (!down) onClose()
            event.key !in OK_KEYS -> Unit
            down -> pressed = pressed || event.nativeKeyEvent.repeatCount == 0
            else -> {
                if (pressed && rows.getOrNull(selected)?.selectable == true) onPick(selected)
                pressed = false
            }
        }
        return true
    }
}

/** Scrolls as little as needed so the items in [range] are in view; the first item wins when they do not fit. */
private suspend fun LazyListState.reveal(range: IntRange) {
    val info = layoutInfo
    val start = info.viewportStartOffset
    val end = info.viewportEndOffset
    val items = info.visibleItemsInfo
    val top = items.firstOrNull { it.index == range.first }
    val bottom = items.firstOrNull { it.index == range.last }
    when {
        range.first == 0 -> animateScrollToItem(0)
        top == null || top.offset < start -> animateScrollToItem(range.first)
        bottom == null -> animateScrollToItem(range.first)
        bottom.offset + bottom.size > end ->
            animateScrollBy(minOf(bottom.offset + bottom.size - end, top.offset - start).toFloat())
    }
}

private const val SCRIM = 0.78f
private const val EDGE = 6
private const val PANEL_WIDTH = 760
private const val PANEL_PAD_X = 40
private const val PANEL_PAD_TOP = 56
private const val PANEL_PAD_BOTTOM = 40
private const val ROW_GAP = 4
