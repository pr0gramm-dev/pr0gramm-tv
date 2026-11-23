package dev.pr0gramm.tv.ui.browser

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.pr0gramm.tv.data.appsettings.PointerLook
import dev.pr0gramm.tv.data.catalogsync.catalogOf
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.savedpages.CoverSource
import dev.pr0gramm.tv.data.savedpages.SavedPageStore
import dev.pr0gramm.tv.data.savedpages.Shelf
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import dev.pr0gramm.tv.domain.savedpages.CONTINUE_WATCHING_AFTER_MS
import dev.pr0gramm.tv.domain.savedpages.SavedShelf
import dev.pr0gramm.tv.domain.savedpages.Visit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import java.io.File

internal val testSite = Site(
    id = SiteId("a"),
    name = "Alpha",
    url = "https://a.example/",
    description = null,
    category = null,
    tags = emptyList(),
    color = null,
    allowInsecureContent = false,
    allow = emptySet(),
    defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false),
    banner = null,
)
internal const val TEST_PAGE = "https://a.example/de/article"
internal const val TEST_META = """{"titles":["Ein Artikel"]}"""

/** One browser session of [testSite] for the tests, with its settings, both lists and its event sender. */
internal class BrowserEnv(
    val vm: BrowserViewModel,
    val settings: SiteSettingsStore,
    val myList: SavedShelf,
    val watching: SavedShelf,
    val sender: BrowserEventSender,
    private val routed: StateFlow<List<VideoWatched>>,
) {
    /** Waits until the route has stored [event] and every event sent before it; null waits for nothing. */
    suspend fun stored(event: VideoWatched?) {
        if (event != null) routed.first { done -> done.any { it === event } }
    }
}

/**
 * A session whose files live in [dir], on the test dispatcher (also as Main). Callers reset Main after the test. The
 * sender goes to [BrowserEnv.watching] as the app routes it (E5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.browserEnv(dir: File): BrowserEnv {
    val dispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    val scope = CoroutineScope(dispatcher)
    val settings = SiteSettingsStore(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "s.preferences_pb") }),
    )
    val myList = savedList(dir, scope, "list", Shelf.MY_LIST)
    val watching = savedList(dir, scope, "watching", Shelf.CONTINUE_WATCHING)
    val visit = Visit(testSite.url, fromStart = false)
    val sender = BrowserEventSender()
    val routed = MutableStateFlow(emptyList<VideoWatched>())
    scope.launch {
        sender.events.collect { event ->
            watching.save(event.site, event.page, event.meta)
            routed.update { it + event }
        }
    }
    val shelves = BrowserShelves(myList, watching)
    val vm = BrowserViewModel(testSite, settings, flowOf(PointerLook.HAND), shelves, visit, sender)
    return BrowserEnv(vm, settings, myList, watching, sender, routed)
}

/** A fullscreen on [url] that has played [ms] (by default just long enough to count) and is still on. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.play(e: BrowserEnv, url: String, ms: Long = CONTINUE_WATCHING_AFTER_MS) {
    e.vm.watch.started(url)
    e.vm.watch.pageMeta(TEST_META)
    advanceTimeBy(ms)
    runCurrent()
}

private class NoCovers : CoverSource {
    override suspend fun fetch(imageUrl: String, pageUrl: String): ByteArray? = null
}

private fun savedList(dir: File, scope: CoroutineScope, name: String, shelf: Shelf): SavedShelf {
    val store = SavedPageStore.create(File(dir, "$name.json"), File(dir, "images-$name"), shelf, NoCovers(), scope)
    return SavedShelf(store, { catalogOf(testSite).ids }, { 1L })
}
