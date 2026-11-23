package dev.pr0gramm.tv.data.manifest

import java.time.LocalDate

/** The APK file with the [sha256] (64 lowercase hex digits) and [size] (> 0) it must have after the download. */
data class Apk(val file: Resource, val sha256: String, val size: Long)

/** An installable app. [pkg] is the Android package name, never [Manifest.OWN_PACKAGE]; [versionCode] > 0. */
data class CatalogApp(val pkg: String, val name: String, val image: Resource?, val versionCode: Long, val apk: Apk)

/** What the server offers. The launcher itself comes from GitHub releases, so [apps] never holds [OWN_PACKAGE]. */
data class Manifest(val version: Int, val date: LocalDate, val sites: List<Site>, val apps: List<CatalogApp>) {
    /** Banners and app images, without APKs: what the disk cache and the build copy. */
    val images: Set<Resource>
        get() = (sites.mapNotNull { it.banner } + apps.mapNotNull { it.image }).toSet()

    companion object {
        /** The launcher's own package: the app's `BuildConfig.APPLICATION_ID`, which build-logic cannot see. */
        const val OWN_PACKAGE = "dev.pr0gramm.tv"

        /**
         * The only check of a manifest, shared by app and build. Unknown fields (e.g. an `ambient` section) are
         * ignored; anything else that is wrong makes the whole manifest invalid, with every problem listed.
         */
        fun parse(text: String): ManifestResult = ManifestParser.parse(text)
    }
}

sealed interface ManifestResult {
    data class Valid(val manifest: Manifest) : ManifestResult

    /** Every problem, in the order of the file; never empty. */
    data class Invalid(val problems: List<ManifestProblem>) : ManifestResult {
        init {
            require(problems.isNotEmpty()) { "an invalid manifest names at least one problem" }
        }
    }
}
