package dev.pr0gramm.tv.ui.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.cutShape
import dev.pr0gramm.tv.core.text.asString
import androidx.compose.foundation.layout.Row as LayoutRow

/**
 * A full-screen page of the design system: title and hint, below them the [content] (a list of [size] rows) on the
 * left and the [help] of the selected row on the right. The page takes the focus and owns the selection: Up and
 * Down move it, OK reports the selected row to [onOk] once per press (a held OK does not repeat), and with
 * [onStep] Left and Right report -1 and +1. With no rows (size 0) the list keys do nothing and the help is hidden.
 * The selection survives a recreation of the activity, for example after a language switch (AC7). A caller that
 * leaves the page for another composable keeps the row with its own [selection].
 */
@Composable
internal fun NightPage(
    title: String,
    hint: String,
    size: Int,
    modifier: Modifier = Modifier,
    help: (selected: Int) -> RowHelp? = { null },
    onOk: (selected: Int) -> Unit = {},
    onStep: ((selected: Int, direction: Int) -> Unit)? = null,
    selection: MutableIntState = rememberSaveable { mutableIntStateOf(0) },
    content: @Composable (selected: Int) -> Unit,
) {
    val stage = LocalStage.current
    val requester = remember { FocusRequester() }
    var selected by selection
    LaunchedEffect(Unit) { requester.requestFocus() }
    val keys = Modifier.onPreviewKeyEvent { event ->
        val down = event.type == KeyEventType.KeyDown
        val repeat = event.nativeKeyEvent.repeatCount
        when (val action = ListAction.of(event.key, down, repeat, selected, size)) {
            ListAction.Pass -> return@onPreviewKeyEvent false
            ListAction.Consume -> Unit
            is ListAction.Select -> selected = action.index
            is ListAction.Ok -> onOk(action.index)
            is ListAction.Step -> onStep?.invoke(action.index, action.direction) ?: return@onPreviewKeyEvent false
        }
        true
    }
    Box(modifier.focusRequester(requester).then(keys).fillMaxSize().background(Night.Bg).focusable()) {
        Column(Modifier.padding(start = stage.dp(SAFE_X), top = stage.dp(PAGE_TOP))) {
            NightTitle(title, hint)
            LayoutRow(horizontalArrangement = Arrangement.spacedBy(stage.dp(HELP_GAP))) {
                Column(Modifier.width(stage.dp(LIST_WIDTH))) { content(selected) }
                if (selected < size) help(selected)?.let { HelpPanel(it) }
            }
        }
    }
}

/** The title of a page and the hint below it, with the space to the content. */
@Composable
internal fun NightTitle(title: String, hint: String) {
    val stage = LocalStage.current
    Text(title, fontSize = stage.sp(TITLE_SIZE), fontWeight = FontWeight.ExtraBold, color = Night.Text)
    Text(
        hint,
        fontSize = stage.sp(HINT_SIZE),
        color = Night.Text2,
        modifier = Modifier.padding(top = stage.dp(HINT_TOP), bottom = stage.dp(HINT_BOTTOM)),
    )
}

@Composable
private fun HelpPanel(help: RowHelp) {
    val stage = LocalStage.current
    val rule = Night.Surface3
    Column(
        Modifier
            .width(stage.dp(HELP_WIDTH))
            .drawBehind { drawRect(rule, size = Size(stage.dp(RULE).toPx(), size.height)) }
            .padding(start = stage.dp(HELP_PAD), top = stage.dp(HELP_PAD_Y), bottom = stage.dp(HELP_PAD_Y)),
    ) {
        Text(help.label.asString(), fontSize = stage.sp(ITEM_SIZE), fontWeight = FontWeight.Medium, color = Night.Text)
        val body = help.help
        body.intro?.let { HelpText(it.asString()) }
        body.topics.forEach {
            Column(Modifier.padding(top = stage.dp(HELP_BLOCK_GAP))) {
                NameAndText(it.name.asString(), it.text.asString(), active = false)
            }
        }
        body.options.forEach { HelpChoice(it) }
        body.note?.let { HelpText(it.asString()) }
        Text(
            help.scope.asString(),
            fontSize = stage.sp(SCOPE_SIZE),
            color = Night.Text3,
            modifier = Modifier.padding(top = stage.dp(HELP_SCOPE_TOP)),
        )
    }
}

@Composable
private fun HelpText(text: String) {
    val stage = LocalStage.current
    Text(
        text,
        fontSize = stage.sp(VALUE_SIZE),
        lineHeight = stage.sp(HELP_LINE),
        color = Night.Text2,
        modifier = Modifier.padding(top = stage.dp(HELP_BLOCK_GAP)),
    )
}

/** One choice: a square (white for the one set now), its name and what it does below it. */
@Composable
private fun HelpChoice(option: HelpOption) {
    val stage = LocalStage.current
    LayoutRow(Modifier.padding(top = stage.dp(HELP_BLOCK_GAP))) {
        Box(
            Modifier
                .padding(top = stage.dp(MARK_TOP), end = stage.dp(MARK_GAP))
                .size(stage.dp(MARK))
                .background(if (option.active) Night.Text else Night.Surface3),
        )
        Column { NameAndText(option.name.asString(), option.text.asString(), option.active) }
    }
}

