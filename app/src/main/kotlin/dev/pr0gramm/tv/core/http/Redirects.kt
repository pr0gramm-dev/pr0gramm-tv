package dev.pr0gramm.tv.core.http

import java.io.InputStream
import java.net.URI

/**
 * GETs [url] like [Http.get] and follows redirects (301, 302, 303, 307, 308), each with the same [headers]. A relative
 * `Location` resolves against the URL that answered. Only locations that pass [allowed] are requested; [url] itself
 * is the caller's choice and is not checked. Throws [RedirectRefused] for a location it does not take, and
 * [HttpFailure] like [Http.get], also for a redirect without `Location`.
 */
suspend fun <T> Http.follow(
    url: URI,
    headers: Map<String, String>,
    allowed: (URI) -> Boolean,
    read: (InputStream) -> T,
): T {
    var current = url
    var redirects = 0
    while (true) {
        val failure = try {
            return get(current, headers, read)
        } catch (e: HttpFailure) {
            e
        }
        val location = failure.location?.takeIf { failure.code in REDIRECT_CODES } ?: throw failure
        current = target(current, location, allowed, redirects++)
    }
}

/** Where the redirect to [location] from [from] goes, after [redirects] earlier ones; or [RedirectRefused]. */
private fun target(from: URI, location: String, allowed: (URI) -> Boolean, redirects: Int): URI {
    val next = try {
        from.resolve(location)
    } catch (e: IllegalArgumentException) {
        throw RedirectRefused(location, "not a URI", e)
    }
    val reason = when {
        redirects >= MAX_REDIRECTS -> "more than $MAX_REDIRECTS redirects"
        !allowed(next) -> "redirect not allowed"
        else -> return next
    }
    throw RedirectRefused(location, reason)
}

/** How many redirects [follow] takes for one request. */
private const val MAX_REDIRECTS = 5

private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
