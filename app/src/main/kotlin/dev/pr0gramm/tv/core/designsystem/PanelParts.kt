package dev.pr0gramm.tv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.asString
import androidx.compose.foundation.layout.Row as LayoutRow

/** The kicker, then the picture next to the title and its line (seitenleisten.html). */
@Composable
internal fun PanelHeadView(head: PanelHead) {
    val stage = LocalStage.current
    val kicker = head.kicker.asString()
    val badge = head.badge?.asString()
    val subtitle = head.subtitle.map { it.asString() }.joinToString(" · ")
    if (kicker.isNotBlank() || badge != null) {
        LayoutRow(
            Modifier.padding(bottom = stage.dp(KICKER_GAP)),
            horizontalArrangement = Arrangement.spacedBy(stage.dp(KICKER_PAD_X)),
        ) {
            if (kicker.isNotBlank()) Chip(kicker, Night.Surface3, Night.Text2)
            if (badge != null) Chip(badge, Night.Text, Night.Bg)
        }
    }
    LayoutRow(
        Modifier.padding(bottom = stage.dp(HEAD_GAP)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stage.dp(HEAD_PIC_GAP)),
    ) {
        if (head.picture != null) Picture(head.picture, stage.dp(HEAD_PIC_W), stage.dp(HEAD_PIC_H), HEAD_PIC_CUT)
        Column {
            Text(
                head.title,
                fontSize = stage.sp(HEAD_TITLE_SIZE),
                fontWeight = FontWeight.Bold,
                color = Night.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    fontSize = stage.sp(HEAD_SUB_SIZE),
                    color = Night.Text2,
                    modifier = Modifier.padding(top = stage.dp(HEAD_SUB_TOP)),
                )
            }
        }
    }
}

@Composable
internal fun PanelRowView(row: PanelRow, selected: Boolean) {
    when (row) {
        is PanelRow.Action -> ActionRow(row.label.asString(), selected)
        is PanelRow.Group -> GroupRow(row)
        is PanelRow.Section -> SectionRow(row)
        is PanelRow.Entry -> EntryRow(row, selected)
    }
}

/** The keys line at the end of the list: `*words*` are the keys, a little brighter. */
@Composable
internal fun PanelKeys(keys: UiText) {
    val stage = LocalStage.current
    val rule = Night.Surface3
    Text(
        marked(keys.asString(), Night.Text2, FontWeight.SemiBold),
        fontSize = stage.sp(KEYS_SIZE),
        color = Night.Text3,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = stage.dp(KEYS_TOP))
            .drawBehind { drawRect(rule, size = Size(size.width, stage.dp(RULE).toPx())) }
            .padding(start = stage.dp(INDENT), top = stage.dp(KEYS_PAD_TOP)),
    )
}

@Composable
private fun ActionRow(label: String, selected: Boolean) {
    val stage = LocalStage.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = stage.dp(ACTION_HEIGHT))
            .clip(stage.cutShape(ROW_CUT))
            .background(if (selected) Night.Focus else Color.Transparent)
            .padding(horizontal = stage.dp(INDENT)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, fontSize = stage.sp(ACTION_SIZE), fontWeight = FontWeight.Medium, color = textOf(selected))
    }
}

@Composable
private fun GroupRow(group: PanelRow.Group) {
    val stage = LocalStage.current
    val rule = Night.Surface3
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = stage.dp(GROUP_TOP))
            .drawBehind { drawRect(rule, size = Size(size.width, stage.dp(RULE).toPx())) }
            .padding(start = stage.dp(INDENT), top = stage.dp(GROUP_PAD_TOP)),
    ) {
        Text(group.title.asString(), fontSize = stage.sp(GROUP_SIZE), fontWeight = FontWeight.Bold, color = Night.Text)
        val sub = group.sub?.asString().orEmpty()
        if (sub.isNotBlank()) {
            Text(
                sub,
                fontSize = stage.sp(GROUP_SUB_SIZE),
                color = Night.Text3,
                modifier = Modifier.padding(top = stage.dp(GROUP_SUB_TOP)),
            )
        }
    }
}

