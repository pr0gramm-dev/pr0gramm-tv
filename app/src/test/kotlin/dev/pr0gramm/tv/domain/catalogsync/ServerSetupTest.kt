package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.HttpFailure
import dev.pr0gramm.tv.data.catalogsync.SavedAddress
import dev.pr0gramm.tv.data.catalogsync.ServerAddress
import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.ManifestResult
import kotlinx.coroutines.test.runTest
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

class ServerSetupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Answers by URL: text for 200, an HTTP code, or an IOException. Records each URL it got. */
    private class FakeHttp(private val responses: Map<URI, Any>) : Http {
        val requests = mutableListOf<URI>()
        val sentHeaders = mutableListOf<Map<String, String>>()

        override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T {
            requests += url
            sentHeaders += headers
            return when (val response = responses[url] ?: 404) {
                is String -> read(response.byteInputStream())
                is Int -> throw HttpFailure(response)
                is IOException -> throw response
                else -> error("bad fake response $response")
            }
        }
    }

    private val valid = """{"version": 1, "date": "2026-11-18",
        "sites": [{"id": "nova", "name": "Nova", "url": "https://nova.example/", "banner": "img/nova.png"}],
        "apps": []}"""
    private val invalid = """{"version": 0, "sites": []}"""

    private val one = URI("https://one.example.org/manifest.json")
    private val two = URI("https://two.example.org/tv/manifest.json")

    private val file get() = File(tmp.root, "server-address")

    private fun saved() = SavedAddress(file)

    private fun address(text: String) = ServerAddress.parse(text)!!

    private fun setup(http: Http) = ServerSetup.create(saved(), http) { "en" }

    @Test fun `without a saved address there is no current one`() {
        assertNull(setup(FakeHttp(emptyMap())).current)
    }

    @Test fun `the saved address is the current one`() {
        saved().save(address("one.example.org"))
        assertEquals(address("one.example.org"), setup(FakeHttp(emptyMap())).current)
    }

    @Test fun `every request of the connect check names the language of the app texts`() = runTest {
        val http = FakeHttp(mapOf(two to valid))
        ServerSetup.create(saved(), http) { "de" }.connect("two.example.org/tv")
        assertTrue(http.sentHeaders.isNotEmpty())
        assertTrue(http.sentHeaders.toString(), http.sentHeaders.all { it["Accept-Language"] == "de" })
    }

    @Test fun `a valid manifest saves a new address as changed`() = runTest {
        val http = FakeHttp(mapOf(two to valid))
        assertEquals(Connect.Saved(changed = true), setup(http).connect("  two.example.org/tv "))
        assertEquals(listOf(two), http.requests)
        assertEquals(address("two.example.org/tv"), saved().load())
    }

    @Test fun `a valid manifest at the current address saves as unchanged`() = runTest {
        saved().save(address("one.example.org"))
        val setup = setup(FakeHttp(mapOf(one to valid)))
        assertEquals(Connect.Saved(changed = false), setup.connect("https://one.example.org/manifest.json"))
        assertEquals(address("one.example.org"), saved().load())
    }

    @Test fun `an unreachable server is refused and nothing is saved`() = runTest {
        assertEquals(Connect.Unreachable, setup(FakeHttp(mapOf(two to IOException("offline")))).connect(two.toString()))
        assertEquals(Connect.Unreachable, setup(FakeHttp(mapOf(two to 500))).connect(two.toString()))
        assertEquals(Connect.Unreachable, setup(FakeHttp(emptyMap())).connect(two.toString()))
        assertFalse(file.exists())
    }

    @Test fun `an invalid manifest is refused with the parser's first problem`() = runTest {
        val problems = (Manifest.parse(invalid) as ManifestResult.Invalid).problems
        assertTrue(problems.size > 1)
        val outcome = setup(FakeHttp(mapOf(two to invalid))).connect("two.example.org/tv")
        assertEquals(Connect.Rejected(problems.first()), outcome)
        assertFalse(file.exists())
    }

    @Test fun `a refused address keeps the saved one`() = runTest {
        saved().save(address("one.example.org"))
        val setup = setup(FakeHttp(mapOf(two to invalid)))
        assertTrue(setup.connect("two.example.org/tv") is Connect.Refused)
        assertEquals(address("one.example.org"), saved().load())
        assertEquals(address("one.example.org"), setup.current)
    }

    @Test fun `a bad address is refused without a request`() = runTest {
        val http = FakeHttp(emptyMap())
        val setup = setup(http)
        assertEquals(Connect.BadAddress, setup.connect(""))
        assertEquals(Connect.BadAddress, setup.connect("ftp://one.example.org"))
        assertEquals(Connect.BadAddress, setup.connect("https://user@one.example.org"))
        assertTrue(http.requests.isEmpty())
        assertFalse(file.exists())
    }

    @Test fun `an http address is accepted and saved`() = runTest {
        val local = URI("http://10.0.0.2:8080/manifest.json")
        val outcome = setup(FakeHttp(mapOf(local to valid))).connect("http://10.0.0.2:8080")
        assertEquals(Connect.Saved(changed = true), outcome)
        assertEquals(address("http://10.0.0.2:8080"), saved().load())
    }
}
