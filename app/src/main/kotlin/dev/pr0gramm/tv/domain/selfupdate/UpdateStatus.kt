package dev.pr0gramm.tv.domain.selfupdate

import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure

/** What the self-update is doing or why it stopped. */
sealed interface UpdateStatus {
    /** Not checked yet. */
    data object Idle : UpdateStatus

    data object UpToDate : UpdateStatus

    data class Downloading(val percent: Int) : UpdateStatus

    /** The checked APK waits in the install queue until the start page is in front. */
    data object Ready : UpdateStatus

    /** A newer version exists, but this build has no release key, so only `mise run deploy` can update it. */
    data object DeployOnly : UpdateStatus

    /** A newer version exists, but the app may not install packages. */
    data object NotAllowed : UpdateStatus

    data class Failed(val failure: Failure) : UpdateStatus
}

/**
 * What to do with the release at [versionCode] on [device]. [UpdateStatus.Downloading] (0 %) means: go ahead and
 * download. [skipped] is a version that already ended in a status the user cannot change by
 * retrying (versionCode to status); that version keeps its status until a newer one appears.
 */
internal fun decide(
    versionCode: Long,
    device: PackageInstallerPort,
    skipped: Pair<Long, UpdateStatus>?,
): UpdateStatus = when {
    versionCode <= device.versionCode -> UpdateStatus.UpToDate
    skipped != null && skipped.first == versionCode -> skipped.second
    !device.updatable -> UpdateStatus.DeployOnly
    !device.mayInstall -> UpdateStatus.NotAllowed
    else -> UpdateStatus.Downloading(0)
}
