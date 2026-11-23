package dev.pr0gramm.tv.data.catalogsync

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.HttpFailure
import dev.pr0gramm.tv.core.http.RedirectRefused
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.core.updatefailure.UpdateFailure
import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import dev.pr0gramm.tv.data.manifest.ManifestProblem.Kind
import dev.pr0gramm.tv.data.manifest.Resource
import dev.pr0gramm.tv.data.manifest.ServerPath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.URI
import java.security.MessageDigest

class ServerClientTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val base = URI("https://tv.example/store/manifest.json")
    private val apk = "apk bytes ".repeat(100).toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private val emptyManifest = """{"version": 1, "date": "2026-11-18", "sites": [], "apps": []}"""

    /** A redirect to [location]. */
    private fun moved(location: String) = HttpFailure(302, location)

    private fun server(http: Http, language: () -> String = { "en" }) =
        ServerClient(ServerAddress.parse(base.toString())!!, http, language)

    private fun entry(versionCode: Long = 6, packageName: String = "dev.pr0gramm.tv.app") = IndexEntry(
        packageName = packageName,
        name = "pr0gramm",
        versionCode = versionCode,
        apk = URI("https://tv.example/store/app-6.apk"),
        sha256 = sha,
        size = apk.size.toLong(),
    )

    /** The answer for a body above the limit. */
    private val tooLarge = ServerAnswer.Invalid(listOf(ManifestProblem("", Kind.TOO_LARGE)))

    private suspend fun suspendFailure(block: suspend () -> Unit): Failure {
        try {
            block()
        } catch (e: UpdateFailure) {
            return e.failure
        }
        fail("expected UpdateFailure")
        error("unreachable")
    }

    @Test
    fun `a manifest above the limit is invalid and the client stops reading`() = runTest {
        val body = EndlessBody()
        val answer = server(FakeHttp(mapOf(base to body))).manifest()
        assertEquals(tooLarge, answer)
        assertEquals("manifest.json is larger than 2 MiB", (answer as ServerAnswer.Invalid).problems.single().english())
        assertTrue("took ${body.taken} bytes", body.taken <= ServerClient.MAX_MANIFEST_BYTES + 1L)
    }

    @Test
    fun `a manifest at the limit is read and parsed`() = runTest {
        val text = """{"version": 1, "date": "2026-11-18", "sites": [], "apps": []}"""
        val padded = text + " ".repeat(ServerClient.MAX_MANIFEST_BYTES - text.length)
        val answer = server(FakeHttp(mapOf(base to padded.toByteArray()))).manifest()
        assertTrue("$answer", answer is ServerAnswer.Valid)
    }

    @Test
    fun `a valid manifest is the answer with its text`() = runTest {
        val text = """{"version": 1, "date": "2026-11-18", "sites": [], "apps": []}"""
        val http = FakeHttp(mapOf(base to text.toByteArray()))
        val answer = server(http).manifest() as ServerAnswer.Valid
        assertEquals(text, answer.text)
        assertEquals(1, answer.manifest.version)
        assertEquals(listOf(base to mapOf("Accept-Language" to "en")), http.requests)
    }

    @Test
    fun `every request names the language of the app texts at the time of the request`() = runTest {
        val image = URI("https://tv.example/store/img/a.png")
        val http = FakeHttp(mapOf(base to emptyManifest.toByteArray(), image to apk, entry().apk to apk))
        var language = "de"
        val client = server(http) { language }
        client.manifest()
        language = "en"
        client.fetch(Resource.of("img/a.png")!!, tmp.root.resolve("a.png"))
        client.download(entry(), tmp.root.resolve("app.apk"))
        val sent = http.requests.map { (url, headers) -> url to headers["Accept-Language"] }
        assertEquals(listOf(base to "de", image to "en", entry().apk to "en"), sent)
    }

    @Test
    fun `an invalid manifest is the answer with the parser's problems`() = runTest {
        val answer = server(FakeHttp(mapOf(base to "{".toByteArray()))).manifest()
        assertEquals(ServerAnswer.Invalid(listOf(ManifestProblem("", Kind.JSON_KEY_EXPECTED, listOf("1")))), answer)
    }

    @Test
    fun `HTTP and network errors for the manifest are Unreachable`() = runTest {
        for (response in listOf(404, 500, IOException("down"))) {
            assertEquals(ServerAnswer.Unreachable, server(FakeHttp(mapOf(base to response))).manifest())
        }
    }

    @Test
    fun `the manifest follows a redirect`() = runTest {
        val mirror = URI("http://mirror.example/tv/manifest.json")
        val http = FakeHttp(mapOf(base to moved(mirror.toString()), mirror to emptyManifest.toByteArray()))
        val answer = server(http).manifest()
        assertTrue("$answer", answer is ServerAnswer.Valid)
        assertEquals(listOf(base, mirror), http.requests.map { it.first })
    }

    @Test
    fun `six redirects or a target off http and https make the manifest Unreachable`() = runTest {
        fun hop(n: Int) = URI("https://hop$n.example/manifest.json")
        val chain = (0..5).associate { n -> (if (n == 0) base else hop(n)) to moved(hop(n + 1).toString()) }
        val tooLong = FakeHttp(chain + (hop(6) to emptyManifest.toByteArray()))
        assertEquals(ServerAnswer.Unreachable, server(tooLong).manifest())
        assertEquals(6, tooLong.requests.size)

        val ftp = FakeHttp(mapOf(base to moved("ftp://tv.example/manifest.json")))
        assertEquals(ServerAnswer.Unreachable, server(ftp).manifest())
        assertEquals(listOf(base), ftp.requests.map { it.first })
    }

    @Test
    fun `the manifest limit holds after a redirect`() = runTest {
        val mirror = URI("https://mirror.example/manifest.json")
        val body = EndlessBody()
        val answer = server(FakeHttp(mapOf(base to moved(mirror.toString()), mirror to body))).manifest()
        assertEquals(tooLarge, answer)
        assertTrue("took ${body.taken} bytes", body.taken <= ServerClient.MAX_MANIFEST_BYTES + 1L)
    }

    @Test
    fun `a relative image loads from the saved address after a manifest redirect`() = runTest {
        val mirror = URI("https://mirror.example/manifest.json")
        val image = URI("https://tv.example/store/img/a.png")
        val manifest = emptyManifest.toByteArray()
        val http = FakeHttp(mapOf(base to moved(mirror.toString()), mirror to manifest, image to apk))
        val client = server(http)
        assertTrue(client.manifest() is ServerAnswer.Valid)
        val file = tmp.root.resolve("a.png")
        client.fetch(Resource.of("img/a.png")!!, file)
        assertEquals(image, http.requests.last().first)
        assertTrue(file.readBytes().contentEquals(apk))
    }

    @Test
    fun `an absolute image url is requested exactly as written`() = runTest {
        val url = "HTTPS://cdn.example/a.png?sig=1"
        val http = FakeHttp(mapOf(URI(url) to apk))
        server(http).fetch(Resource.of(url)!!, tmp.root.resolve("a.png"))
        assertEquals(url, http.requests.single().first.toString())
    }

    @Test
    fun `an image above the limit throws IMAGE_TOO_LARGE`() = runTest {
        val url = "https://cdn.example/a.png"
        val body = EndlessBody()
        val thrown = try {
            server(FakeHttp(mapOf(URI(url) to body))).fetch(Resource.of(url)!!, tmp.root.resolve("a.png"))
            null
        } catch (e: IOException) {
            e
        }
        assertEquals(ServerClient.IMAGE_TOO_LARGE, thrown?.message)
        assertTrue("took ${body.taken} bytes", body.taken <= ServerClient.MAX_IMAGE_BYTES + 1L)
    }

    @Test
    fun `an image redirect off http and https is refused`() = runTest {
        val url = "https://cdn.example/a.png"
        val http = FakeHttp(mapOf(URI(url) to moved("file:///etc/passwd")))
        val thrown = try {
            server(http).fetch(Resource.of(url)!!, tmp.root.resolve("a.png"))
            null
        } catch (e: RedirectRefused) {
            e
        }
        assertEquals("file:///etc/passwd", thrown?.location)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `the apk of an app from the manifest is next to it`() {
        val file = Apk(ServerPath.of("app-6.apk")!!, sha, apk.size.toLong())
        val app = CatalogApp("dev.pr0gramm.tv.app", "pr0gramm", null, 6, file)
        assertEquals(entry(), server(FakeHttp(emptyMap())).entry(app))
    }

    @Test
    fun `HTTP and network errors are UNREACHABLE`() = runTest {
        suspend fun downloadWith(response: Any) = suspendFailure {
            server(FakeHttp(mapOf(entry().apk to response))).download(entry(), tmp.root.resolve("u.apk"))
        }
        assertEquals(Failure.UNREACHABLE, downloadWith(401))
        assertEquals(Failure.UNREACHABLE, downloadWith(500))
        assertEquals(Failure.UNREACHABLE, downloadWith(IOException("down")))
    }

    @Test
    fun `an apk in a GitHub release loads over a redirect and is verified`() = runTest {
        val release = "https://github.com/o/r/releases/download/v1/a.apk"
        val blob = URI("https://objects.githubusercontent.com/blob/1")
        val file = Apk(Resource.of(release)!!, sha, apk.size.toLong())
        val app = CatalogApp("dev.pr0gramm.tv.app", "pr0gramm", null, 6, file)
        val http = FakeHttp(mapOf(URI(release) to moved(blob.toString()), blob to apk))
        val client = server(http)
        val into = tmp.root.resolve("a.apk")
        client.download(client.entry(app), into)
        assertTrue(into.readBytes().contentEquals(apk))
        assertEquals(listOf(URI(release), blob), http.requests.map { it.first })
    }

    @Test
    fun `an apk redirect off http and https is BAD_INDEX and leaves no file`() = runTest {
        val http = FakeHttp(mapOf(entry().apk to moved("ftp://tv.example/app-6.apk")))
        val file = tmp.root.resolve("u.apk")
        assertEquals(Failure.BAD_INDEX, suspendFailure { server(http).download(entry(), file) })
        assertEquals(listOf(entry().apk), http.requests.map { it.first })
        assertFalse(file.exists())
    }

    @Test
    fun `download writes the verified apk and reports progress`() = runTest {
        val http = FakeHttp(mapOf(URI("https://tv.example/store/app-6.apk") to apk))
        val file = tmp.root.resolve("update.apk")
        val progress = mutableListOf<Int>()
        server(http).download(entry(), file) { progress += it }
        assertTrue(file.readBytes().contentEquals(apk))
        assertEquals(100, progress.last())
        assertEquals(mapOf("Accept-Language" to "en"), http.requests.single().second)
    }

    @Test
    fun `SHA mismatch is BROKEN_APK and leaves no file`() = runTest {
        val tampered = apk.copyOf().also { it[0] = 'X'.code.toByte() }
        val http = FakeHttp(mapOf(URI("https://tv.example/store/app-6.apk") to tampered))
        val file = tmp.root.resolve("update.apk")
        assertEquals(Failure.BROKEN_APK, suspendFailure { server(http).download(entry(), file) })
        assertFalse(file.exists())
    }

    @Test
    fun `wrong size is BROKEN_APK`() = runTest {
        val file = tmp.root.resolve("update.apk")
        val longer = FakeHttp(mapOf(URI("https://tv.example/store/app-6.apk") to apk + 1))
        assertEquals(Failure.BROKEN_APK, suspendFailure { server(longer).download(entry(), file) })
        val shorter = FakeHttp(mapOf(URI("https://tv.example/store/app-6.apk") to apk.copyOf(apk.size - 1)))
        assertEquals(Failure.BROKEN_APK, suspendFailure { server(shorter).download(entry(), file) })
        assertFalse(file.exists())
    }
}