/** A name and the text below it; [active] adds `help_active` ("active") after the name. */
@Composable
private fun NameAndText(name: String, text: String, active: Boolean) {
    val stage = LocalStage.current
    LayoutRow(verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = stage.sp(VALUE_SIZE), fontWeight = FontWeight.Medium, color = Night.Text)
        if (active) {
            Text(
                stringResource(R.string.help_active),
                fontSize = stage.sp(SCOPE_SIZE),
                color = Night.Text3,
                modifier = Modifier.padding(start = stage.dp(MARK_GAP)),
            )
        }
    }
    Text(
        text,
        fontSize = stage.sp(VALUE_SIZE),
        lineHeight = stage.sp(HELP_LINE),
        color = Night.Text2,
        modifier = Modifier.padding(top = stage.dp(CHOICE_TEXT_TOP)),
    )
}

/**
 * A list item of the design system (system.html, ListItem): label left, the value right as text, switch or
 * ◀ value ▶; a [ItemStyle.DANGER] row is red. The focused row turns orange, its controls dark.
 */
@Composable
internal fun NightItem(
    label: String,
    value: String,
    selected: Boolean,
    style: ItemStyle = ItemStyle.TEXT,
    on: Boolean = false,
) {
    val stage = LocalStage.current
    val danger = style == ItemStyle.DANGER
    val fill = when {
        selected && danger -> Night.Danger
        selected -> Night.Focus
        else -> Night.Surface1
    }
    val ink = when {
        selected -> Night.OnFocus
        danger -> Night.Danger
        else -> Night.Text
    }
    val valueInk = if (selected) Night.OnFocus else Night.Text2
    LayoutRow(
        Modifier
            .padding(bottom = stage.dp(ITEM_GAP))
            .fillMaxWidth()
            .clip(stage.cutShape(ITEM_CUT))
            .background(fill)
            .padding(horizontal = stage.dp(ITEM_PAD_X), vertical = stage.dp(ITEM_PAD_Y)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = stage.sp(ITEM_SIZE),
            fontWeight = FontWeight.Medium,
            color = ink,
            modifier = Modifier.weight(1f),
        )
        when (style) {
            ItemStyle.SWITCH -> Switch(on, selected)
            ItemStyle.STEPPER -> LayoutRow(horizontalArrangement = Arrangement.spacedBy(stage.dp(STEP_GAP))) {
                val arrows = Modifier.alpha(if (selected) 1f else 0f)
                Text("◀", fontSize = stage.sp(VALUE_SIZE), color = valueInk, modifier = arrows)
                Text(value, fontSize = stage.sp(VALUE_SIZE), color = valueInk)
                Text("▶", fontSize = stage.sp(VALUE_SIZE), color = valueInk, modifier = arrows)
            }
            ItemStyle.TEXT -> Text(value, fontSize = stage.sp(VALUE_SIZE), color = valueInk)
            ItemStyle.DANGER -> Unit
        }
    }
}

/** White when on (orange is only the focus); on the orange row the track turns dark. */
@Composable
private fun Switch(on: Boolean, selected: Boolean) {
    val stage = LocalStage.current
    val track = when {
        selected && on -> Night.OnFocus
        selected -> Night.OnFocus.copy(alpha = OFF_TRACK_ALPHA)
        on -> Night.Text
        else -> Night.Surface3
    }
    val knob = when {
        selected && on -> Night.Focus
        on -> Night.Bg
        else -> Night.Text2
    }
    Box(Modifier.size(stage.dp(SWITCH_W), stage.dp(SWITCH_H)).background(track)) {
        Box(
            Modifier
                .offset(x = stage.dp(if (on) KNOB_ON else KNOB_INSET), y = stage.dp(KNOB_INSET))
                .size(stage.dp(KNOB))
                .background(knob),
        )
    }
}

internal const val SAFE_X = 96
internal const val PAGE_TOP = 130
private const val TITLE_SIZE = 64
private const val HINT_SIZE = 24
private const val HINT_TOP = 8
private const val HINT_BOTTOM = 40
private const val LIST_WIDTH = 960
private const val HELP_GAP = 96
private const val HELP_WIDTH = 672
private const val RULE = 4
private const val HELP_PAD = 36
private const val HELP_PAD_Y = 8
private const val HELP_LINE = 36
private const val HELP_BLOCK_GAP = 24
private const val HELP_SCOPE_TOP = 40
private const val CHOICE_TEXT_TOP = 4
private const val MARK = 12
private const val MARK_TOP = 11
private const val MARK_GAP = 16
private const val SCOPE_SIZE = 20
private const val ITEM_GAP = 8
private const val ITEM_CUT = 10
private const val ITEM_PAD_X = 24
private const val ITEM_PAD_Y = 20
private const val ITEM_SIZE = 28
private const val VALUE_SIZE = 24
private const val STEP_GAP = 16
private const val SWITCH_W = 64
private const val SWITCH_H = 32
private const val KNOB = 24
private const val KNOB_INSET = 4
private const val KNOB_ON = 36
private const val OFF_TRACK_ALPHA = 0.25f
