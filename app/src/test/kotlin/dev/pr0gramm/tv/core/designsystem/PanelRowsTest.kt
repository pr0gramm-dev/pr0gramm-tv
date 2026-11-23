package dev.pr0gramm.tv.core.designsystem

import dev.pr0gramm.tv.core.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelRowsTest {
    private val rows = listOf(
        PanelRow.Action(UiText.Plain("A")),
        PanelRow.Action(UiText.Plain("B")),
        PanelRow.Group(UiText.Plain("Saved"), UiText.Plain("OK opens")),
        PanelRow.Section(UiText.Plain("Continue Watching"), 1),
        PanelRow.Entry("W", UiText.Plain("today"), null),
        PanelRow.Section(UiText.Plain("My List"), 1),
        PanelRow.Entry("M", UiText.Plain("saved"), null),
    )

    @Test
    fun stepsSkipHeadingsAndStopAtTheEnds() {
        assertEquals(4, PanelRows.step(rows, 1, +1))
        assertEquals(1, PanelRows.step(rows, 4, -1))
        assertEquals(6, PanelRows.step(rows, 6, +1))
        assertEquals(0, PanelRows.step(rows, 0, -1))
    }

    @Test
    fun revealBringsTheHeadingsAboveTheTopAndTheKeysLine() {
        assertEquals(0..0, PanelRows.reveal(rows, 0))
        assertEquals(2..4, PanelRows.reveal(rows, 4))
        assertEquals(5..7, PanelRows.reveal(rows, 6))
    }
}
