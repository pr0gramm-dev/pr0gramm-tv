package dev.pr0gramm.tv.core.http

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.InputStream
import java.net.URI

class RedirectsTest {
    private val start = URI("https://github.com/o/r/releases/download/v1.2.3/pr0gramm-tv.apk")
    private val target = URI("https://objects.githubusercontent.com/blob/1")
    private val headers = mapOf("Accept" to "application/octet-stream")
    private val githubOnly: (URI) -> Boolean = { it.scheme == "https" && it.host.endsWith("githubusercontent.com") }

    /** Answers by URL: text for 200, an [HttpFailure] to throw, or 404. Records every request. */
    private class FakeHttp(private val responses: Map<URI, Any>) : Http {
        val requests = mutableListOf<Pair<URI, Map<String, String>>>()

        override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T {
            requests += url to headers
            return when (val response = responses[url] ?: HttpFailure(404)) {
                is String -> read(response.byteInputStream())
                is HttpFailure -> throw response
                else -> error("bad fake response $response")
            }
        }
    }

    private suspend fun FakeHttp.text(from: URI = start, allowed: (URI) -> Boolean = githubOnly): String =
        follow(from, headers, allowed) { it.readBytes().decodeToString() }

    private suspend inline fun <reified E : Throwable> thrown(block: suspend () -> Unit): E {
        try {
            block()
        } catch (e: Throwable) {
            if (e is E) return e
            throw e
        }
        fail("expected ${E::class.simpleName}")
        error("unreachable")
    }

    @Test
    fun `a 200 answer is read without a redirect`() = runTest {
        val http = FakeHttp(mapOf(start to "body"))
        assertEquals("body", http.text())
        assertEquals(listOf(start to headers), http.requests)
    }

    @Test
    fun `every redirect code goes to an allowed location with the same headers`() = runTest {
        for (code in listOf(301, 302, 303, 307, 308)) {
            val http = FakeHttp(mapOf(start to HttpFailure(code, target.toString()), target to "apk"))
            assertEquals("apk", http.text())
            assertEquals(listOf(start to headers, target to headers), http.requests)
        }
    }

    @Test
    fun `a relative location resolves against the url that answered`() = runTest {
        val next = URI("https://github.com/o/r/next")
        val http = FakeHttp(mapOf(start to HttpFailure(302, "../../../next"), next to "body"))
        assertEquals("body", http.text { it.host == "github.com" })
    }

    @Test
    fun `a location the caller does not allow is refused and never asked`() = runTest {
        val foreign = "https://evil.example/app.apk"
        val http = FakeHttp(mapOf(start to HttpFailure(302, foreign)))
        val refused = thrown<RedirectRefused> { http.text() }
        assertEquals(foreign, refused.location)
        assertEquals(listOf(start), http.requests.map { it.first })
    }

    @Test
    fun `an http location is refused when only https is allowed`() = runTest {
        val http = FakeHttp(mapOf(start to HttpFailure(302, "http://objects.githubusercontent.com/blob/1")))
        thrown<RedirectRefused> { http.text() }
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `a location that is no URI is refused`() = runTest {
        val http = FakeHttp(mapOf(start to HttpFailure(302, "https://bad host/ x")))
        thrown<RedirectRefused> { http.text { true } }
    }

    @Test
    fun `five redirects are followed, the sixth is refused`() = runTest {
        fun hop(n: Int) = URI("https://objects.githubusercontent.com/hop/$n")
        fun from(n: Int) = if (n == 0) start else hop(n)
        fun chain(hops: Int): Map<URI, Any> =
            (0 until hops).associate { n -> from(n) to HttpFailure(302, hop(n + 1).toString()) } + (hop(hops) to "end")

        assertEquals("end", FakeHttp(chain(5)).text())

        val tooLong = FakeHttp(chain(6))
        thrown<RedirectRefused> { tooLong.text() }
        assertEquals(6, tooLong.requests.size)
    }

    @Test
    fun `other failures and a redirect without location stay HttpFailure`() = runTest {
        val notFound = thrown<HttpFailure> { FakeHttp(emptyMap()).text() }
        assertEquals(404, notFound.code)

        val bare = thrown<HttpFailure> { FakeHttp(mapOf(start to HttpFailure(302))).text() }
        assertEquals(302, bare.code)
        assertNull(bare.location)
    }
}
