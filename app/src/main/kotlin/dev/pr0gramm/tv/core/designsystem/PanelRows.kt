package dev.pr0gramm.tv.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text

/**
 * The fixed top of a [SidePanel]: a [kicker] chip that says what the panel belongs to ("Current page"), an
 * optional [badge] chip, a 16:9 [picture] (a Coil model, none when null), the [title] and the line below it: the
 * [subtitle] parts joined with ` · `. Blank texts are left out.
 */
data class PanelHead(
    val kicker: UiText,
    val title: String,
    val subtitle: List<UiText>,
    val picture: Any? = null,
    val badge: UiText? = null,
)

/** One row of a [SidePanel], top to bottom. Only [Action] and [Entry] take the selection. */
sealed interface PanelRow {
    /** Something to do, e.g. "Switch to Pointer". */
    data class Action(val label: UiText) : PanelRow

    /** A heading over the rows that follow, with a rule above it, e.g. "Saved on Alpha"; [sub] below it. */
    data class Group(val title: UiText, val sub: UiText? = null) : PanelRow

    /** A small heading inside a group with the number of its entries, e.g. "Continue Watching 2". */
    data class Section(val title: UiText, val count: Int) : PanelRow

    /** A saved page: its cover [picture] (a Coil model), its own title and a grey line below. */
    data class Entry(val title: String, val meta: UiText, val picture: Any?) : PanelRow

    val selectable: Boolean get() = this is Action || this is Entry
}

/** The keys line under every menu panel; `*words*` are the keys. */
val PANEL_KEYS: UiText = text(R.string.panel_keys)

/** How the selection moves through the rows of a [SidePanel]. Pure. */
internal object PanelRows {
    /**
     * The rows that move into view when [selected] gets the selection: the headings right above it come along,
     * the first selectable row brings the top of the list, the last one the end (index `rows.size`, the keys line).
     */
    fun reveal(rows: List<PanelRow>, selected: Int): IntRange {
        val selectable = rows.indices.filter { rows[it].selectable }
        var top = selected
        while (top > 0 && !rows[top - 1].selectable) top--
        return when (selected) {
            selectable.firstOrNull() -> 0..selected
            selectable.lastOrNull() -> top..rows.size
            else -> top..selected
        }
    }

    /** The next selectable row from [from] in [direction] (-1 or +1); [from] when there is none. */
    fun step(rows: List<PanelRow>, from: Int, direction: Int): Int {
        var index = from + direction
        while (index in rows.indices) {
            if (rows[index].selectable) return index
            index += direction
        }
        return from
    }
}

/** [text] with the `*marked*` words in [bright] and medium weight; the rest keeps the colour of its Text. */
fun marked(text: String, bright: Color, weight: FontWeight = FontWeight.Medium): AnnotatedString =
    buildAnnotatedString {
        text.split('*').forEachIndexed { index, part ->
            if (index % 2 == 0) {
                append(part)
            } else {
                withStyle(SpanStyle(color = bright, fontWeight = weight)) { append(part) }
            }
        }
    }
