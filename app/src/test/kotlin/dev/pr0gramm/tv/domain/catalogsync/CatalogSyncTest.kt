package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.HttpFailure
import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.catalogsync.CatalogStorage
import dev.pr0gramm.tv.data.catalogsync.ServerAddress
import dev.pr0gramm.tv.data.catalogsync.ServerClient
import dev.pr0gramm.tv.data.manifest.ServerPath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogSyncTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val manifestUrl = URI("https://tv.example/manifest.json")
    private val nova = ServerPath.of("img/nova.png")!!
    private val kodi = ServerPath.of("img/kodi.png")!!

    /** Answers by URL: bytes for 200, an HTTP code, an IOException, or a gate the answer waits for. */
    private class FakeHttp(val responses: MutableMap<URI, Any>) : Http {
        val requests = mutableListOf<URI>()

        override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T {
            requests += url
            var response = responses[url] ?: 404
            if (response is CompletableDeferred<*>) response = response.await()!!
            return when (response) {
                is ByteArray -> read(response.inputStream())
                is Int -> throw HttpFailure(response)
                is IOException -> throw response
                else -> error("bad fake response $response")
            }
        }
    }

    private fun manifestText(version: Int, name: String = "Nova") = """{"version": $version, "date": "2026-11-18",
        "sites": [{"id": "nova", "name": "$name", "url": "https://nova.example/", "banner": "img/nova.png"}],
        "apps": [{"package": "org.kodi", "name": "Kodi", "image": "img/kodi.png", "versionCode": 1,
                  "apk": "apks/kodi.apk", "sha256": "${"cd".repeat(32)}", "size": 10}]}"""

    private fun url(resource: ServerPath) = manifestUrl.resolve(resource.path)

    private fun server(version: Int, images: List<ServerPath> = listOf(nova, kodi)): MutableMap<URI, Any> {
        val files = images.associate { url(it) to it.path.toByteArray() }
        return (files + (manifestUrl to manifestText(version).toByteArray())).toMutableMap<URI, Any>()
    }

    private var now = 0L
    private val root get() = tmp.root.resolve("catalog")

    /** A sync in this test's background scope; [CatalogSync.check] runs there. */
    private fun TestScope.sync(http: Http?, budget: Long = CatalogSync.IMAGE_BUDGET) = CatalogSync.create(
        CatalogStorage(root),
        http?.let { ServerClient(ServerAddress.parse(manifestUrl.toString())!!, it) { "en" } },
        backgroundScope,
        clock = { now },
        imageBudget = budget,
    )

    /** The ready catalog; fails while none is there. */
    private fun CatalogSync.shown() = (catalog.value as CatalogState.Ready).catalog

    private fun CachedCatalog.version() = manifest.version

    private fun SyncResult.switchedTo() = (this as SyncResult.Switched).catalog.version()

    @Test
    fun `a new version applies with its images and is read again on the next start`() = runTest {
        val sync = sync(FakeHttp(server(2)))
        assertEquals(2, sync.refresh(force = false).switchedTo())
        val catalog = sync.shown()
        assertEquals(2, catalog.version())
        assertEquals(setOf(nova, kodi), catalog.present)
        assertEquals("img/nova.png", File(catalog.dir, "img/nova.png").readText())
        assertEquals("file://${File(catalog.dir, "img/nova.png").absolutePath}", catalog.image(nova))

        val restarted = sync(FakeHttp(mutableMapOf())).shown()
        assertEquals(catalog, restarted)
    }

    @Test
    fun `the image budget keeps the images that fit and deletes the one that does not`() = runTest {
        // Each fake image is 12 bytes: a budget of 15 fits one of the two.
        val sync = sync(FakeHttp(server(2)), budget = 15)
        sync.refresh(force = false)
        val catalog = sync.shown()
        assertEquals(1, catalog.present.size)
        val dropped = (setOf(nova, kodi) - catalog.present).single()
        assertFalse(catalog.file(dropped).exists())
        assertEquals(12L, catalog.present.sumOf { catalog.file(it).length() })
    }

    @Test
    fun `a smaller version applies too`() = runTest {
        val http = FakeHttp(server(3))
        val sync = sync(http)
        sync.refresh(force = true)
        http.responses += server(2)
        assertEquals(2, sync.refresh(force = true).switchedTo())
        assertEquals(2, sync.shown().version())
    }

    @Test
    fun `the manifest applies before its images arrive`() = runTest {
        val gate = CompletableDeferred<Any>()
        val sync = sync(FakeHttp(server(2).also { it[url(nova)] = gate }))
        val running = async { sync.refresh(force = false) }
        while (sync.catalog.value == CatalogState.Loading) yield()
        assertEquals(2, sync.shown().version())
        assertNull(sync.shown().image(nova))
        gate.complete("img/nova.png".toByteArray())
        assertEquals(2, running.await().switchedTo())
        assertEquals(setOf(nova, kodi), sync.shown().present)
    }

    @Test
    fun `an invalid manifest is rejected and the last state stays`() = runTest {
        val http = FakeHttp(server(2))
        val sync = sync(http)
        sync.refresh(force = true)
        val before = sync.shown()
        http.responses[manifestUrl] = manifestText(3).replace("\"nova\"", "\"Nova!\"").toByteArray()
        val result = sync.refresh(force = true)
        assertTrue(result is SyncResult.Rejected && result.problems.isNotEmpty())
        assertEquals(before, sync.shown())
        assertEquals(before, sync(FakeHttp(mutableMapOf())).shown())
    }

    @Test
    fun `no network or a server error keep the last state`() = runTest {
        val http = FakeHttp(server(2))
        val sync = sync(http)
        sync.refresh(force = true)
        val before = sync.shown()
        http.responses[manifestUrl] = IOException("offline")
        assertEquals(SyncResult.Unreachable, sync.refresh(force = true))
        http.responses[manifestUrl] = 500
        assertEquals(SyncResult.Unreachable, sync.refresh(force = true))
        assertEquals(before, sync.shown())
    }

    @Test
    fun `without a server nothing is fetched and no catalog comes`() = runTest {
        val sync = sync(null)
        assertEquals(SyncResult.Unreachable, sync.refresh(force = true))
        assertEquals(CatalogState.Missing(Absence.NO_SERVER), sync.catalog.value)
    }

    @Test
    fun `the same version only fetches missing images`() = runTest {
        val http = FakeHttp(server(2, listOf(nova)))
        val sync = sync(http)
        sync.refresh(force = true)
        assertEquals(setOf(nova), sync.shown().present)
        assertNull(sync.shown().image(kodi))

        http.responses += server(2)
        http.requests.clear()
        assertTrue(sync.refresh(force = true) is SyncResult.Unchanged)
        assertEquals(listOf(manifestUrl, url(kodi)), http.requests)
        assertEquals(setOf(nova, kodi), sync.shown().present)
    }

    @Test
    fun `the same version keeps the stored manifest`() = runTest {
        val http = FakeHttp(server(2))
        val sync = sync(http)
        sync.refresh(force = true)
        http.responses[manifestUrl] = manifestText(2, name = "Anders").toByteArray()
        assertTrue(sync.refresh(force = true) is SyncResult.Unchanged)
        assertEquals("Nova", sync.shown().sites.single().name)
    }

    @Test
    fun `the folder of the old version is deleted after the switch`() = runTest {
        val http = FakeHttp(server(2))
        val sync = sync(http)
        sync.refresh(force = true)
        val old = sync.shown().dir
        assertTrue(old.isDirectory)
        http.responses += server(3)
        sync.refresh(force = true)
        assertFalse(old.exists())
        assertTrue(sync.shown().dir.isDirectory)
    }

    @Test
    fun `the server is asked at most hourly unless forced`() = runTest {
        val http = FakeHttp(server(2))
        val sync = sync(http)
        sync.refresh(force = false)
        http.requests.clear()
        now += HOUR - 1
        assertTrue(sync.refresh(force = false) is SyncResult.Unchanged)
        assertTrue(http.requests.isEmpty())
        sync.refresh(force = true)
        assertEquals(listOf(manifestUrl), http.requests)
        now += HOUR
        sync.refresh(force = false)
        assertEquals(listOf(manifestUrl, manifestUrl), http.requests)
    }

    @Test
    fun `an unreachable server is asked again on the next refresh`() = runTest {
        val http = FakeHttp(mutableMapOf(manifestUrl to IOException("offline")))
        val sync = sync(http)
        assertEquals(SyncResult.Unreachable, sync.refresh(force = false))
        http.responses += server(2)
        assertEquals(2, sync.refresh(force = false).switchedTo())
    }

    @Test
    fun `check runs one refresh at a time and the next one after it ended`() = runTest {
        val gate = CompletableDeferred<Any>()
        val http = FakeHttp(server(2).also { it[manifestUrl] = gate })
        val sync = sync(http)
        sync.check()
        runCurrent()
        sync.check(force = true)
        runCurrent()
        assertEquals(listOf(manifestUrl), http.requests)

        gate.complete(manifestText(2).toByteArray())
        sync.catalog.first { it.catalog?.present == setOf(nova, kodi) }
        sync.check(force = true)
        runCurrent()
        assertEquals(2, http.requests.count { it == manifestUrl })
    }

    @Test
    fun `cold start without a stored manifest loads, without a server it is missing`() = runTest {
        assertEquals(CatalogState.Loading, sync(FakeHttp(server(2))).catalog.value)
        val serverless = sync(null)
        assertEquals(CatalogState.Missing(Absence.NO_SERVER), serverless.catalog.value)
        assertEquals(SyncResult.Unreachable, serverless.refresh(force = true))
        assertEquals(CatalogState.Missing(Absence.NO_SERVER), serverless.catalog.value)
    }

    @Test
    fun `a failed first refresh names the reason and the next one asks again`() = runTest {
        val http = FakeHttp(mutableMapOf(manifestUrl to IOException("offline")))
        val sync = sync(http)
        assertEquals(SyncResult.Unreachable, sync.refresh(force = false))
        assertEquals(CatalogState.Missing(Absence.UNREACHABLE), sync.catalog.value)
        http.responses[manifestUrl] = "{".toByteArray()
        assertTrue(sync.refresh(force = false) is SyncResult.Rejected)
        assertEquals(CatalogState.Missing(Absence.REJECTED), sync.catalog.value)
        http.responses += server(2)
        assertEquals(2, sync.refresh(force = false).switchedTo())
        assertEquals(2, sync.shown().version())
    }

    @Test
    fun `a failed refresh keeps the cached catalog ready`() = runTest {
        val http = FakeHttp(server(2))
        sync(http).refresh(force = true)
        val restarted = sync(http)
        val cached = restarted.catalog.value
        assertEquals(2, restarted.shown().version())
        http.responses[manifestUrl] = IOException("offline")
        assertEquals(SyncResult.Unreachable, restarted.refresh(force = true))
        assertEquals(cached, restarted.catalog.value)
        http.responses[manifestUrl] = "{".toByteArray()
        assertTrue(restarted.refresh(force = true) is SyncResult.Rejected)
        assertEquals(cached, restarted.catalog.value)
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
