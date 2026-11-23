package dev.pr0gramm.tv.data.catalogsync

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.HttpFailure
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Answers by URL: bytes or a stream for 200, an HTTP code, or an IOException (an [HttpFailure] with a location is a
 * redirect). Records the URL and headers of every request.
 */
internal class FakeHttp(private val responses: Map<URI, Any>) : Http {
    val requests = mutableListOf<Pair<URI, Map<String, String>>>()

    override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T {
        requests += url to headers
        return when (val response = responses[url] ?: 404) {
            is ByteArray -> read(response.inputStream())
            is InputStream -> read(response)
            is Int -> throw HttpFailure(response)
            is IOException -> throw response
            else -> error("bad fake response $response")
        }
    }
}

/** An endless body of spaces that counts the bytes the client took. */
internal class EndlessBody : InputStream() {
    var taken = 0L

    override fun read(): Int = ' '.code.also { taken++ }
}
