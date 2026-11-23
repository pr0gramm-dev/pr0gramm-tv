package dev.pr0gramm.tv.data.catalogsync

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.util.Locale

/**
 * The canonical URL of a server's `manifest.json`: lower-case scheme and host, no default port, no user, query or
 * fragment, the path ending in `/manifest.json`. Two addresses are equal when their canonical URLs are equal.
 */
@JvmInline
value class ServerAddress private constructor(private val url: String) {
    /** Where `manifest.json` lives. */
    val manifestUrl: URI get() = URI.create(url)

    /** The address as a user types it: without `https://` and without `/manifest.json`. [parse] gives it back. */
    val shown: String get() = url.removePrefix("$HTTPS://").removeSuffix(MANIFEST)

    /** The first 16 hex digits of the SHA-256 of the canonical URL: the name of the server's data folder. */
    val key: String
        get() = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .take(KEY_BYTES).joinToString("") { "%02x".format(it) }

    /** The folder of this server's data: `<filesDir>/servers/<key>`. */
    fun dir(filesDir: File): File = File(File(filesDir, SERVERS), key)

    companion object {
        internal const val SERVERS = "servers"
        private const val HTTPS = "https"
        private const val HTTP = "http"
        private const val MANIFEST = "/manifest.json"
        private const val KEY_BYTES = 8
        private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        private val DEFAULT_PORTS = mapOf(HTTPS to 443, HTTP to 80)

        /**
         * The address the user typed as [text], or null when it is no plain http or https address with a host.
         * Text without a scheme gets `https://`; a path without `/manifest.json` at its end gets it.
         */
        fun parse(text: String): ServerAddress? {
            val typed = text.trim()
            val uri = uri(if (SCHEME.containsMatchIn(typed)) typed else "$HTTPS://$typed")
            return uri?.let(::canonical)?.let(::ServerAddress)
        }

        private fun canonical(uri: URI): String? {
            val scheme = uri.scheme.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT)
            if (scheme !in DEFAULT_PORTS || host == null || !plain(uri)) return null
            val port = if (uri.port == -1 || uri.port == DEFAULT_PORTS[scheme]) "" else ":${uri.port}"
            val path = uri.normalize().rawPath.orEmpty().trimEnd('/')
            val manifest = if (path.endsWith(MANIFEST)) path else path + MANIFEST
            return "$scheme://$host$port$manifest"
        }

        private fun uri(text: String): URI? = try {
            URI(text)
        } catch (_: URISyntaxException) {
            null
        }

        private fun plain(uri: URI): Boolean =
            uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null
    }
}

/**
 * The folder of the per-server data: [ServerAddress.dir] of [address], or `<filesDir>/servers/none` without an
 * address. `none` is no key, because a key has only hex digits.
 */
fun serverDir(filesDir: File, address: ServerAddress?): File =
    address?.dir(filesDir) ?: File(File(filesDir, ServerAddress.SERVERS), "none")
