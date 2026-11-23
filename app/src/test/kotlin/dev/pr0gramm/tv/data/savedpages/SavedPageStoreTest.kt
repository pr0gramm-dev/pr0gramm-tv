package dev.pr0gramm.tv.data.savedpages

import dev.pr0gramm.tv.data.manifest.SiteId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SavedPageStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file get() = File(tmp.root, "list.json")
    private val images get() = File(tmp.root, "images")

    private object NoCovers : CoverSource {
        override suspend fun fetch(imageUrl: String, pageUrl: String): ByteArray? = null
    }

    private fun TestScope.list() = SavedPageStore.create(
        file,
        images,
        Shelf.MY_LIST,
        NoCovers,
        CoroutineScope(StandardTestDispatcher(testScheduler)),
    )

    private val jpeg = byteArrayOf(-1, -40, -1, 0)

    private fun page(path: String) = SavedPage(SiteId("a"), "https://a.example/$path", path, null, 1, null)

    @Test
    fun aServersShelfAndCoversLiveInItsFolder() = runTest {
        val dir = File(tmp.root, "servers/0123456789abcdef")
        val store = SavedPageStore.forServer(
            dir,
            Shelf.CONTINUE_WATCHING,
            NoCovers,
            CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
        val image = store.update { pages, covers ->
            val file = covers.write(SavedPageKey.of(SiteId("a"), "https://a.example/x"), Cover(jpeg))
            Edit(pages + page("x").copy(image = file), file)
        }
        assertTrue(File(dir, "datastore/continue-watching.json").isFile)
        assertEquals(File(dir, "continue-watching"), image?.parentFile)
    }

    @Test
    fun brokenEntriesAreSkipped() = runTest {
        file.writeText(
            """[{"site":"a","url":"https://a.example/ok","title":"Ok","savedAt":5},
              {"site":"a","title":"no url","savedAt":1}, 7, {"site":"a","url":"u"}]""",
        )
        val entries = list().entries.first()
        assertEquals(listOf("https://a.example/ok"), entries.map { it.url })
    }

    @Test
    fun corruptFileMeansEmptyList() = runTest {
        file.writeText("not json {")
        assertTrue(list().entries.first().isEmpty())
    }

    @Test
    fun anUpdateSeesTheListOfTheUpdateBeforeIt() = runTest {
        val store = list()
        val inside = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val slow = async {
            store.update { pages, _ ->
                inside.complete(Unit)
                gate.await()
                Edit(pages + page("first"), "first")
            }
        }
        inside.await()
        val next = async { store.update { pages, _ -> Edit(pages + page("second"), pages.map { it.title }) } }
        runCurrent()
        assertFalse(next.isCompleted)
        gate.complete(Unit)
        assertEquals("first", slow.await())
        assertEquals(listOf("first"), next.await())
        assertEquals(listOf("first", "second"), store.current().map { it.title })
    }
}
