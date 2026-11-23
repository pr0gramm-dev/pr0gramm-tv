package dev.pr0gramm.tv.ui.browser

import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.catalogsync.catalogOf
import dev.pr0gramm.tv.data.manifest.Control
import dev.pr0gramm.tv.data.manifest.Layout
import dev.pr0gramm.tv.data.manifest.ServerPath
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.data.manifest.SiteOptions
import dev.pr0gramm.tv.data.manifest.Zoom
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class SplashLookTest {
    private val defaults = SiteOptions(Control.FOCUS, Zoom.of(100)!!, Layout.NORMAL, true, false)
    private val banner = ServerPath.of("img/nova.png")!!

    private fun site(banner: ServerPath?) = Site(
        SiteId("nova"), "Nova", "https://nova.example/", null, null, emptyList(), 0xFF8800, false, emptySet(),
        defaults, banner,
    )

    private val withBanner = site(banner)
    private val builtIn = catalogOf(withBanner)

    @Test
    fun anEntryImageBeatsTheBanner() {
        val cover = File("cover.webp")
        assertEquals(
            SplashLook(cover, "Folge 1", "Nova", 0xFF8800),
            SplashLook.of(withBanner, builtIn, "Folge 1", cover),
        )
    }

    @Test
    fun aSiteShowsItsBannerFromTheCatalog() {
        assertEquals(builtIn.image(banner), SplashLook.of(withBanner, builtIn, null, null).picture)
        val dir = File("/data/catalog/2")
        val downloaded = CachedCatalog(builtIn.manifest, dir, present = setOf(banner))
        assertEquals("file:///data/catalog/2/img/nova.png", SplashLook.of(withBanner, downloaded, null, null).picture)
    }

    @Test
    fun aBannerNotYetDownloadedLeavesTheNameOnItsColour() {
        val downloaded = CachedCatalog(builtIn.manifest, File("/data/catalog/2"), present = emptySet())
        assertEquals(SplashLook(null, null, "Nova", 0xFF8800), SplashLook.of(withBanner, downloaded, null, null))
    }

    @Test
    fun withoutImageAndBannerThereIsNoPicture() {
        val plain = site(banner = null)
        assertEquals(SplashLook(null, null, "Nova", 0xFF8800), SplashLook.of(plain, catalogOf(plain), null, null))
    }
}
