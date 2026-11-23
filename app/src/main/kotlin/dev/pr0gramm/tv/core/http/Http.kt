package dev.pr0gramm.tv.core.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** The network edge of the app: catalog sync and self-update. Tests use a fake. */
interface Http {
    /**
     * GETs [url] and hands the body of a 200 answer to [read]. Throws [HttpFailure] for any other
     * status and [IOException] when the server cannot be reached. Redirects are not followed; [follow] does that.
     */
    suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T
}

/** A non-200 answer. [location] is its `Location` header as sent, possibly relative, or null without one. */
class HttpFailure(val code: Int, val location: String? = null) : IOException("HTTP $code")

/**
 * A redirect that [follow] does not take: its [location] is no URI, the caller does not allow it, or it comes after
 * five redirects.
 */
class RedirectRefused(val location: String, reason: String, cause: Throwable? = null) :
    IOException("$reason: $location", cause)

/** [Http] over [HttpURLConnection], on the IO dispatcher; cancelling interrupts the blocking call. */
class UrlConnectionHttp : Http {
    override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T =
        runInterruptible(Dispatchers.IO) {
            val connection = url.toURL().openConnection() as? HttpURLConnection
                ?: throw IOException("not HTTP: $url")
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.useCaches = false
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw HttpFailure(code, connection.getHeaderField("Location"))
                connection.inputStream.use(read)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
    }
}
