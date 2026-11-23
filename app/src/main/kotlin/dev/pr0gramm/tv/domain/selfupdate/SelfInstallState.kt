package dev.pr0gramm.tv.domain.selfupdate

import dev.pr0gramm.tv.core.updatefailure.Failure

/**
 * What the self-update knows about its own install jobs, from E2 (sent) and E3 (ended); it never reads the install
 * queue. [handedOff] is the version sent with E2 whose install has not ended. [failedVersion] is the versionCode whose
 * install failed, until an install of the own app succeeds or is cancelled.
 */
data class SelfInstallState(val handedOff: Long? = null, val failedVersion: Long? = null) {
    /** [versionCode] went out with E2; it replaces an older one that waited. */
    fun handedOff(versionCode: Long): SelfInstallState = copy(handedOff = versionCode)

    /** E3: the install of [versionCode] ended. A newer hand-off stays. */
    fun ended(versionCode: Long, failed: Boolean): SelfInstallState = copy(
        handedOff = handedOff?.takeUnless { it == versionCode },
        failedVersion = if (failed) versionCode else null,
    )

    /** [versionCode] waits for its install or runs: it is not downloaded again. */
    fun queued(versionCode: Long): Boolean = handedOff == versionCode

    /** The version that keeps its status until a newer one appears (see [decide]); null when none failed. */
    fun skipped(): Pair<Long, UpdateStatus>? = failedVersion?.let { it to UpdateStatus.Failed(Failure.INSTALL_FAILED) }
}
