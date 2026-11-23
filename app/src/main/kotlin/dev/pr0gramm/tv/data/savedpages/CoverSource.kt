package dev.pr0gramm.tv.data.savedpages

import dev.pr0gramm.tv.core.useragent.IMAGE_ACCEPT
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Loads the cover image of a page. An adapter interface inside this slice, not a port: tests use a fake. */
interface CoverSource {
    /** The raw bytes, or null when the image could not be loaded. [SavedPageStore.cover] checks size and format. */
    suspend fun fetch(imageUrl: String, pageUrl: String): ByteArray?
}

/**
 * Downloads at most [LIMIT] + 1 bytes (so the caller can see that the image is too big) within
 * [TOTAL_MS]. Any failure is null. [userAgent] is asked on the first download, on the IO thread: reading the
 * WebView's user agent loads the WebView, which would cost the launcher's cold start about 200 ms on the TCL.
 */
internal class CoverDownloader(userAgent: () -> String) : CoverSource {
    private val userAgent by lazy(userAgent)

    override suspend fun fetch(imageUrl: String, pageUrl: String): ByteArray? = kotlinx.coroutines.withContext(
        kotlinx.coroutines.Dispatchers.IO,
    ) {
        runCatching { download(imageUrl, pageUrl) }.getOrNull()
    }

    private fun download(imageUrl: String, pageUrl: String): ByteArray? {
        val connection = URL(imageUrl).openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", IMAGE_ACCEPT)
            origin(pageUrl)?.let { connection.setRequestProperty("Referer", it) }
            if (connection.responseCode in HTTP_OK_RANGE) connection.inputStream.use(::readLimited) else null
        } finally {
            connection.disconnect()
        }
    }

    /** Up to [LIMIT] + 1 bytes, or null when [TOTAL_MS] is over. */
    private fun readLimited(input: java.io.InputStream): ByteArray? {
        val deadline = System.nanoTime() + TOTAL_MS * NANOS_PER_MS
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var timedOut = false
        while (out.size() <= LIMIT && !timedOut) {
            val n = input.read(buffer, 0, minOf(buffer.size, LIMIT + 1 - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
            timedOut = System.nanoTime() > deadline
        }
        return if (timedOut) null else out.toByteArray()
    }

    private fun origin(page: String): String? = runCatching {
        val uri = URI(page)
        "${uri.scheme}://${uri.authority}/"
    }.getOrNull()

    companion object {
        const val LIMIT = 5 * 1024 * 1024
        const val TOTAL_MS = 5_000L
        private const val TIMEOUT_MS = 5_000
        private const val NANOS_PER_MS = 1_000_000L
        private const val BUFFER = 16 * 1024
        private val HTTP_OK_RANGE = 200..299
    }
}
