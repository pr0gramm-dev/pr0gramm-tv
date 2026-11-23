package dev.pr0gramm.tv.ui.browser

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.PANEL_KEYS
import dev.pr0gramm.tv.core.designsystem.PanelHead
import dev.pr0gramm.tv.core.designsystem.PanelRow
import dev.pr0gramm.tv.core.designsystem.SidePanel
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.SavedPageKey
import dev.pr0gramm.tv.data.savedpages.Shelf
import dev.pr0gramm.tv.domain.savedpages.DayClock
import dev.pr0gramm.tv.domain.savedpages.SavedLists
import dev.pr0gramm.tv.domain.savedpages.SavedSections
import dev.pr0gramm.tv.domain.savedpages.title
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

internal val PageMenuItem.label: UiText
    get() = when (this) {
        PageMenuItem.ADD -> text(R.string.page_menu_add)
        PageMenuItem.REMOVE -> text(R.string.menu_remove_from, Shelf.MY_LIST.title)
        PageMenuItem.POINTER_ON -> text(R.string.page_menu_pointer)
        PageMenuItem.POINTER_OFF -> text(R.string.page_menu_focus)
    }

/** What a row of the page menu does: one of the [PageMenuItem]s, or open a saved page here. */
sealed interface PagePick {
    data class Item(val item: PageMenuItem) : PagePick

    data class Open(val entry: SavedPage) : PagePick
}

/** The page the WebView shows: its address and its document title, either unknown while it loads. */
data class OpenPage(val url: String?, val title: String?)

/** A row of the page menu and what OK on it does; null for a heading. */
data class PageMenuLine(val row: PanelRow, val pick: PagePick?)

/**
 * The page menu (AC7, seitenleisten.html): the head names the open page ([title], and the site in [subtitle] when
 * the title is the page's own), then the [items] for it, then what is saved on the site: [continueWatching] and
 * [myList], newest first, without the open page. An empty list is left out, and the whole group when both are empty.
 */
data class PageMenu(
    val title: String,
    val subtitle: UiText?,
    val items: List<PageMenuItem>,
    val continueWatching: List<SavedPage>,
    val myList: List<SavedPage>,
    val site: String,
) {
    /** The rows top to bottom; an entry without a cover shows [fallback] (the site's banner). */
    fun lines(today: LocalDate, zone: ZoneId, fallback: Any? = null): List<PageMenuLine> =
        items.map { PageMenuLine(PanelRow.Action(it.label), PagePick.Item(it)) } +
            saves(SavedLists(continueWatching, myList), DayClock(today, zone), fallback)

    /**
     * The group of the [site] with a hint, then each non-empty section: Continue Watching, then My List. An entry
     * without a cover shows [fallback]. Nothing when [lists] is empty, so an empty group is never shown.
     */
    private fun saves(lists: SavedLists, day: DayClock, fallback: Any?): List<PageMenuLine> {
        if (lists.empty) return emptyList()
        val hint = text(R.string.page_menu_saved_hint)
        val group = PageMenuLine(PanelRow.Group(text(R.string.panel_saved_on, site), hint), null)
        return listOf(group) + SavedSections.of(lists, day).flatMap { section ->
            val heading = PageMenuLine(PanelRow.Section(section.shelf.title, section.entries.size), null)
            listOf(heading) + section.entries.map { (entry, saved) ->
                PageMenuLine(PanelRow.Entry(entry.title, saved, entry.image ?: fallback), PagePick.Open(entry))
            }
        }
    }

    companion object {
        /**
         * The menu for the [open] page of [site]: ADD, or REMOVE when the page is in [myList]; then the switch to
         * the other control. The head shows the page's own title, else the site's name.
         */
        fun of(
            site: Site,
            page: OpenPage,
            pointer: Boolean,
            continueWatching: List<SavedPage>,
            myList: List<SavedPage>,
        ): PageMenu {
            val open = page.url?.let { SavedPageKey.of(site.id, it) }
            val saved = open != null && myList.any { it.key == open }
            fun own(list: List<SavedPage>) = list.filter { it.site == site.id && it.key != open }
            val named = page.title?.trim().orEmpty().takeIf { it.isNotEmpty() && it != site.name }
            return PageMenu(
                title = named ?: site.name,
                subtitle = named?.let { text(R.string.page_menu_on_site, site.name) },
                items = listOf(
                    if (saved) PageMenuItem.REMOVE else PageMenuItem.ADD,
                    if (pointer) PageMenuItem.POINTER_OFF else PageMenuItem.POINTER_ON,
                ),
                continueWatching = own(continueWatching),
                myList = own(myList),
                site = site.name,
            )
        }
    }
}

/** The page menu as a [SidePanel] over the page; [picture] is the site's banner, also for entries without one. */
@Composable
fun PageMenuPanel(menu: PageMenu, picture: Any?, modifier: Modifier = Modifier, onPick: (PagePick) -> Unit) {
    val lines = menu.lines(LocalDate.now(), ZoneId.systemDefault(), picture)
    SidePanel(
        head = PanelHead(text(R.string.page_menu_kicker), menu.title, listOfNotNull(menu.subtitle), picture),
        rows = lines.map { it.row },
        keys = PANEL_KEYS,
        onPick = { index -> lines[index].pick?.let(onPick) },
        modifier = modifier,
    )
}

/** A row of the page menu: its action, or a saved page of the site, opened here as if the user went there. */
internal fun BrowserScreen.pick(pick: PagePick) {
    when (pick) {
        is PagePick.Item -> vm.pick(pick.item, webView, scripts)
        is PagePick.Open -> {
            vm.dismissMenu()
            if (fullscreen.view != null) fullscreen.leave(notifyPage = true)
            guard.userNavigated()
            webView.loadUrl(pick.entry.url)
        }
    }
}

/** ADD reads the page's meta data first; every other item needs none. */
internal fun BrowserViewModel.pick(item: PageMenuItem, webView: WebView, scripts: Scripts) {
    if (item == PageMenuItem.ADD) {
        webView.evaluateJavascript(scripts[Script.PAGE_META]) { json -> pick(item, webView.url, json) }
    } else {
        pick(item, webView.url, null)
    }
}

/**
 * Connects the view model to the screen: the menu gets the key focus while open and gives it back to
 * [page] after; messages are shown once; events are carried out.
 */
internal fun ComponentActivity.bind(
    vm: BrowserViewModel,
    overlay: ComposeView,
    page: () -> View,
    say: (UiText) -> Unit,
    onEvent: (BrowserEvent) -> Unit,
) {
    lifecycleScope.launch { vm.events.collect(onEvent) }
    lifecycleScope.launch {
        vm.state.map { it.menu != null }.distinctUntilChanged().collect { open ->
            overlay.descendantFocusability =
                if (open) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
            (if (open) overlay else page()).requestFocus()
        }
    }
    lifecycleScope.launch {
        vm.state.map { it.message }.collect { text ->
            if (text != null) {
                say(text)
                vm.messageShown()
            }
        }
    }
}
