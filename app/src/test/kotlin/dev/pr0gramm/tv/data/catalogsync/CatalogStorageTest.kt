package dev.pr0gramm.tv.data.catalogsync

import dev.pr0gramm.tv.core.http.HttpFailure
import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.ManifestResult
import dev.pr0gramm.tv.data.manifest.Resource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

/** Each server keeps its catalog under `servers/<key>/catalog/`; a switch leaves the other one in place (AC5). */
class CatalogStorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val text = """{"version": 3, "date": "2026-11-18", "sites": [], "apps": []}"""
    private val manifest = (Manifest.parse(text) as ManifestResult.Valid).manifest

    private fun dirOf(host: String) = serverDir(tmp.root, ServerAddress.parse(host))

    private val image = Resource.of("https://cdn.x/a.png")!!
    private val imageUrl = URI("https://cdn.x/a.png")

    /** Fetches [image] into an empty catalog with [http] answering; the result and the catalog's folder. */
    private suspend fun fetch(http: FakeHttp): Pair<Boolean, File> {
        val storage = CatalogStorage.forServer(dirOf("one.example"))
        val catalog = storage.store(manifest, text)
        val server = ServerClient(ServerAddress.parse("one.example")!!, http) { "en" }
        return storage.fetchImage(server, catalog, image) to catalog.dir
    }

    @Test fun `each server keeps its own stored catalog`() = runTest {
        val one = dirOf("one.example")
        val two = dirOf("two.example")
        CatalogStorage.forServer(one).store(manifest, text)
        assertTrue(File(one, "catalog/manifest.json").isFile)
        assertTrue(File(one, "catalog/3").isDirectory)
        assertNull(CatalogStorage.forServer(two).stored())
        assertEquals(3, CatalogStorage.forServer(one).stored()?.manifest?.version)
    }

    @Test fun `an image of 0 bytes is missing, because a power loss can cut it short`() = runTest {
        val withUrl = """{"version": 3, "date": "2026-11-18", "sites": [{"id": "nova", "name": "Nova",
            "url": "https://nova.example/", "banner": "https://cdn.x/a.png"}], "apps": []}"""
        val storage = CatalogStorage.forServer(dirOf("one.example"))
        val empty = storage.store((Manifest.parse(withUrl) as ManifestResult.Valid).manifest, withUrl)
        val banner = Resource.of("https://cdn.x/a.png")!!
        empty.file(banner).writeBytes(ByteArray(0))
        assertEquals(emptySet<Resource>(), storage.stored()?.present)
    }

    @Test fun `stored finds an image of an absolute url in its url- file`() = runTest {
        val withUrl = """{"version": 3, "date": "2026-11-18", "sites": [{"id": "nova", "name": "Nova",
            "url": "https://nova.example/", "banner": "https://cdn.x/a.png"}], "apps": []}"""
        val storage = CatalogStorage.forServer(dirOf("one.example"))
        val empty = storage.store((Manifest.parse(withUrl) as ManifestResult.Valid).manifest, withUrl)
        val banner = Resource.of("https://cdn.x/a.png")!!
        assertEquals(emptySet<Resource>(), storage.stored()?.present)
        empty.file(banner).writeBytes(byteArrayOf(1))
        assertTrue(empty.file(banner).name.startsWith("url-"))
        assertEquals(setOf(banner), storage.stored()?.present)
    }

    @Test fun `an endless image is not stored and leaves no tmp file`() = runTest {
        val body = EndlessBody()
        val (fetched, dir) = fetch(FakeHttp(mapOf(imageUrl to body)))
        assertFalse(fetched)
        assertTrue("took ${body.taken} bytes", body.taken <= ServerClient.MAX_IMAGE_BYTES + 1L)
        assertEquals(emptyList<String>(), dir.list()?.toList())
    }

    @Test fun `an image of exactly the limit is stored`() = runTest {
        val bytes = ByteArray(ServerClient.MAX_IMAGE_BYTES) { 7 }
        val (fetched, dir) = fetch(FakeHttp(mapOf(imageUrl to bytes)))
        assertTrue(fetched)
        assertEquals(ServerClient.MAX_IMAGE_BYTES.toLong(), dir.listFiles()?.single()?.length())
    }

    @Test fun `a fetched image of an absolute url is present again after a restart`() = runTest {
        val withUrl = """{"version": 3, "date": "2026-11-18", "sites": [{"id": "nova", "name": "Nova",
            "url": "https://nova.example/", "banner": "https://cdn.x/a.png"}], "apps": []}"""
        val storage = CatalogStorage.forServer(dirOf("one.example"))
        val catalog = storage.store((Manifest.parse(withUrl) as ManifestResult.Valid).manifest, withUrl)
        val http = FakeHttp(mapOf(imageUrl to byteArrayOf(1, 2)))
        val server = ServerClient(ServerAddress.parse("one.example")!!, http) { "en" }
        assertTrue(storage.fetchImage(server, catalog, image))
        val restarted = CatalogStorage.forServer(dirOf("one.example")).stored()
        assertEquals(setOf(image), restarted?.present)
        assertEquals(2L, restarted?.let { File(it.image(image)!!.removePrefix("file://")).length() })
    }

    @Test fun `a refused image redirect leaves nothing`() = runTest {
        val (fetched, dir) = fetch(FakeHttp(mapOf(imageUrl to HttpFailure(302, "ftp://cdn.x/a.png"))))
        assertFalse(fetched)
        assertEquals(emptyList<String>(), dir.list()?.toList())
    }
}
