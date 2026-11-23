package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.data.recentlyopened.OpenedKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

/** E6: the site or app [key], opened from the start page, is in front now, so it counts as used. */
data class OpenedFromHome(val key: OpenedKey)

/**
 * The start page's events for the whole process. `AppContainer` owns the one instance, so an event outlives the
 * screen that sent it.
 */
class LauncherEventSender {
    private val buffer = Channel<OpenedFromHome>(Channel.UNLIMITED)

    /**
     * Each event exactly once, in order; an event waits in the buffer until it is collected. Only one collector may
     * collect it; a second one fails.
     */
    val events: Flow<OpenedFromHome> = buffer.consumeAsFlow()

    internal fun publish(event: OpenedFromHome) {
        buffer.trySend(event)
    }
}
