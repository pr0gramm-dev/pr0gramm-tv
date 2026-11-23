package dev.pr0gramm.tv.data.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ResourceTest {
    private val base = URI("http://tv.example.org:8080/catalog/manifest.json")

    @Test
    fun ofTakesARelativePathAsServerPath() {
        val resource = Resource.of("img/a.png")
        assertTrue(resource is ServerPath)
        assertEquals("img/a.png", (resource as ServerPath).path)
    }

    @Test
    fun ofTakesHttpUrlsInAnyCaseAndWithQueryAsTheyAre() {
        for (raw in listOf("HTTPS://x/a.apk", "https://x/a.png?sig=1", "http://x:8080/a.png")) {
            val resource = Resource.of(raw)
            assertTrue(raw, resource is AbsoluteUrl)
            assertEquals(raw, (resource as AbsoluteUrl).text)
        }
    }

    @Test
    fun ofRejectsOtherSchemesNoHostUserInfoFragmentAndDotSegments() {
        val rejected = listOf(
            "ftp://x/a.apk",
            "file:///a.apk",
            "https:///a.apk",
            "https://user@x/a.apk",
            "https://x/a.png#v2",
            "../a",
            "",
            "https://x y/a.png",
        )
        for (raw in rejected) assertNull(raw, Resource.of(raw))
    }

    @Test
    fun isWebIsHttpOrHttpsInAnyCaseWithAHost() {
        assertTrue(Resource.isWeb(URI("https://x/a")))
        assertTrue(Resource.isWeb(URI("HTTP://x/a")))
        assertFalse(Resource.isWeb(URI("ftp://x/a")))
        assertFalse(Resource.isWeb(URI("https:///a")))
        assertFalse(Resource.isWeb(URI("img/a.png")))
    }

    @Test
    fun resolveSetsAPathAgainstTheBase() {
        assertEquals(URI("http://tv.example.org:8080/catalog/img/a.png"), Resource.of("img/a.png")!!.resolve(base))
    }

    @Test
    fun resolveReturnsAnAbsoluteUrlUnchanged() {
        val raw = "HTTPS://cdn.x/a.png?sig=1"
        assertEquals(URI(raw), Resource.of(raw)!!.resolve(base))
    }

    /** A manifest with one site and one app; [banner], [image] and [apk] are raw JSON strings. */
    private fun manifest(banner: String = "img/nova.png", image: String = "img/kodi.png", apk: String = "kodi.apk") =
        """{"version": 1, "date": "2026-11-18",
            "sites": [{"id": "nova", "name": "Nova", "url": "https://nova.example/", "banner": "$banner"}],
            "apps": [{"package": "org.kodi", "name": "Kodi", "image": "$image", "versionCode": 1,
            "apk": "$apk", "sha256": "${"ab".repeat(32)}", "size": 10}]}"""

    private fun problems(text: String) =
        (Manifest.parse(text) as ManifestResult.Invalid).problems.map(ManifestProblem::english)

    @Test
    fun theManifestTakesAbsoluteUrlsForBannerImageAndApk() {
        val text = manifest("HTTPS://cdn.x/nova.png", "https://cdn.x/kodi.png?sig=1", "https://github.com/o/r/a.apk")
        val manifest = (Manifest.parse(text) as ManifestResult.Valid).manifest
        assertEquals(Resource.of("HTTPS://cdn.x/nova.png"), manifest.sites.single().banner)
        assertEquals(Resource.of("https://cdn.x/kodi.png?sig=1"), manifest.apps.single().image)
        assertEquals(Resource.of("https://github.com/o/r/a.apk"), manifest.apps.single().apk.file)
    }

    @Test
    fun everyRejectedValueGetsTheSameProblemText() {
        val rule = "must be a relative path such as img/a.png, or an http(s) URL with a host, " +
            "without a user and without a fragment"
        val rejected =
            listOf("ftp://x/a.apk", "file:///a.apk", "https:///a.apk", "https://user@x/a.apk", "https://x/a.png#v2")
        for (raw in rejected) {
            assertEquals(listOf("sites[0] (nova).banner: \"$raw\" $rule"), problems(manifest(banner = raw)))
            assertEquals(listOf("apps[0] (org.kodi).image: \"$raw\" $rule"), problems(manifest(image = raw)))
            assertEquals(listOf("apps[0] (org.kodi).apk: \"$raw\" $rule"), problems(manifest(apk = raw)))
        }
    }
}
