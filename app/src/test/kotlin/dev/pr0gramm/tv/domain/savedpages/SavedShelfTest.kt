package dev.pr0gramm.tv.domain.savedpages

import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.catalogsync.catalogOf
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import dev.pr0gramm.tv.data.savedpages.CoverSource
import dev.pr0gramm.tv.data.savedpages.PageMeta
import dev.pr0gramm.tv.data.savedpages.SavedListSerializer
import dev.pr0gramm.tv.data.savedpages.SavedPage
import dev.pr0gramm.tv.data.savedpages.SavedPageStore
import dev.pr0gramm.tv.data.savedpages.Shelf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SavedShelfTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)
    private val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1)
    private val webp = "RIFF....WEBPVP8 ".toByteArray()

    private class FakeCovers(var bytes: ByteArray?) : CoverSource {
        var calls = 0

        override suspend fun fetch(imageUrl: String, pageUrl: String): ByteArray? {
            calls++
            return bytes
        }
    }

    private fun site(id: String) = Site(
        id = SiteId(id),
        name = id,
        url = "https://$id.example/",
        description = null,
        category = null,
        tags = emptyList(),
        color = null,
        allowInsecureContent = false,
        allow = emptySet(),
        defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false),
        banner = null,
    )

    private val a = site("a")
    private val b = site("b")
    private val catalog = catalogOf(a, b)
    private val file get() = File(tmp.root, "list.json")
    private val images get() = File(tmp.root, "images")

    private fun TestScope.list(
        covers: CoverSource = FakeCovers(jpeg),
        catalog: CachedCatalog = this@SavedShelfTest.catalog,
        clock: () -> Long = { 1L },
    ) = listOn(file, covers, { catalog.ids }, clock)

    private fun TestScope.watching(file: File = File(tmp.root, "watching.json")) =
        listOn(file, FakeCovers(jpeg), { catalog.ids }, shelf = Shelf.CONTINUE_WATCHING)

    private fun TestScope.listOn(
        file: File,
        covers: CoverSource,
        ids: () -> Set<SiteId>,
        clock: () -> Long = { 1L },
        shelf: Shelf = Shelf.MY_LIST,
    ) = SavedShelf(
        SavedPageStore.create(file, images, shelf, covers, CoroutineScope(StandardTestDispatcher(testScheduler))),
        ids,
        clock,
    )

    private fun meta(image: String? = "https://a.example/c.jpg") =
        PageMeta.parse(JSONObject(mapOf("title" to "T", "image" to image)).toString())

    private fun page(n: Int) = Page("https://a.example/v/$n", Page.Kind.NORMAL)

    private fun imageFiles() = images.listFiles().orEmpty().map { it.name }

    @Test
    fun watchingKeepsTheLastFivePerSite() = runTest {
        val list = watching()
        (1..6).forEach { list.save(a, page(it), meta()) }
        list.save(b, Page("https://b.example/v/1", Page.Kind.NORMAL), meta())
        val urls = list.entries.first().map { it.url }
        assertEquals(listOf("https://b.example/v/1") + (6 downTo 2).map { "https://a.example/v/$it" }, urls)
    }

    @Test
    fun adoptCopiesAnEntryWithItsImageIntoMyList() = runTest {
        val from = watching()
        val saved = (from.save(a, page(1), meta()) as SaveResult.Saved).entry
        val mine = list()
        val adopted = mine.adopt(saved) as SaveResult.Saved
        assertEquals(saved.key, adopted.entry.key)
        assertTrue(adopted.entry.image!!.readBytes().contentEquals(jpeg))
        assertTrue(mine.current().any { it.key == saved.key })
    }

    @Test
    fun adoptRefusesANewEntryOnAFullList() = runTest {
        val mine = list()
        (1..SavedList.MAX).forEach { mine.save(a, page(it), meta(image = null)) }
        val extra = SavedPage(a.id, "https://a.example/v/99", "X", null, 1, null)
        assertEquals(SaveResult.Refused(Refusal.FULL), mine.adopt(extra))
    }

    @Test
    fun savesWithCoverImage() = runTest {
        val list = list()
        val saved = list.save(a, page(1), meta()) as SaveResult.Saved
        val image = saved.entry.image
        assertNotNull(image)
        assertTrue(image!!.readBytes().contentEquals(jpeg))
        assertEquals(listOf(saved.entry), list.entries.first())
        assertTrue(list.current().any { it.key == saved.entry.key })
    }

    @Test
    fun acceptsPngAndWebp() = runTest {
        val covers = FakeCovers(png)
        val list = list(covers)
        assertNotNull((list.save(a, page(1), meta()) as SaveResult.Saved).entry.image)
        covers.bytes = webp
        assertNotNull((list.save(a, page(2), meta()) as SaveResult.Saved).entry.image)
    }

    @Test
    fun coverOver5MbIsLeftOut() = runTest {
        val big = ByteArray(5 * 1024 * 1024 + 1).also { jpeg.copyInto(it) }
        val saved = list(FakeCovers(big)).save(a, page(1), meta()) as SaveResult.Saved
        assertNull(saved.entry.image)
        assertTrue(imageFiles().isEmpty())
    }

    @Test
    fun coverOfWrongFormatIsLeftOut() = runTest {
        val saved = list(FakeCovers("GIF89a".toByteArray())).save(a, page(1), meta()) as SaveResult.Saved
        assertNull(saved.entry.image)
    }

    @Test
    fun failedCoverIsLeftOut() = runTest {
        val saved = list(FakeCovers(null)).save(a, page(1), meta()) as SaveResult.Saved
        assertNull(saved.entry.image)
    }

    @Test
    fun withoutImageUrlNothingIsFetched() = runTest {
        val covers = FakeCovers(jpeg)
        list(covers).save(a, page(1), meta(image = null))
        assertEquals(0, covers.calls)
    }

    @Test
    fun refusedPageIsNotFetchedOrStored() = runTest {
        val covers = FakeCovers(jpeg)
        val list = list(covers)
        val result = list.save(a, Page("https://a.example/", Page.Kind.START), meta())
        assertEquals(SaveResult.Refused(Refusal.START_PAGE), result)
        assertEquals(0, covers.calls)
        assertTrue(list.entries.first().isEmpty())
    }

    @Test
    fun fullListRefusesNewPageButMovesKnownPageToFront() = runTest {
        var now = 0L
        val list = list(clock = { ++now })
        (1..SavedList.MAX).forEach { list.save(a, page(it), meta(null)) }
        assertEquals(SaveResult.Refused(Refusal.FULL), list.save(a, page(99), meta(null)))
        list.save(a, page(1), meta(null))
        val urls = list.entries.first().map { it.url }
        assertEquals(SavedList.MAX, urls.size)
        assertEquals(page(1).url, urls.first())
    }

    @Test
    fun resaveReplacesImageWithoutLeavingOrphans() = runTest {
        val covers = FakeCovers(jpeg)
        val list = list(covers)
        list.save(a, page(1), meta())
        covers.bytes = null
        val again = list.save(a, page(1), meta()) as SaveResult.Saved
        assertNull(again.entry.image)
        assertEquals(1, list.entries.first().size)
        assertTrue(imageFiles().isEmpty())
    }

    @Test
    fun removeDeletesEntryAndImage() = runTest {
        val list = list()
        val saved = list.save(a, page(1), meta()) as SaveResult.Saved
        assertEquals(SaveResult.Removed(saved.entry), list.remove(saved.entry.key))
        assertTrue(list.entries.first().isEmpty())
        assertTrue(imageFiles().isEmpty())
        assertEquals(SaveResult.Removed(null), list.remove(saved.entry.key))
    }

    @Test
    fun entriesOfRemovedSitesAndTheirImagesStayAndStrayImagesGo() = runTest {
        val before = list()
        val keep = before.save(a, page(1), meta()) as SaveResult.Saved
        val gone = before.save(b, page(2), meta()) as SaveResult.Saved
        File(images, "stray.tmp").writeText("x")
        val gonePath = gone.entry.image!!
        assertEquals(2, SavedListSerializer(images).readFrom(file.inputStream()).size)

        val reduced = listOn(
            File(tmp.root, "other.json").also { file.copyTo(it) },
            FakeCovers(null),
            { setOf(a.id) },
        )
        assertEquals(listOf(gone.entry.url, keep.entry.url).sorted(), reduced.entries.first().map { it.url }.sorted())
        assertEquals(listOf(keep.entry.image!!.name, gonePath.name).sorted(), imageFiles().sorted())
        assertTrue(gonePath.exists())
        assertTrue(!File(images, "stray.tmp").exists())
    }

    @Test
    fun hiddenEntriesDoNotCountForTheLimit() = runTest {
        list().save(b, page(1), meta(null))
        val reopened = File(tmp.root, "other.json").also { file.copyTo(it) }
        val onlyA = listOn(reopened, FakeCovers(null), { setOf(a.id) })
        (1..SavedList.MAX).forEach { onlyA.save(a, page(it + 10), meta(null)) }
        assertEquals(SaveResult.Refused(Refusal.FULL), onlyA.save(a, page(99), meta(null)))
        val urls = SavedListSerializer(images).readFrom(reopened.inputStream()).map { it.url }
        assertEquals(SavedList.MAX, urls.size)
        assertTrue(page(1).url !in urls)
    }

    @Test
    fun theCatalogSitesAreReadAtEachSave() = runTest {
        var ids = setOf(a.id, b.id)
        val list = listOn(file, FakeCovers(null), { ids })
        val onB = { n: Int -> Page("https://b.example/v/$n", Page.Kind.NORMAL) }
        (1..SavedList.MAX).forEach { list.save(b, onB(it), meta(null)) }
        assertEquals(SaveResult.Refused(Refusal.FULL), list.save(b, onB(99), meta(null)))
        ids = setOf(a.id)
        assertTrue(list.save(a, page(99), meta(null)) is SaveResult.Saved)
    }

    @Test
    fun twoSavesAtOnceOn49EntriesLeaveOneRefusedAsFull() = runTest {
        val gate = CompletableDeferred<Unit>()
        val waiting = Channel<Unit>(Channel.UNLIMITED)
        val covers = object : CoverSource {
            override suspend fun fetch(imageUrl: String, pageUrl: String): ByteArray? {
                waiting.send(Unit)
                gate.await()
                return jpeg
            }
        }
        val list = list(covers)
        (1 until SavedList.MAX).forEach { list.save(a, page(it), meta(image = null)) }
        val first = async { list.save(a, page(100), meta()) }
        val second = async { list.save(a, page(101), meta()) }
        repeat(2) { waiting.receive() } // both passed the first check and load their cover
        gate.complete(Unit)
        val results = listOf(first.await(), second.await())
        assertEquals(1, results.count { it is SaveResult.Saved })
        assertEquals(1, results.count { it == SaveResult.Refused(Refusal.FULL) })
        assertEquals(SavedList.MAX, list.current().size)
    }
}
