package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.savedpages.PageMeta
import dev.pr0gramm.tv.domain.savedpages.CONTINUE_WATCHING_AFTER_MS
import dev.pr0gramm.tv.domain.savedpages.Page
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow

/**
 * E5: a video played [CONTINUE_WATCHING_AFTER_MS] in fullscreen on [page] of [site]; [meta] is the page's page-meta.js
 * result.
 */
data class VideoWatched(val site: Site, val page: Page, val meta: PageMeta)

/**
 * The browser's events for the whole process. `AppContainer` owns the one instance, so an event outlives the screen
 * that sent it.
 */
class BrowserEventSender {
    private val buffer = Channel<VideoWatched>(Channel.UNLIMITED)

    /**
     * Each event exactly once, in order; an event waits in the buffer until it is collected. Only one collector may
     * collect it; a second one fails.
     */
    val events: Flow<VideoWatched> = buffer.consumeAsFlow()

    internal fun publish(event: VideoWatched) {
        buffer.trySend(event)
    }
}
