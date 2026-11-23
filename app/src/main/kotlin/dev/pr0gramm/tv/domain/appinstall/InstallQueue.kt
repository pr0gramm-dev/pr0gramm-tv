package dev.pr0gramm.tv.domain.appinstall

import dev.pr0gramm.tv.data.manifest.CatalogApp
import java.io.File

/** One install of package [pkg] at [versionCode]: an app from the manifest, or the launcher's own update. */
sealed interface InstallJob {
    val pkg: String
    val versionCode: Long

    /** An app from the manifest, downloaded when its turn comes. */
    data class App(val app: CatalogApp) : InstallJob {
        override val pkg: String get() = app.pkg
        override val versionCode: Long get() = app.versionCode
    }

    /** The own update [pkg] at [versionCode], already downloaded to [apk] with size and SHA-256 checked. */
    data class Self(override val pkg: String, override val versionCode: Long, val apk: File) : InstallJob
}

/** How an install ended. [CANCELLED] is the user saying no in Android: no error, the app is just not installed. */
enum class InstallOutcome { INSTALLED, FAILED, CANCELLED }

/**
 * The one install queue for apps and the own update (AC9, AC11) as a value: one install runs at a time, apps in the
 * order they were asked for. [failed] maps a package to the versionCode whose install failed, until it is asked for
 * again. [awaitingPermission] waits for the user to allow installs in the Android setting. [ready] is the checked own
 * update; it runs only with the start page in the foreground and no app waiting, because installing it ends the
 * process. [percent] is the download progress of [running]. Every change is a named step; the worker of
 * [AppInstaller] takes the next job with [next].
 */
data class InstallQueue(
    val running: InstallJob? = null,
    val waiting: List<InstallJob> = emptyList(),
    val failed: Map<String, Long> = emptyMap(),
    val awaitingPermission: InstallJob? = null,
    val ready: InstallJob.Self? = null,
    val percent: Int = 0,
) {
    /**
     * Queues [job] and clears an earlier failure of its package; nothing when the package already waits or runs.
     * Without the permission to install ([allowed] false) the job waits for it instead.
     */
    fun request(job: InstallJob, allowed: Boolean): InstallQueue = when {
        running?.pkg == job.pkg || waiting.any { it.pkg == job.pkg } -> this
        !allowed -> copy(awaitingPermission = job)
        else -> copy(
            waiting = waiting + job,
            failed = failed - job.pkg,
            awaitingPermission = awaitingPermission?.takeUnless { it.pkg == job.pkg },
        )
    }

    /** The user is back from the Android setting: with the permission the job goes on, without it is dropped. */
    fun permission(allowed: Boolean): InstallQueue {
        val job = awaitingPermission ?: return this
        val rest = copy(awaitingPermission = null)
        return if (allowed) rest.request(job, allowed = true) else rest
    }

    /** Starts the oldest waiting app, else the [ready] own update while the start page is in the [foreground]. */
    fun next(foreground: Boolean): InstallQueue = when {
        running != null -> this
        waiting.isNotEmpty() -> copy(running = waiting.first(), waiting = waiting.drop(1), percent = 0)
        foreground && ready != null -> copy(running = ready, ready = null, percent = 0)
        else -> this
    }

    /** The install of [pkg] ended; nothing when [pkg] does not run. */
    fun finished(pkg: String, outcome: InstallOutcome): InstallQueue {
        val job = running?.takeIf { it.pkg == pkg } ?: return this
        val failed = when (outcome) {
            InstallOutcome.INSTALLED, InstallOutcome.CANCELLED -> failed - pkg
            InstallOutcome.FAILED -> failed + (pkg to job.versionCode)
        }
        return copy(running = null, failed = failed, percent = 0)
    }

    /** The download of [pkg] reached [percent]; nothing when [pkg] does not run. */
    fun progress(pkg: String, percent: Int): InstallQueue = if (running?.pkg == pkg) copy(percent = percent) else this

    /** The checked own update waits for the start page; it replaces an older one that waited. */
    fun selfReady(job: InstallJob.Self): InstallQueue = copy(ready = job)
}
