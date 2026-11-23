package dev.pr0gramm.tv.ui.browser

import androidx.lifecycle.viewModelScope
import dev.pr0gramm.tv.domain.savedpages.CONTINUE_WATCHING_AFTER_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** "Weiterschauen" records of a video fullscreen ([BrowserViewModel.WatchRecord]). */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchRecordTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun fullscreenRecordsTheAddressItStartedOnWhenItEnds() = runTest {
        val e = browserEnv(tmp.root)
        play(e, TEST_PAGE)
        e.stored(e.vm.watch.ended(TEST_META))
        e.watching.entries.first().single().let {
            assertEquals(TEST_PAGE, it.url)
            assertEquals("Ein Artikel", it.title)
        }
        assertEquals(emptyList<Any>(), e.myList.entries.first())
        assertNull(e.vm.state.value.message)
    }

    @Test
    fun aSecondVideoOfTheSiteComesFirstAndKeepsTheOne() = runTest {
        val e = browserEnv(tmp.root)
        play(e, TEST_PAGE)
        e.stored(e.vm.watch.ended(TEST_META))
        play(e, "$TEST_PAGE-2")
        e.stored(e.vm.watch.ended(TEST_META))
        assertEquals(listOf("$TEST_PAGE-2", TEST_PAGE), e.watching.entries.first().map { it.url })
    }

    @Test
    fun fullscreenIsRecordedWhileItPlaysSoClosingInFullscreenKeepsIt() = runTest {
        val e = browserEnv(tmp.root)
        play(e, TEST_PAGE)
        advanceUntilIdle()
        // No watch.ended: Back held or the Home key closed the screen while the video was fullscreen.
        e.watching.entries.first().single().let {
            assertEquals(TEST_PAGE, it.url)
            assertEquals("Ein Artikel", it.title)
        }
    }

    @Test
    fun aRecordSentBeforeTheScreenClosesIsStillStored() = runTest {
        val e = browserEnv(tmp.root)
        play(e, TEST_PAGE)
        // The Home key closed the screen in fullscreen: the view model is cleared before the record is stored.
        e.vm.viewModelScope.cancel()
        advanceUntilIdle()
        e.watching.entries.first().single().let {
            assertEquals(TEST_PAGE, it.url)
            assertEquals("Ein Artikel", it.title)
        }
    }

    @Test
    fun aFullscreenShorterThanTheWaitIsNotRecorded() = runTest {
        val e = browserEnv(tmp.root)
        play(e, TEST_PAGE, CONTINUE_WATCHING_AFTER_MS - 1)
        assertNull(e.vm.watch.ended(TEST_META))
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), e.watching.entries.first())
    }

    @Test
    fun aShortFullscreenDoesNotCountTowardsTheNextOne() = runTest {
        val e = browserEnv(tmp.root)
        play(e, TEST_PAGE, CONTINUE_WATCHING_AFTER_MS - 1)
        e.vm.watch.ended(TEST_META)
        play(e, "$TEST_PAGE-2", 1)
        e.vm.watch.ended(TEST_META)
        advanceUntilIdle()
        assertEquals(emptyList<Any>(), e.watching.entries.first())
    }

    @Test
    fun recordingWhilePlayingAndAtTheEndKeepsOneEntry() = runTest {
        val e = browserEnv(tmp.root)
        play(e, TEST_PAGE)
        e.stored(e.vm.watch.ended(TEST_META))
        assertEquals(listOf(TEST_PAGE), e.watching.entries.first().map { it.url })
    }

    @Test
    fun endingWithoutAStartRecordsNothing() = runTest {
        val e = browserEnv(tmp.root)
        assertNull(e.vm.watch.ended(TEST_META))
        assertEquals(emptyList<Any>(), e.watching.entries.first())
    }
}
