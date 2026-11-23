package dev.pr0gramm.tv.data.manifest

import java.net.URI
import java.net.URISyntaxException

/** A file the manifest names (`banner`, `image`, `apk`): a [ServerPath] next to the manifest or an [AbsoluteUrl]. */
sealed interface Resource {
    /** Where to request the file: a [ServerPath] against [manifestUrl] (the saved address), an [AbsoluteUrl] as is. */
    fun resolve(manifestUrl: URI): URI

    companion object {
        /** A relative path or an http(s) URL with a host, no user info or fragment, a query allowed; else null. */
        fun of(raw: String): Resource? = ServerPath.of(raw) ?: AbsoluteUrl.of(raw)

        /** The one scheme rule for links and their redirects: http or https in any case, with a non-empty host. */
        fun isWeb(uri: URI): Boolean =
            uri.scheme?.lowercase() in WEB_SCHEMES && !uri.host.isNullOrEmpty()

        private val WEB_SCHEMES = setOf("http", "https")
    }
}

/**
 * A file next to `manifest.json`, e.g. `img/kodi.png`: a relative path of segments `[A-Za-z0-9._-]+`, without
 * `.`/`..`, scheme, host, query or a leading `/`. So it resolves against the manifest URL and maps 1:1 to a file in a
 * directory.
 */
@JvmInline
value class ServerPath private constructor(val path: String) : Resource {
    override fun resolve(manifestUrl: URI): URI = manifestUrl.resolve(path)

    companion object {
        private val SEGMENT = Regex("[A-Za-z0-9._-]+")

        /** null when [raw] is not such a path. */
        fun of(raw: String): ServerPath? =
            if (raw.split('/').all { it != "." && it != ".." && SEGMENT.matches(it) }) ServerPath(raw) else null
    }
}

/** An http(s) URL from the manifest. [text] is exactly the manifest's text and the URL's identity. */
@JvmInline
value class AbsoluteUrl private constructor(val text: String) : Resource {
    override fun resolve(manifestUrl: URI): URI = URI(text)

    companion object {
        /** null when [raw] is not an http(s) URL with a host, or has user info or a fragment. */
        internal fun of(raw: String): AbsoluteUrl? {
            val uri = try {
                URI(raw)
            } catch (_: URISyntaxException) {
                return null
            }
            val valid = Resource.isWeb(uri) && uri.rawUserInfo == null && uri.rawFragment == null
            return if (valid) AbsoluteUrl(raw) else null
        }
    }
}
