package dev.pr0gramm.tv.core.http

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

class UrlConnectionHttpTest {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply {
        createContext("/moved") { exchange ->
            exchange.responseHeaders.add("Location", "/target")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        start()
    }

    private fun url(path: String) = URI("http://127.0.0.1:${server.address.port}$path")

    @After
    fun stop() = server.stop(0)

    @Test
    fun `a redirect is an HttpFailure with its Location and is not followed`() = runTest {
        try {
            UrlConnectionHttp().get(url("/moved"), emptyMap()) { it.readBytes() }
            fail("expected HttpFailure")
        } catch (e: HttpFailure) {
            assertEquals(302, e.code)
            assertEquals("/target", e.location)
        }
    }
}
