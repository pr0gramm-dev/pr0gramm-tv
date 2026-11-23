package dev.pr0gramm.tv.data.releases

import dev.pr0gramm.tv.core.appversion.AppVersion
import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.HttpFailure
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.core.updatefailure.UpdateFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

class GithubReleasesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val api = URI("https://api.github.com/repos/owner/repo/releases/latest")
    private val asset = URI("https://github.com/owner/repo/releases/download/v1.2.3/pr0gramm-tv.apk")
    private val blob = URI("https://objects.githubusercontent.com/github-production-release-asset/1")
    private val apk = "apk bytes ".repeat(100).toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private val release = Release(AppVersion.parse("1.2.3")!!, asset, apk.size.toLong(), sha)

    /** Answers by URL: bytes for 200, an [HttpFailure] or [IOException] to throw, or 404. Records every request. */
    private class FakeHttp(private val responses: Map<URI, Any>) : Http {
        val requests = mutableListOf<Pair<URI, Map<String, String>>>()

        override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T {
            requests += url to headers
            return when (val response = responses[url] ?: HttpFailure(404)) {
                is ByteArray -> read(response.inputStream())
                is String -> read(response.byteInputStream())
                is IOException -> throw response
                else -> error("bad fake response $response")
            }
        }
    }

    private fun assetJson(
        name: String = "pr0gramm-tv.apk",
        size: String = "${apk.size}",
        digest: String? = "\"sha256:$sha\"",
        url: String = asset.toString(),
    ) = """{"name": "$name", "size": $size, "digest": ${digest ?: "null"}, "browser_download_url": "$url"}"""

    private fun releaseJson(tag: String = "v1.2.3", assets: String = "[${assetJson()}]") =
        """{"tag_name": "$tag", "draft": false, "assets": $assets}"""

    private fun releases(vararg responses: Pair<URI, Any>) = GithubReleases("owner/repo", FakeHttp(mapOf(*responses)))

    private suspend fun failure(block: suspend () -> Unit): Failure {
        try {
            block()
        } catch (e: UpdateFailure) {
            return e.failure
        }
        fail("expected UpdateFailure")
        error("unreachable")
    }

    @Test
    fun `latest reads tag, asset url, size and digest of the latest release`() = runTest {
        val http = FakeHttp(mapOf(api to releaseJson()))
        assertEquals(release, GithubReleases("owner/repo", http).latest())
        val (url, headers) = http.requests.single()
        assertEquals(api, url)
        assertEquals("application/vnd.github+json", headers["Accept"])
        // GitHub gets no Accept-Language: the header goes to the catalog server only (AC16).
        assertNull(headers["Accept-Language"])
    }

    @Test
    fun `another asset next to pr0gramm-tv apk is ignored`() = runTest {
        val other = assetJson(name = "notes.txt", digest = null, url = "https://example.org/notes.txt")
        assertEquals(release, releases(api to releaseJson(assets = "[$other, ${assetJson()}]")).latest())
    }

    @Test
    fun `a tag that is no version is ignored`() = runTest {
        for (tag in listOf("1.2", "v1.2", "release-1", "v1.100.0", "v1.2.3-beta")) {
            assertNull(tag, releases(api to releaseJson(tag = tag, assets = "[]")).latest())
        }
    }

    @Test
    fun `a release without the apk or its digest is BAD_INDEX`() = runTest {
        val broken = listOf(
            "[]",
            "[${assetJson(name = "app.apk")}]",
            "[${assetJson(digest = null)}]",
            "[${assetJson(digest = "\"md5:abc\"")}]",
            "[${assetJson(digest = "\"sha256:1234\"")}]",
            "[${assetJson(size = "0")}]",
            "[${assetJson(url = "http://github.com/owner/repo/pr0gramm-tv.apk")}]",
            "[${assetJson(url = "https://evil.example/pr0gramm-tv.apk")}]",
            "[${assetJson(url = "https://objects.githubusercontent.com/a.apk")}]",
        )
        for (assets in broken) {
            assertEquals(assets, Failure.BAD_INDEX, failure { releases(api to releaseJson(assets = assets)).latest() })
        }
        assertEquals(Failure.BAD_INDEX, failure { releases(api to "{not json").latest() })
        assertEquals(Failure.BAD_INDEX, failure { releases(api to """{"assets": []}""").latest() })
    }

    @Test
    fun `a broken release names the problem in English`() = runTest {
        suspend fun message(text: String): String? = try {
            releases(api to text).latest()
            null
        } catch (e: UpdateFailure) {
            e.message
        }
        val noJson = "BAD_INDEX not valid JSON: field name in quotes expected (character 1)"
        assertEquals(noJson, message("{not json"))
        val noSize = "BAD_INDEX assets.pr0gramm-tv.apk.size: missing"
        assertEquals(noSize, message(releaseJson(assets = "[${assetJson(size = "null")}]")))
    }

    @Test
    fun `HTTP and network errors of the API are UNREACHABLE`() = runTest {
        assertEquals(Failure.UNREACHABLE, failure { releases(api to HttpFailure(403)).latest() })
        assertEquals(Failure.UNREACHABLE, failure { releases().latest() })
        assertEquals(Failure.UNREACHABLE, failure { releases(api to IOException("down")).latest() })
    }

    @Test
    fun `download follows the redirect to githubusercontent and writes the verified apk`() = runTest {
        val http = FakeHttp(mapOf(asset to HttpFailure(302, blob.toString()), blob to apk))
        val file = tmp.root.resolve("update.apk")
        val progress = mutableListOf<Int>()
        GithubReleases("owner/repo", http).download(release, file) { progress += it }
        assertTrue(file.readBytes().contentEquals(apk))
        assertEquals(100, progress.last())
        assertEquals(listOf(asset, blob), http.requests.map { it.first })
    }

    @Test
    fun `a redirect to another host is BAD_INDEX and never asked`() = runTest {
        val foreign = URI("https://evil.example/pr0gramm-tv.apk")
        val http = FakeHttp(mapOf(asset to HttpFailure(302, foreign.toString()), foreign to apk))
        val file = tmp.root.resolve("update.apk")
        assertEquals(Failure.BAD_INDEX, failure { GithubReleases("owner/repo", http).download(release, file) })
        assertEquals(listOf(asset), http.requests.map { it.first })
        assertFalse(file.exists())
    }

    @Test
    fun `an http redirect to githubusercontent is BAD_INDEX`() = runTest {
        val plain = "http://objects.githubusercontent.com/github-production-release-asset/1"
        val http = FakeHttp(mapOf(asset to HttpFailure(302, plain)))
        val file = tmp.root.resolve("update.apk")
        assertEquals(Failure.BAD_INDEX, failure { GithubReleases("owner/repo", http).download(release, file) })
    }

    @Test
    fun `a download with another SHA-256 is BROKEN_APK and leaves no file`() = runTest {
        val tampered = apk.copyOf().also { it[0] = 'X'.code.toByte() }
        val file = tmp.root.resolve("update.apk")
        val failure = failure { releases(asset to tampered).download(release, file) }
        assertEquals(Failure.BROKEN_APK, failure)
        assertFalse(file.exists())
    }

    @Test
    fun `a failed download is UNREACHABLE and leaves no file`() = runTest {
        val file = tmp.root.resolve("update.apk")
        assertEquals(Failure.UNREACHABLE, failure { releases(asset to HttpFailure(500)).download(release, file) })
        assertFalse(file.exists())
    }
}
