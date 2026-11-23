package dev.pr0gramm.tv.data.catalogsync

import dev.pr0gramm.tv.data.manifest.AbsoluteUrl
import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.Resource
import dev.pr0gramm.tv.data.manifest.ServerPath
import dev.pr0gramm.tv.data.manifest.Site
import dev.pr0gramm.tv.data.manifest.SiteId
import java.io.File
import java.security.MessageDigest

/**
 * What the app shows: a [manifest] from the server, stored with its images in [dir]; [present] are the images
 * loaded so far. `CatalogSync` owns the current one.
 */
data class CachedCatalog(val manifest: Manifest, val dir: File, val present: Set<Resource>) {
    val sites: List<Site> get() = manifest.sites

    val ids: Set<SiteId> get() = manifest.sites.mapTo(linkedSetOf()) { it.id }

    operator fun get(id: SiteId): Site? = manifest.sites.firstOrNull { it.id == id }

    /** The Coil model of [resource], null while it is not loaded: then the tile shows the name. */
    fun image(resource: Resource): String? =
        if (resource in present) "file://${file(resource).absolutePath}" else null

    /**
     * The only rule for the cache file of [resource] in [dir]: a [ServerPath] under its path, an [AbsoluteUrl] flat
     * as `url-<SHA-256>`, 64 lowercase hex digits over its exact text. So equal text is one file, any other text not.
     */
    internal fun file(resource: Resource): File = when (resource) {
        is ServerPath -> File(dir, resource.path)
        is AbsoluteUrl -> File(dir, "url-" + sha256(resource.text))
    }

    private companion object {
        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
