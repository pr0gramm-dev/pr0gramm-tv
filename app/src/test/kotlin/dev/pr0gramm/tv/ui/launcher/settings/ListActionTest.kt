package dev.pr0gramm.tv.ui.launcher.settings

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Test

class ListActionTest {
    private fun down(key: Key, selected: Int, size: Int = 3, repeat: Int = 0) =
        ListAction.of(key, down = true, repeatCount = repeat, selected = selected, size = size)

    private fun up(key: Key) = ListAction.of(key, down = false, repeatCount = 0, selected = 0, size = 3)

    @Test
    fun downAndUpMoveTheSelectionAndStopAtTheEnds() {
        assertEquals(ListAction.Select(1), down(Key.DirectionDown, selected = 0))
        assertEquals(ListAction.Select(2), down(Key.DirectionDown, selected = 2))
        assertEquals(ListAction.Select(1), down(Key.DirectionUp, selected = 2))
        assertEquals(ListAction.Select(0), down(Key.DirectionUp, selected = 0))
    }

    @Test
    fun okActsOnTheSelectedRowOnlyOnTheFirstPress() {
        for (key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) {
            assertEquals(ListAction.Ok(1), down(key, selected = 1))
            assertEquals(ListAction.Consume, down(key, selected = 1, repeat = 1))
        }
    }

    @Test
    fun okReleaseIsSwallowedOtherReleasesPass() {
        assertEquals(ListAction.Consume, up(Key.DirectionCenter))
        assertEquals(ListAction.Pass, up(Key.DirectionDown))
    }

    @Test
    fun leftAndRightStepTheSelectedRow() {
        assertEquals(ListAction.Step(1, -1), down(Key.DirectionLeft, selected = 1))
        assertEquals(ListAction.Step(1, 1), down(Key.DirectionRight, selected = 1))
    }

    @Test
    fun withoutRowsTheListKeysDoNothingAndKeepTheSelection() {
        for (key in listOf(Key.DirectionDown, Key.DirectionUp, Key.DirectionCenter, Key.DirectionRight)) {
            assertEquals(ListAction.Consume, down(key, selected = 1, size = 0))
        }
    }

    @Test
    fun otherKeysPass() {
        assertEquals(ListAction.Pass, down(Key.Back, selected = 0))
    }
}
