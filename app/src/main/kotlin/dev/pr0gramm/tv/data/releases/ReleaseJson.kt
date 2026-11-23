package dev.pr0gramm.tv.data.releases

import dev.pr0gramm.tv.core.appversion.AppVersion
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.core.updatefailure.UpdateFailure
import dev.pr0gramm.tv.data.manifest.Fields
import dev.pr0gramm.tv.data.manifest.Json
import dev.pr0gramm.tv.data.manifest.JsonException
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import java.net.URI
import java.net.URISyntaxException

/**
 * A release on GitHub that the self-update can install: its [version] from the tag and its asset `pr0gramm-tv.apk`
 * at [apk] (https on `github.com`) with [size] bytes and the SHA-256 [sha256] (lower-case hex).
 */
internal data class Release(val version: AppVersion, val apk: URI, val size: Long, val sha256: String)

/** The answer of `GET /repos/{owner}/{repo}/releases/latest`, read with the strict [Json] reader of the manifest. */
internal object ReleaseJson {
    /** The one asset of a release that the app installs. */
    const val ASSET = "pr0gramm-tv.apk"

    private const val SHA256_PREFIX = "sha256:"
    private val SHA256 = Regex("[0-9a-f]{64}")

    /**
     * The release in [text], or null when its `tag_name` is no version `MAJOR.MINOR.PATCH` with MINOR and PATCH up
     * to 99, with or without the leading `v` ([AppVersion.parse]): the app ignores that release. Throws
     * [UpdateFailure] with [Failure.BAD_INDEX] for anything else that is not a release with the asset [ASSET],
     * its `size`, its `digest` `sha256:<hex>` and an https URL on `github.com`.
     */
    fun parse(text: String): Release? {
        val root = try {
            Json.parse(text)
        } catch (e: JsonException) {
            bad(e.problem.english())
        }
        if (root !is Json.Obj) bad("not a JSON object")
        val problems = mutableListOf<ManifestProblem>()
        val tag = Fields("", root.fields, problems).text("tag_name", required = true) ?: bad(problems.first().english())
        val version = AppVersion.parse(tag) ?: return null
        val asset = (root.fields["assets"] as? Json.Arr)?.items.orEmpty()
            .filterIsInstance<Json.Obj>()
            .firstOrNull { (it.fields["name"] as? Json.Str)?.value == ASSET }
            ?: bad("release $tag has no asset $ASSET")
        val fields = Fields("assets.$ASSET", asset.fields, problems)
        val size = fields.positive("size")
        val digest = fields.text("digest", required = true)
        val url = fields.text("browser_download_url", required = true)
        if (size == null || digest == null || url == null) bad(problems.first().english())
        val sha256 = digest.lowercase().takeIf { it.startsWith(SHA256_PREFIX) }?.removePrefix(SHA256_PREFIX)
            ?.takeIf { SHA256.matches(it) }
            ?: bad("digest \"$digest\" is not sha256:<64 hex characters>")
        val apk = uri(url)?.takeIf { it.isHttpsOn { host -> host == "github.com" } }
            ?: bad("browser_download_url \"$url\" is not an https URL on github.com")
        return Release(version, apk, size, sha256)
    }

    private fun uri(text: String): URI? = try {
        URI(text)
    } catch (_: URISyntaxException) {
        null
    }

    private fun bad(detail: String): Nothing = throw UpdateFailure(Failure.BAD_INDEX, detail)
}

/** This URI is https, and [host] accepts its host (lower case). */
internal fun URI.isHttpsOn(host: (String) -> Boolean): Boolean =
    scheme.equals("https", ignoreCase = true) && this.host?.lowercase()?.let(host) == true
