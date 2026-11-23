package dev.pr0gramm.tv.core.designsystem

import dev.pr0gramm.tv.core.text.StringTable

/**
 * [row] as one line in the language of this table, for tests: an action is its label, a group `# title | sub`,
 * a section `## title count`, an entry `title · meta`.
 */
fun StringTable.line(row: PanelRow): String = when (row) {
    is PanelRow.Action -> this(row.label)
    is PanelRow.Group -> "# ${this(row.title)} | ${row.sub?.let { this(it) }.orEmpty()}"
    is PanelRow.Section -> "## ${this(row.title)} ${row.count}"
    is PanelRow.Entry -> "${row.title} · ${this(row.meta)}"
}

/** The subtitle of [head] as the panel shows it: the parts joined with ` · `. */
fun StringTable.subtitle(head: PanelHead): String = head.subtitle.joinToString(" · ") { this(it) }
