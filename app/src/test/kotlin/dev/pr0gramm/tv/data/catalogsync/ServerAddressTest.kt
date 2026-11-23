package dev.pr0gramm.tv.data.catalogsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.net.URI
import java.security.MessageDigest

class ServerAddressTest {
    private fun https(text: String) = ServerAddress.parse(text)

    @Test fun `a bare host gets https and manifest json`() {
        val address = https("tv.example.org")!!
        assertEquals(URI("https://tv.example.org/manifest.json"), address.manifestUrl)
        assertEquals("tv.example.org", address.shown)
    }

    @Test fun `a path gets manifest json appended, a path that ends in manifest json keeps it`() {
        assertEquals(URI("https://example.org/tv/manifest.json"), https("example.org/tv")!!.manifestUrl)
        assertEquals(URI("https://example.org/tv/manifest.json"), https("example.org/tv/")!!.manifestUrl)
        assertEquals(URI("https://example.org/tv/manifest.json"), https("example.org/tv/manifest.json")!!.manifestUrl)
        assertEquals(URI("https://example.org/manifest.json"), https("https://example.org/manifest.json")!!.manifestUrl)
        assertEquals("example.org/tv", https("example.org/tv")!!.shown)
    }

    @Test fun `a port other than the default stays`() {
        val address = https("tv.example.org:8443")!!
        assertEquals(URI("https://tv.example.org:8443/manifest.json"), address.manifestUrl)
        assertEquals("tv.example.org:8443", address.shown)
    }

    @Test fun `http and https to any host, no other scheme`() {
        val named = ServerAddress.parse("http://tv.example.org")!!
        assertEquals(URI("http://tv.example.org/manifest.json"), named.manifestUrl)
        assertEquals("http://tv.example.org", named.shown)
        val local = ServerAddress.parse("http://192.168.1.5:8080/tv")!!
        assertEquals(URI("http://192.168.1.5:8080/tv/manifest.json"), local.manifestUrl)
        assertEquals("http://192.168.1.5:8080/tv", local.shown)
        assertNull(ServerAddress.parse("ftp://tv.example.org"))
        assertNull(ServerAddress.parse("file:///sdcard/manifest.json"))
    }

    @Test fun `text that is no plain server address is refused`() {
        listOf(
            "",
            "   ",
            "tv example.org",
            "https://",
            "https:///manifest.json",
            "https://user:secret@tv.example.org",
            "tv.example.org/?token=1",
            "tv.example.org/#top",
        ).forEach { assertNull(it, https(it)) }
    }

    @Test fun `the canonical form ignores case of scheme and host, the default port, blanks and trailing slashes`() {
        val address = https("  HTTPS://TV.Example.ORG:443/ ")!!
        assertEquals(URI("https://tv.example.org/manifest.json"), address.manifestUrl)
        assertEquals(https("tv.example.org"), address)
        assertEquals(https("tv.example.org")!!.key, address.key)
    }

    @Test fun `the canonical form is idempotent`() {
        listOf("tv.example.org", "example.org/tv", "tv.example.org:8443/a/b/manifest.json", "http://10.0.0.2/tv")
            .map { ServerAddress.parse(it)!! }
            .forEach { address ->
                assertEquals(address, ServerAddress.parse(address.shown))
                assertEquals(address, ServerAddress.parse(address.manifestUrl.toString()))
            }
    }

    @Test fun `the key is the first 16 hex digits of the SHA-256 of the canonical manifest url`() {
        val sha = MessageDigest.getInstance("SHA-256").digest("https://tv.example.org/manifest.json".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val key = https("tv.example.org")!!.key
        assertEquals(sha.take(16), key)
        assertEquals(16, key.length)
        assertNotEquals(key, https("example.org/tv")!!.key)
    }

    @Test fun `the data of a server lives under servers slash key`() {
        val address = https("tv.example.org")!!
        assertEquals(File("/data/files/servers/${address.key}"), address.dir(File("/data/files")))
    }

    @Test fun `the data folder is the address's folder, or servers slash none without an address`() {
        val address = https("tv.example.org")!!
        assertEquals(address.dir(File("/data/files")), serverDir(File("/data/files"), address))
        assertEquals(File("/data/files/servers/none"), serverDir(File("/data/files"), null))
    }
}
