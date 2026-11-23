package dev.pr0gramm.tv.domain.selfupdate

import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.core.updatefailure.UpdateFailure
import dev.pr0gramm.tv.data.releases.GithubReleases
import dev.pr0gramm.tv.data.releases.Release
import java.io.File

/** The end of one check: its [status] and, when a new version was downloaded and checked, the E2 [handOff]. */
internal data class Checked(val status: UpdateStatus, val handOff: SelfUpdateReady? = null)

/**
 * One check of the self-update: the latest release from [releases], decision on it, download, hand-off. It needs no
 * catalog (AC7). A version whose install failed is not downloaded again until a newer one appears (see [decide]).
 * Not thread-safe: [run] is called one at a time.
 */
internal class SelfUpdateCheck(
    private val releases: GithubReleases,
    private val device: PackageInstallerPort,
    private val clock: () -> Long,
    private val workFile: () -> File = { File.createTempFile("update", ".apk") },
) {
    /** The last answer of GitHub, kept for an hour; null before the first one. */
    private var answer: Answer? = null

    /**
     * The end of one check. GitHub is asked at most hourly unless [force]; within the hour [decide] runs again on the
     * last answer, so a status such as [UpdateStatus.NotAllowed] changes at once. [installs] is what E2 and E3 told
     * about the own install jobs. [show] gets the statuses on the way (download progress).
     */
    suspend fun run(force: Boolean, installs: () -> SelfInstallState, show: (UpdateStatus) -> Unit): Checked = try {
        prepareOrRetry(force, installs, show)
    } catch (e: UpdateFailure) {
        Checked(UpdateStatus.Failed(e.failure))
    }

    private suspend fun prepareOrRetry(
        force: Boolean,
        installs: () -> SelfInstallState,
        show: (UpdateStatus) -> Unit,
    ): Checked = try {
        prepare(latest(force), installs(), show)
    } catch (e: UpdateFailure) {
        if (e.failure != Failure.BROKEN_APK) throw e
        // The release may have got a new APK while this one was downloading: look again, once.
        val again = try {
            latest(force = true)
        } catch (_: UpdateFailure) {
            throw e
        }
        prepare(again, installs(), show)
    }

    /**
     * The latest release, from GitHub or, within the hour and without [force], from the last answer. An
     * unreachable GitHub is not kept, so the next check asks again.
     */
    private suspend fun latest(force: Boolean): Release? {
        val last = answer
        if (!force && last != null && clock() - last.at < HOUR_MS) return last.release.getOrThrow()
        val release = try {
            Result.success(releases.latest())
        } catch (e: UpdateFailure) {
            if (e.failure == Failure.UNREACHABLE) throw e
            Result.failure(e)
        }
        answer = Answer(clock(), release)
        return release.getOrThrow()
    }

    /** Downloads and checks [release] if it is newer; the result goes out as the hand-off. */
    private suspend fun prepare(release: Release?, installs: SelfInstallState, show: (UpdateStatus) -> Unit): Checked {
        val verdict = verdict(release, installs)
        if (release == null || verdict !is UpdateStatus.Downloading) return Checked(verdict)
        show(verdict)
        val file = workFile()
        releases.download(release, file) { show(UpdateStatus.Downloading(it)) }
        return Checked(UpdateStatus.Ready, SelfUpdateReady(release.version.code, file))
    }

    /** No [release] (its tag is no version) is up to date; a queued one is ready; else [decide]. */
    private fun verdict(release: Release?, installs: SelfInstallState): UpdateStatus = when {
        release == null -> UpdateStatus.UpToDate
        installs.queued(release.version.code) -> UpdateStatus.Ready
        else -> decide(release.version.code, device, installs.skipped())
    }

    /** What GitHub answered [at] (epoch ms): a release, none, or a [Failure.BAD_INDEX]. */
    private class Answer(val at: Long, val release: Result<Release?>)

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
    }
}
