package dev.pr0gramm.tv.data.catalogsync

import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.ManifestResult
import dev.pr0gramm.tv.data.manifest.Resource
import dev.pr0gramm.tv.data.manifest.ServerPath
import dev.pr0gramm.tv.data.manifest.SiteId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CachedCatalogTest {
    private val manifest = """{"version": 1, "date": "2026-11-18",
        "sites": [{"id": "nova", "name": "Nova", "url": "https://nova.example/", "banner": "img/nova.png"}],
        "apps": []}"""

    private fun stored(present: Set<Resource> = emptySet(), text: String = manifest) =
        CachedCatalog((Manifest.parse(text) as ManifestResult.Valid).manifest, File("/data/catalog/1"), present)

    private fun url(text: String) = Resource.of(text)!!

    @Test fun `a catalog finds its sites by id`() {
        val catalog = stored()
        assertEquals(listOf(SiteId("nova")), catalog.sites.map { it.id })
        assertEquals(setOf(SiteId("nova")), catalog.ids)
        assertEquals("Nova", catalog[SiteId("nova")]?.name)
        assertNull(catalog[SiteId("weg")])
    }

    @Test fun `a stored image resolves to its file, a missing one to null`() {
        val nova = ServerPath.of("img/nova.png")!!
        val catalog = stored(present = setOf(nova))
        assertEquals("file:///data/catalog/1/img/nova.png", catalog.image(nova))
        assertNull(catalog.image(ServerPath.of("img/kodi.png")!!))
    }

    @Test fun `an absolute url lives flat in the version folder as url- and the sha256 of its text`() {
        val file = stored().file(url("https://cdn.x/a.png"))
        assertEquals(File("/data/catalog/1"), file.parentFile)
        assertTrue(file.name, Regex("url-[0-9a-f]{64}").matches(file.name))
        // `printf '%s' 'https://cdn.x/a.png' | shasum -a 256`
        assertEquals("url-18912e7e1a5fe92e8d315a289efbbd3e69f11ae0903954fa5629301f0d749434", file.name)
    }

    @Test fun `two urls are one image only with the same text`() {
        val catalog = stored()
        assertNotEquals(catalog.file(url("https://cdn.x/a.png")), catalog.file(url("https://CDN.x/a.png")))
        assertEquals(catalog.file(url("https://cdn.x/a.png")), catalog.file(url("https://cdn.x/a.png")))
    }

    @Test fun `the same url in two entries is one image`() {
        val text = """{"version": 1, "date": "2026-11-18",
            "sites": [{"id": "nova", "name": "Nova", "url": "https://nova.example/", "banner": "https://cdn.x/a.png"}],
            "apps": [{"package": "org.kodi", "name": "Kodi", "image": "https://cdn.x/a.png", "versionCode": 1,
            "apk": "kodi.apk", "sha256": "${"ab".repeat(32)}", "size": 10}]}"""
        val image = url("https://cdn.x/a.png")
        val catalog = stored(present = setOf(image), text = text)
        assertEquals(setOf(image), catalog.manifest.images)
        assertEquals("file://${catalog.file(image).absolutePath}", catalog.image(image))
    }
}
