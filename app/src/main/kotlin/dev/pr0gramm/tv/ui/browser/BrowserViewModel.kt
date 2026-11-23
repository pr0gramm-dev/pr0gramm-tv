package dev.pr0gramm.tv.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.data.appsettings.PointerLook
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.savedpages.PageMeta
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.sitesettings.SiteSettingsStore
import dev.pr0gramm.tv.domain.savedpages.CONTINUE_WATCHING_AFTER_MS
import dev.pr0gramm.tv.domain.savedpages.Page
import dev.pr0gramm.tv.domain.savedpages.SaveResult
import dev.pr0gramm.tv.domain.savedpages.Visit
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The actions of the page menu (AC7); [PageMenu] puts them in order. */
enum class PageMenuItem { ADD, REMOVE, POINTER_ON, POINTER_OFF }

/**
 * What the screen shows besides the page. [menu] is null while closed. [pointer] starts from the site's
 * stored options; switching it in the menu stores it there too, so the site opens that way next time.
 * [look] is how the pointer looks (app setting). [progress] is the load percentage; [message] is shown once, then
 * cleared with [BrowserViewModel.messageShown].
 */
data class BrowserState(
    val menu: PageMenu?,
    val pointer: Boolean,
    val look: PointerLook,
    val progress: Int,
    val message: UiText?,
)

sealed interface BrowserEvent {
    /** The screen ends and the start page shows again. */
    data object Close : BrowserEvent

    /** The pointer mode changed; the page has to be told. */
    data class Pointer(val on: Boolean) : BrowserEvent
}

/**
 * Page menu and load state of one browser session. It never sees the WebView: the Activity passes the page
 * address and the page-meta.js result in.
 */
class BrowserViewModel(
    private val site: Site,
    private val settings: SiteSettingsStore,
    looks: Flow<PointerLook>,
    shelves: BrowserShelves,
    private val visit: Visit,
    private val sender: BrowserEventSender,
) : ViewModel() {
    private val mutable = MutableStateFlow(
        BrowserState(
            menu = null,
            pointer = site.defaults.control == Control.POINTER,
            look = PointerLook.HAND,
            progress = 0,
            message = null,
        ),
    )
    private val myList = shelves.myList
    private val continueWatching = shelves.continueWatching
    private val channel = Channel<BrowserEvent>(Channel.BUFFERED)

    /** The video fullscreen of this session in "Weiterschauen". */
    val watch = WatchRecord()

    val state: StateFlow<BrowserState> = mutable.asStateFlow()
    val events: Flow<BrowserEvent> = channel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val pointer = settings.of(site).first().control == Control.POINTER
            mutable.update { it.copy(pointer = pointer) }
        }
        viewModelScope.launch { looks.collect { look -> mutable.update { it.copy(look = look) } } }
    }

    /** Shows the [PageMenu] for the [page] shown, with what is saved on the site. */
    fun openMenu(page: OpenPage) {
        viewModelScope.launch {
            val watched = continueWatching.current()
            val mine = myList.current()
            mutable.update { it.copy(menu = PageMenu.of(site, page, it.pointer, watched, mine)) }
        }
    }

    fun dismissMenu() = mutable.update { it.copy(menu = null) }

    /** Switches between pointer and focus mode (OK held), as the menu item does. */
    fun togglePointer() {
        val item = if (state.value.pointer) PageMenuItem.POINTER_OFF else PageMenuItem.POINTER_ON
        pick(item, url = null, metaJson = null)
    }

    /** [metaJson] is the result of page-meta.js; only [PageMenuItem.ADD] reads it. */
    fun pick(item: PageMenuItem, url: String?, metaJson: String?) {
        dismissMenu()
        viewModelScope.launch {
            when (item) {
                PageMenuItem.POINTER_ON, PageMenuItem.POINTER_OFF -> {
                    // Stored for the site, so it opens that way next time.
                    val on = item == PageMenuItem.POINTER_ON
                    mutable.update { it.copy(pointer = on) }
                    channel.send(BrowserEvent.Pointer(on))
                    settings.update(site) { it.copy(control = if (on) Control.POINTER else Control.FOCUS) }
                }
                PageMenuItem.ADD -> say(myList.save(site, visit.page(url), PageMeta.parse(metaJson)))
                PageMenuItem.REMOVE -> url?.let { say(myList.remove(SavedPageKey.of(site.id, it))) }
            }
        }
    }

    private suspend fun say(result: SaveResult) {
        mutable.update { it.copy(message = SaveMessages.of(result, site)) }
    }

    /**
     * Sends [VideoWatched] (E5, the page goes to "Weiterschauen") once a video has played [CONTINUE_WATCHING_AFTER_MS]
     * in fullscreen: shorter looks (a wrong episode, a quick peek) are left out. It sends while the video plays,
     * because the screen can close without leaving fullscreen (Back held, the Home key), and again when a counted
     * fullscreen ends, which moves the page to the front. The [sender] keeps a sent event when the screen closes.
     * A metaJson is the page-meta.js result.
     */
    inner class WatchRecord {
        private var playing: Page? = null
        private var meta: String? = null
        private var timer: Job? = null
        private var counted = false

        /** A video went fullscreen on the page at [url]: remember the address, record it after the wait. */
        fun started(url: String?) {
            val page = visit.page(url)
            timer?.cancel()
            playing = page
            meta = null
            counted = false
            timer = viewModelScope.launch {
                delay(CONTINUE_WATCHING_AFTER_MS)
                counted = true
                sender.publish(VideoWatched(site, page, PageMeta.parse(meta)))
            }
        }

        /** The page-meta.js result read when fullscreen started; the timed record uses it. */
        fun pageMeta(metaJson: String?) {
            if (playing != null) meta = metaJson
        }

        /** The event sent; null without a start or before [CONTINUE_WATCHING_AFTER_MS]. */
        fun ended(metaJson: String?): VideoWatched? {
            val page = playing?.takeIf { counted }
            playing = null
            timer?.cancel()
            timer = null
            return page?.let { VideoWatched(site, it, PageMeta.parse(metaJson)) }?.also(sender::publish)
        }
    }

    fun messageShown() = mutable.update { it.copy(message = null) }

    fun progress(percent: Int) = mutable.update { it.copy(progress = percent) }

    /** The page's renderer died: the screen has to close. */
    fun rendererGone() {
        channel.trySend(BrowserEvent.Close)
    }
}
