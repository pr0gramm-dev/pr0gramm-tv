package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.data.appsettings.PointerLook
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.domain.savedpages.Visit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val site = testSite
    private val page = TEST_PAGE
    private val meta = TEST_META

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.env(): BrowserEnv = browserEnv(tmp.root)

    @Test
    fun menuOffersAddForAnUnsavedPage() = runTest {
        val e = env()
        e.vm.openMenu(OpenPage(page, "Ein Artikel"))
        advanceUntilIdle()
        assertEquals(listOf(PageMenuItem.ADD, PageMenuItem.POINTER_ON), e.vm.state.value.menu?.items)
        assertEquals("Ein Artikel", e.vm.state.value.menu?.title)
    }

    @Test
    fun menuListsWhatIsSavedOnTheSite() = runTest {
        val e = env()
        e.vm.pick(PageMenuItem.ADD, page, meta)
        play(e, "https://a.example/v/1")
        e.stored(e.vm.watch.ended(null))
        e.vm.openMenu(OpenPage("https://a.example/other", null))
        advanceUntilIdle()
        assertEquals(listOf("https://a.example/v/1"), e.vm.state.value.menu?.continueWatching?.map { it.url })
        assertEquals(listOf(page), e.vm.state.value.menu?.myList?.map { it.url })
    }

    @Test
    fun savingShowsTheTextAndTheMenuNowOffersRemove() = runTest {
        val e = env()
        e.vm.openMenu(OpenPage(page, null))
        advanceUntilIdle()
        e.vm.pick(PageMenuItem.ADD, page, meta)
        assertNull(e.vm.state.value.menu)
        val said = saidNext(e.vm)
        assertEquals("„Ein Artikel“ ist jetzt in Meine Liste", Strings.de(said))
        assertEquals("\"Ein Artikel\" is now in My List", Strings.en(said))
        e.vm.openMenu(OpenPage(page, null))
        advanceUntilIdle()
        assertEquals(PageMenuItem.REMOVE, e.vm.state.value.menu?.items?.first())
    }

    @Test
    fun removingShowsTheText() = runTest {
        val e = env()
        e.vm.pick(PageMenuItem.ADD, page, meta)
        saidNext(e.vm)
        e.vm.messageShown()
        e.vm.pick(PageMenuItem.REMOVE, page, null)
        val said = saidNext(e.vm)
        assertEquals("„Ein Artikel“ aus Meine Liste entfernt", Strings.de(said))
        assertEquals("Removed \"Ein Artikel\" from My List", Strings.en(said))
    }

    @Test
    fun theStartPageIsRefusedWithItsText() = runTest {
        val e = env()
        e.vm.pick(PageMenuItem.ADD, site.url, meta)
        val said = saidNext(e.vm)
        assertEquals("Das ist die Start-URL von Alpha", Strings.de(said))
        assertEquals("This page is the start URL of Alpha", Strings.en(said))
    }

    @Test
    fun messageIsClearedWhenShown() = runTest {
        val e = env()
        e.vm.pick(PageMenuItem.ADD, site.url, meta)
        saidNext(e.vm)
        e.vm.messageShown()
        assertNull(e.vm.state.value.message)
    }

    @Test
    fun pointerChoiceIsStoredForTheSite() = runTest {
        val e = env()
        advanceUntilIdle()
        assertEquals(false, e.vm.state.value.pointer)
        e.vm.pick(PageMenuItem.POINTER_ON, page, null)
        assertEquals(BrowserEvent.Pointer(true), e.vm.events.first())
        assertEquals(true, e.vm.state.value.pointer)
        advanceUntilIdle()
        assertEquals(Control.POINTER, e.settings.of(site).first().control)
        e.vm.openMenu(OpenPage(page, null))
        advanceUntilIdle()
        assertEquals(PageMenuItem.POINTER_OFF, e.vm.state.value.menu?.items?.last())
        e.vm.pick(PageMenuItem.POINTER_OFF, page, null)
        advanceUntilIdle()
        assertEquals(Control.FOCUS, e.settings.of(site).first().control)
    }

    @Test
    fun togglingThePointerSwitchesAndStoresIt() = runTest {
        val e = env()
        advanceUntilIdle()
        e.vm.togglePointer()
        assertEquals(BrowserEvent.Pointer(true), e.vm.events.first())
        advanceUntilIdle()
        assertEquals(Control.POINTER, e.settings.of(site).first().control)
        e.vm.togglePointer()
        assertEquals(BrowserEvent.Pointer(false), e.vm.events.first())
        assertEquals(false, e.vm.state.value.pointer)
        advanceUntilIdle()
        assertEquals(Control.FOCUS, e.settings.of(site).first().control)
    }

    @Test
    fun pointerLooksLikeTheAppSettingSays() = runTest {
        val e = env()
        val looks = MutableStateFlow(PointerLook.HAND)
        val shelves = BrowserShelves(e.myList, e.watching)
        val vm = BrowserViewModel(site, e.settings, looks, shelves, Visit(site.url, false), e.sender)
        advanceUntilIdle()
        assertEquals(PointerLook.HAND, vm.state.value.look)
        looks.value = PointerLook.GLOW
        advanceUntilIdle()
        assertEquals(PointerLook.GLOW, vm.state.value.look)
    }

    @Test
    fun pointerStartsFromTheSiteOptions() = runTest {
        val e = env()
        e.settings.update(site) { it.copy(control = Control.POINTER) }
        val visit = Visit(site.url, false)
        val shelves = BrowserShelves(e.myList, e.watching)
        val again = BrowserViewModel(site, e.settings, flowOf(PointerLook.HAND), shelves, visit, e.sender)
        advanceUntilIdle()
        assertEquals(true, again.state.value.pointer)
    }

    @Test
    fun progressIsKept() = runTest {
        val e = env()
        e.vm.progress(40)
        assertEquals(40, e.vm.state.value.progress)
    }

    @Test
    fun rendererGoneCloses() = runTest {
        val e = env()
        e.vm.rendererGone()
        assertEquals(BrowserEvent.Close, e.vm.events.first())
    }

    /** Waits for the next message; saving and removing finish on a real IO thread. */
    private suspend fun saidNext(vm: BrowserViewModel): UiText =
        requireNotNull(vm.state.first { it.message != null }.message)
}