@Composable
private fun SectionRow(section: PanelRow.Section) {
    val stage = LocalStage.current
    LayoutRow(
        Modifier.padding(start = stage.dp(INDENT), top = stage.dp(SECTION_TOP), bottom = stage.dp(SECTION_BOTTOM)),
        horizontalArrangement = Arrangement.spacedBy(stage.dp(SECTION_GAP)),
    ) {
        Text(
            section.title.asString().uppercase(),
            fontSize = stage.sp(SECTION_SIZE),
            fontWeight = FontWeight.SemiBold,
            color = Night.Text3,
        )
        Text(section.count.toString(), fontSize = stage.sp(SECTION_SIZE), color = Night.Text3)
    }
}

/** A saved page: cover, title and line; equal padding on all sides (seitenleisten.html). */
@Composable
private fun EntryRow(entry: PanelRow.Entry, selected: Boolean) {
    val stage = LocalStage.current
    LayoutRow(
        Modifier
            .fillMaxWidth()
            .clip(stage.cutShape(ROW_CUT))
            .background(if (selected) Night.Focus else Color.Transparent)
            .padding(stage.dp(ENTRY_PAD)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(stage.dp(ENTRY_GAP)),
    ) {
        Picture(entry.picture, stage.dp(ENTRY_PIC_W), stage.dp(ENTRY_PIC_H), ENTRY_PIC_CUT)
        Column {
            Text(
                entry.title,
                fontSize = stage.sp(ENTRY_TITLE_SIZE),
                fontWeight = FontWeight.Medium,
                color = textOf(selected),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.meta.asString(),
                fontSize = stage.sp(ENTRY_META_SIZE),
                color = if (selected) Night.OnFocus else Night.Text3,
                modifier = Modifier.padding(top = stage.dp(ENTRY_META_TOP)),
            )
        }
    }
}

/** A small capital label on a coloured field, e.g. the kicker "APP" or the badge "UPDATE". */
@Composable
private fun Chip(label: String, field: Color, text: Color) {
    val stage = LocalStage.current
    Text(
        label.uppercase(),
        fontSize = stage.sp(KICKER_SIZE),
        fontWeight = FontWeight.Bold,
        color = text,
        modifier = Modifier
            .background(field)
            .padding(horizontal = stage.dp(KICKER_PAD_X), vertical = stage.dp(KICKER_PAD_Y)),
    )
}

/** A 16:9 picture that fills its frame like the covers; an empty frame without one. */
@Composable
private fun Picture(model: Any?, width: Dp, height: Dp, cut: Int) {
    val stage = LocalStage.current
    val frame = Modifier
        .size(width, height)
        .clip(stage.cutShape(cut))
        .background(Night.Surface2)
    if (model == null) Box(frame) else AsyncImage(model, null, frame, contentScale = ContentScale.Crop)
}

private fun textOf(selected: Boolean) = if (selected) Night.OnFocus else Night.Text

private const val INDENT = 24
private const val RULE = 2
private const val ROW_CUT = 10
private const val KICKER_SIZE = 18
private const val KICKER_GAP = 18
private const val KICKER_PAD_X = 12
private const val KICKER_PAD_Y = 5
private const val HEAD_GAP = 26
private const val HEAD_PIC_GAP = 22
private const val HEAD_PIC_W = 192
private const val HEAD_PIC_H = 108
private const val HEAD_PIC_CUT = 14
private const val HEAD_TITLE_SIZE = 34
private const val HEAD_SUB_SIZE = 22
private const val HEAD_SUB_TOP = 6
private const val ACTION_HEIGHT = 60
private const val ACTION_SIZE = 26
private const val GROUP_TOP = 26
private const val GROUP_PAD_TOP = 22
private const val GROUP_SIZE = 26
private const val GROUP_SUB_SIZE = 20
private const val GROUP_SUB_TOP = 4
private const val SECTION_TOP = 14
private const val SECTION_BOTTOM = 4
private const val SECTION_GAP = 12
private const val SECTION_SIZE = 18
private const val ENTRY_PAD = 12
private const val ENTRY_GAP = 18
private const val ENTRY_PIC_W = 120
private const val ENTRY_PIC_H = 68
private const val ENTRY_PIC_CUT = 8
private const val ENTRY_TITLE_SIZE = 24
private const val ENTRY_META_SIZE = 19
private const val ENTRY_META_TOP = 2
private const val KEYS_TOP = 20
private const val KEYS_PAD_TOP = 18
private const val KEYS_SIZE = 19
