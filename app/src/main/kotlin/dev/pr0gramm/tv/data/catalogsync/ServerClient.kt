package dev.pr0gramm.tv.data.catalogsync

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.HttpFailure
import dev.pr0gramm.tv.core.http.RedirectRefused
import dev.pr0gramm.tv.core.http.VerifiedDownload
import dev.pr0gramm.tv.core.http.follow
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import dev.pr0gramm.tv.data.manifest.ManifestProblem.Kind
import dev.pr0gramm.tv.data.manifest.ManifestResult
import dev.pr0gramm.tv.data.manifest.Resource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * The static server at [address]: `manifest.json` at its [ServerAddress.manifestUrl], without a token (the content
 * is not secret). The files the manifest names are next to it or at any http(s) URL ([Resource.resolve]). Every
 * request goes through [get]: it follows up to five redirects to any http or https target and checks no origin.
 * Every request sends `Accept-Language` with [language], the language of the app texts (`en` or `de`, AC16). The
 * client asks [language] for each request: a language switch keeps the process and this client.
 */
class ServerClient(address: ServerAddress, private val http: Http, private val language: () -> String) {
    private val manifestUrl: URI = address.manifestUrl

    /**
     * `manifest.json` parsed: [ServerAnswer.Valid] with its text, [ServerAnswer.Invalid] with the parser's problems,
     * or [ServerAnswer.Unreachable] for a network error, any HTTP answer but 200 after the redirects, or a refused
     * redirect. A body larger than [MAX_MANIFEST_BYTES] is [ServerAnswer.Invalid] with the problem
     * [Kind.TOO_LARGE]: the client reads no more of it, so a server the user typed cannot fill the
     * memory.
     */
    suspend fun manifest(): ServerAnswer {
        val text = try {
            get(manifestUrl) { input ->
                val out = ByteArrayOutputStream()
                if (input.copyAtMost(out, MAX_MANIFEST_BYTES)) out.toByteArray().decodeToString() else null
            }
        } catch (_: IOException) {
            return ServerAnswer.Unreachable
        }
        return text?.let(::parsed) ?: ServerAnswer.Invalid(listOf(ManifestProblem("", Kind.TOO_LARGE)))
    }

    private fun parsed(text: String): ServerAnswer = when (val result = Manifest.parse(text)) {
        is ManifestResult.Valid -> ServerAnswer.Valid(result.manifest, text)
        is ManifestResult.Invalid -> ServerAnswer.Invalid(result.problems)
    }

    /**
     * Writes the file [image] to [into]. Throws [IOException]: an [HttpFailure] for any answer but 200, a
     * [RedirectRefused], or [IMAGE_TOO_LARGE] for a body above [MAX_IMAGE_BYTES] (the client reads no more of it).
     */
    suspend fun fetch(image: Resource, into: File) {
        val complete = get(image.resolve(manifestUrl)) { input ->
            FileOutputStream(into).use { out ->
                val whole = input.copyAtMost(out, MAX_IMAGE_BYTES)
                out.fd.sync() // the caller renames the file next: the bytes must be on disk before that
                whole
            }
        }
        if (!complete) throw IOException(IMAGE_TOO_LARGE)
    }

    /** [app] from the manifest with its APK resolved against the manifest URL ([Resource.resolve]). */
    internal fun entry(app: CatalogApp): IndexEntry =
        IndexEntry(app.pkg, app.name, app.versionCode, app.apk.file.resolve(manifestUrl), app.apk.sha256, app.apk.size)

    /**
     * Streams the APK of [entry] into [into] while checking size and SHA-256 ([VerifiedDownload.save]). A mismatch is
     * [Failure.BROKEN_APK], any HTTP or network error [Failure.UNREACHABLE], a refused redirect [Failure.BAD_INDEX].
     * On any failure [into] is deleted. [progress] gets each new percent on the thread that reads the body.
     */
    internal suspend fun download(entry: IndexEntry, into: File, progress: (Int) -> Unit = {}) {
        VerifiedDownload(entry.size, entry.sha256).save(into, progress) { read -> get(entry.apk, read) }
    }

    /**
     * The one request rule: GETs [url] with `Accept-Language` and follows up to five redirects to http(s) targets
     * ([Resource.isWeb]).
     */
    private suspend fun <T> get(url: URI, read: (InputStream) -> T): T =
        http.follow(url, mapOf(ACCEPT_LANGUAGE to language()), Resource::isWeb, read)

    internal companion object {
        /** The largest `manifest.json` the app reads: 2 MiB, far above a catalog of some hundred entries. */
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024

        /** The largest catalog image the app reads: 5 MiB. */
        const val MAX_IMAGE_BYTES = 5 * 1024 * 1024

        /** The cause for an image above [MAX_IMAGE_BYTES]. */
        const val IMAGE_TOO_LARGE = "image is larger than 5 MiB"

        private const val ACCEPT_LANGUAGE = "Accept-Language"

        /**
         * Copies this stream to [out]: true for all of it, false as soon as it has more than [limit] bytes. Reads at
         * most [limit] + 1 bytes, so an endless body cannot fill the memory or the disk.
         */
        private fun InputStream.copyAtMost(out: OutputStream, limit: Int): Boolean {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val n = read(buffer, 0, minOf(buffer.size, limit + 1 - total))
                if (n < 0) return true
                out.write(buffer, 0, n)
                total += n
                if (total > limit) return false
            }
        }
    }
}

/** What [ServerClient.manifest] got from the server. */
sealed interface ServerAnswer {
    /** A valid manifest and the [text] it was parsed from. */
    data class Valid(val manifest: Manifest, val text: String) : ServerAnswer

    /** The server answered with an invalid manifest; [problems] are the parser's, in its order, never empty. */
    data class Invalid(val problems: List<ManifestProblem>) : ServerAnswer {
        init {
            require(problems.isNotEmpty()) { "an invalid answer names at least one problem" }
        }
    }

    /** No network, or an HTTP answer but 200. */
    data object Unreachable : ServerAnswer
}
