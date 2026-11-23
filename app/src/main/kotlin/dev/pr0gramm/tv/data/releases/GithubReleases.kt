package dev.pr0gramm.tv.data.releases

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.VerifiedDownload
import dev.pr0gramm.tv.core.http.follow
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.core.updatefailure.UpdateFailure
import java.io.File
import java.io.IOException
import java.net.URI

/**
 * The releases of the GitHub repo [repo] (`owner/repo`, the build constant `BuildConfig.UPDATE_REPO`), read without a
 * token (AC6): the latest release and the download of its asset `pr0gramm-tv.apk`. The download follows https
 * redirects only to `github.com` and `*.githubusercontent.com`.
 */
class GithubReleases(repo: String, private val http: Http) {
    private val latestUrl = URI("https://api.github.com/repos/$repo/releases/latest")

    /**
     * The latest release, or null when its tag is no version ([ReleaseJson.parse]). Any HTTP or network error, also
     * the rate limit (403), is [Failure.UNREACHABLE]; an answer without the asset or its digest is
     * [Failure.BAD_INDEX].
     */
    internal suspend fun latest(): Release? {
        val text = try {
            http.get(latestUrl, API_HEADERS) { it.readBytes().decodeToString() }
        } catch (e: IOException) {
            throw UpdateFailure(Failure.UNREACHABLE, e.toString(), e)
        }
        return ReleaseJson.parse(text)
    }

    /**
     * Writes the APK of [release] to [into], checked against its size and SHA-256 ([VerifiedDownload.save]). A
     * mismatch is [Failure.BROKEN_APK], a redirect to another host or to http is [Failure.BAD_INDEX], any other HTTP or
     * network error [Failure.UNREACHABLE]. On any failure [into] is deleted. [progress] gets each new percent on the
     * thread that reads the body.
     */
    internal suspend fun download(release: Release, into: File, progress: (Int) -> Unit = {}) {
        VerifiedDownload(release.size, release.sha256).save(into, progress) { read ->
            http.follow(release.apk, DOWNLOAD_HEADERS, { it.isHttpsOn(::isGithubHost) }, read)
        }
    }

    private companion object {
        val API_HEADERS = mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28")
        val DOWNLOAD_HEADERS = mapOf("Accept" to "application/octet-stream")

        /** Where GitHub serves a release asset: `github.com` and its storage hosts. */
        fun isGithubHost(host: String): Boolean = host == "github.com" || host.endsWith(".githubusercontent.com")
    }
}
