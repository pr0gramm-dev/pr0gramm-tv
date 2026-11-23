package dev.pr0gramm.tv.data.savedpages

import android.content.Context
import android.webkit.WebSettings
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.pr0gramm.tv.core.useragent.siteUserAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** The bytes of an image that passed the cover check of [SavedPageStore.cover]; only the store makes one. */
class Cover internal constructor(internal val bytes: ByteArray)

/** Writes a cover as the image file of a page, inside [SavedPageStore.update]. */
fun interface CoverFiles {
    /** The stored file, or null when it could not be written. */
    fun write(key: SavedPageKey, cover: Cover): File?
}

/** What an edit of [SavedPageStore.update] stores ([pages]) and returns ([result]). */
data class Edit<R>(val pages: List<SavedPage>, val result: R)

/**
 * The stored list of one [shelf] and its cover images, newest first. It only loads and stores: the rules of a
 * shelf run in the edit of [update]. Image files nobody refers to are removed after each [update] and whenever
 * [entries] is collected.
 */
class SavedPageStore internal constructor(
    private val store: DataStore<List<SavedPage>>,
    private val covers: CoverSource,
    private val imageDir: File,
    val shelf: Shelf,
) {
    private val writes = Mutex()
    private val files = CoverFiles(::write)

    /** The stored list; collecting it first removes unreferenced image files. */
    val entries: Flow<List<SavedPage>> = flow {
        writes.withLock { deleteUnreferenced(store.data.first()) }
        emitAll(store.data)
    }

    /** The list as stored now; unlike [entries] it removes no files. */
    suspend fun current(): List<SavedPage> = store.data.first()

    /**
     * The cover at [imageUrl] for the page at [pageUrl], or null when it is missing, over 5 MB, not JPEG/PNG/WebP or
     * fails to load.
     */
    suspend fun cover(imageUrl: String, pageUrl: String): Cover? =
        covers.fetch(imageUrl, pageUrl)?.takeIf(::isCover)?.let(::Cover)

    /** The bytes of a stored image (of another shelf), or null when it cannot be read. */
    suspend fun cover(image: File): Cover? =
        withContext(Dispatchers.IO) { runCatching { image.readBytes() }.getOrNull() }?.let(::Cover)

    /**
     * Runs [edit] on the stored list under one lock and stores its pages, then removes image files nobody refers
     * to. A rule checked inside [edit] sees every earlier update, so two saves at once cannot both pass it.
     */
    suspend fun <R> update(edit: suspend (pages: List<SavedPage>, covers: CoverFiles) -> Edit<R>): R =
        writes.withLock {
            var done: Edit<R>? = null
            store.updateData { current -> edit(current, files).also { done = it }.pages }
            deleteUnreferenced(store.data.first())
            checkNotNull(done).result
        }

    private fun isCover(bytes: ByteArray): Boolean = bytes.size <= CoverDownloader.LIMIT && sniffed(bytes)

    private fun sniffed(b: ByteArray): Boolean =
        b.startsWith(JPEG) || b.startsWith(PNG) || (b.startsWith(RIFF) && b.startsWith(WEBP, WEBP_TAG_AT))

    private fun ByteArray.startsWith(magic: ByteArray, at: Int = 0) =
        size >= at + magic.size && magic.indices.all { this[at + it] == magic[it] }

    /** First 16 bytes of sha256(key) as hex, like the old app. Written to a temp file and renamed. */
    private fun write(key: SavedPageKey, cover: Cover): File? = runCatching {
        imageDir.mkdirs()
        val name = MessageDigest.getInstance("SHA-256").digest(key.value.toByteArray())
            .take(NAME_BYTES).joinToString("") { "%02x".format(it) } + ".jpg"
        val target = File(imageDir, name)
        val tmp = File(imageDir, "$name.tmp")
        tmp.writeBytes(cover.bytes)
        check(tmp.renameTo(target)) { "rename failed" }
        target
    }.getOrNull()

    private suspend fun deleteUnreferenced(list: List<SavedPage>) = withContext(Dispatchers.IO) {
        val used = list.mapNotNullTo(hashSetOf()) { it.image?.name }
        imageDir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    companion object {
        private const val WEBP_TAG_AT = 8
        private val JPEG = byteArrayOf(-1, -40, -1)
        private val PNG = byteArrayOf(-119, 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        private val RIFF = "RIFF".toByteArray()
        private val WEBP = "WEBP".toByteArray()
        private const val NAME_BYTES = 16

        /** The [shelf] of the server whose data lives in [dir] (see `serverDir`). */
        fun forServer(context: Context, dir: File, shelf: Shelf): SavedPageStore = forServer(
            dir = dir,
            shelf = shelf,
            covers = CoverDownloader { siteUserAgent(WebSettings.getDefaultUserAgent(context)) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )

        /** The list in `<dir>/datastore/<Shelf.file>`, the images in `<dir>/<Shelf.dir>` of [shelf]. */
        internal fun forServer(dir: File, shelf: Shelf, covers: CoverSource, scope: CoroutineScope): SavedPageStore =
            create(File(dir, "datastore/${shelf.file}"), File(dir, shelf.dir), shelf, covers, scope)

        /** A corrupt [file] is replaced by an empty list. */
        internal fun create(
            file: File,
            imageDir: File,
            shelf: Shelf,
            covers: CoverSource,
            scope: CoroutineScope,
        ): SavedPageStore {
            val store = DataStoreFactory.create(
                serializer = SavedListSerializer(imageDir),
                corruptionHandler = ReplaceFileCorruptionHandler { emptyList() },
                scope = scope,
                produceFile = { file },
            )
            return SavedPageStore(store, covers, imageDir, shelf)
        }
    }
}
