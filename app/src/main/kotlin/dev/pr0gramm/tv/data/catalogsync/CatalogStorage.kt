package dev.pr0gramm.tv.data.catalogsync

import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.ManifestResult
import dev.pr0gramm.tv.data.manifest.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The files of the catalog under [root]: the current manifest is `<root>/manifest.json`, its images live in
 * `<root>/<version>/`. `CatalogSync` is the only writer.
 */
class CatalogStorage internal constructor(private val root: File) {
    /** The stored manifest with the images on disk, or null without a valid one. */
    fun stored(): CachedCatalog? {
        val file = File(root, MANIFEST)
        val text = if (file.isFile) runCatching { file.readText() }.getOrNull() else null
        val manifest = (text?.let(Manifest::parse) as? ManifestResult.Valid)?.manifest ?: return null
        // CachedCatalog.file owns the cache file names and reads only dir, so a catalog without images can find them.
        val empty = CachedCatalog(manifest, dir(manifest), emptySet())
        // A file of 0 bytes is an image cut short by a power loss: it counts as missing and loads again.
        val present = manifest.images.filterTo(linkedSetOf()) { empty.file(it).let { f -> f.isFile && f.length() > 0 } }
        return empty.copy(present = present)
    }

    /**
     * An empty folder for the images of [manifest], then [text] as the stored manifest; replacing the stored one is
     * the switch. Returns the new catalog without images. Throws [IOException] when a file cannot be written.
     */
    suspend fun store(manifest: Manifest, text: String): CachedCatalog {
        val dir = dir(manifest)
        withContext(Dispatchers.IO) {
            dir.deleteRecursively()
            if (!dir.mkdirs()) throw IOException("cannot create $dir")
            val tmp = File(root, "$MANIFEST.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(File(root, MANIFEST))) throw IOException("cannot replace $MANIFEST")
        }
        return CachedCatalog(manifest, dir, emptySet())
    }

    /** Deletes the stored file of [image] in [catalog]; a missing file is no error. */
    suspend fun drop(catalog: CachedCatalog, image: Resource) {
        withContext(Dispatchers.IO) { catalog.file(image).delete() }
    }

    /** Deletes the image folders of every other version than [keep]. */
    suspend fun dropOthers(keep: CachedCatalog) {
        withContext(Dispatchers.IO) {
            root.listFiles()?.filter { it.isDirectory && it != keep.dir }?.forEach { it.deleteRecursively() }
        }
    }

    /** Loads [image] of [catalog] from [server] into its folder; false when that fails, then nothing is left. */
    suspend fun fetchImage(server: ServerClient, catalog: CachedCatalog, image: Resource): Boolean {
        val target = catalog.file(image)
        val tmp = File(target.path + ".tmp")
        return try {
            withContext(Dispatchers.IO) { target.parentFile?.mkdirs() }
            server.fetch(image, tmp)
            withContext(Dispatchers.IO) { if (!tmp.renameTo(target)) throw IOException("cannot create $target") }
            true
        } catch (_: IOException) {
            withContext(Dispatchers.IO) { tmp.delete() }
            false
        }
    }

    private fun dir(manifest: Manifest) = File(root, manifest.version.toString())

    companion object {
        private const val MANIFEST = "manifest.json"

        /** The catalog of the server whose data lives in [dir] (see `serverDir`): `<dir>/catalog/`. */
        fun forServer(dir: File): CatalogStorage = CatalogStorage(File(dir, "catalog"))
    }
}
