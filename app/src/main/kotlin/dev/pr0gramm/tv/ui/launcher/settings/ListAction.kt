package dev.pr0gramm.tv.ui.launcher.settings

import androidx.compose.ui.input.key.Key
import dev.pr0gramm.tv.core.remotekeys.OK_KEYS

/** What one key event does in the list of a [NightPage]. */
internal sealed interface ListAction {
    /** Not a list key: the page passes the event on. */
    data object Pass : ListAction

    /** Taken without effect: an OK release, a held OK, or a list key on a page without rows. */
    data object Consume : ListAction

    /** Up or Down: the new selection, kept inside the list. */
    data class Select(val index: Int) : ListAction

    /** The first OK press on a row; the repeats of a held OK are [Consume]. */
    data class Ok(val index: Int) : ListAction

    /** Left (-1) or Right (+1) on a row; a page without stepping passes the key on. */
    data class Step(val index: Int, val direction: Int) : ListAction

    companion object {
        /**
         * The action for [key] on a list of [size] rows with [selected] chosen. Only key downs act; the OK key-up
         * is swallowed so the release of a press does not reach anything else.
         */
        fun of(key: Key, down: Boolean, repeatCount: Int, selected: Int, size: Int): ListAction {
            val ok = key in OK_KEYS
            return when {
                !down -> if (ok) Consume else Pass
                ok -> if (repeatCount == 0 && selected < size) Ok(selected) else Consume
                key == Key.DirectionDown -> select(selected + 1, size)
                key == Key.DirectionUp -> select(selected - 1, size)
                key == Key.DirectionLeft -> step(selected, -1, size)
                key == Key.DirectionRight -> step(selected, 1, size)
                else -> Pass
            }
        }

        private fun select(index: Int, size: Int) = if (size == 0) Consume else Select(index.coerceIn(0, size - 1))

        private fun step(selected: Int, direction: Int, size: Int) =
            if (selected < size) Step(selected, direction) else Consume
    }
}
