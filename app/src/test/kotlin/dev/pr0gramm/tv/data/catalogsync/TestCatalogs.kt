package dev.pr0gramm.tv.data.catalogsync

import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.Site
import java.io.File
import java.time.LocalDate

/** A stored catalog of [sites], with [apps] when given, and all its images loaded. */
fun catalogOf(vararg sites: Site, apps: List<CatalogApp> = emptyList()): CachedCatalog {
    val manifest = Manifest(
        version = 1,
        date = LocalDate.of(2026, 11, 18),
        sites = sites.toList(),
        apps = apps,
    )
    return CachedCatalog(manifest, File("/data/catalog/1"), manifest.images)
}
