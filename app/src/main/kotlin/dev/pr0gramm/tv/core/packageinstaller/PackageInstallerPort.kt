package dev.pr0gramm.tv.core.packageinstaller

import dev.pr0gramm.tv.core.updatefailure.Failure
import java.io.File

/**
 * The Android edge of installs and the self-update: own version, installed versions, install. Tests use a fake. An
 * adapter interface of this segment (glossary), not a port between slices: the name stays as the spec mapped it.
 */
interface PackageInstallerPort {
    val packageName: String
    val versionCode: Long

    /** Signed with the release key, so an update from the store can replace it. */
    val updatable: Boolean

    /** The app may install packages (REQUEST_INSTALL_PACKAGES granted). */
    val mayInstall: Boolean

    /** The versionCode of [pkg] on this device, null when it is not installed. */
    fun installedVersion(pkg: String): Long?

    /** Installs [apk] as package [pkg]; null on success. */
    suspend fun install(apk: File, pkg: String): Failure?
}
