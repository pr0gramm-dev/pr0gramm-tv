package dev.pr0gramm.tv.core.appversion

/**
 * A version of the app (AC10): a stable `MAJOR.MINOR.PATCH`, or a nightly `MAJOR.MINOR.PATCH-nightly.N` that builds on
 * the stable MAJOR.MINOR.PATCH. The Gradle build sets `versionCode` and `versionName` from it, and the self-update
 * reads the release tag `vMAJOR.MINOR.PATCH` with it. Pure Kotlin, also compiled into `build-logic`.
 * The rule is in docs/release.md, section "Version code".
 */
class AppVersion private constructor(val major: Long, val minor: Long, val patch: Long, val nightly: Int) {
    /**
     * The Android `versionCode`: (MAJOR·10 000 + MINOR·100 + PATCH)·1000 + N, with N = 0 for a stable version. A
     * nightly is thus above its stable version and below the next stable version. One code names one version.
     */
    val code: Long get() = (major * MAJOR_STEP + minor * MINOR_STEP + patch) * NIGHTLY_SLOTS + nightly

    /** The `versionName` `MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-nightly.N`, without `v`. */
    override fun toString() = "$major.$minor.$patch" + if (nightly > 0) "$NIGHTLY$nightly" else ""

    override fun equals(other: Any?) = other is AppVersion && other.code == code

    override fun hashCode() = code.hashCode()

    companion object {
        private const val MAJOR_STEP = 10_000L
        private const val MINOR_STEP = 100L

        /** The codes 0 to 999 after each stable version: 0 is the stable version, 1 to 999 its nightlies. */
        private const val NIGHTLY_SLOTS = 1_000L
        private const val NIGHTLY = "-nightly."

        /** The highest `versionCode` Android accepts. */
        private const val MAX_CODE = 2_100_000_000L

        /** SemVer numbers without leading zeros, an optional nightly number 1 to 999; a tag may start with `v`. */
        private val FORM = Regex(
            """v?(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)""" + """(?:-nightly\.([1-9][0-9]{0,2}))?""",
        )

        /**
         * The version in [text] (`1.2.3`, the tag `v1.2.3`, or `1.2.3-nightly.4`), or null: another form, MINOR or
         * PATCH over 99, a nightly number outside 1 to 999, or a code outside 1 to 2 100 000 000.
         */
        fun parse(text: String): AppVersion? {
            val match = FORM.matchEntire(text) ?: return null
            val (major, minor, patch) = match.destructured
            // The last group is the nightly number; it is empty for a stable version.
            val nightly = match.groupValues.last().toIntOrNull() ?: 0
            val version = AppVersion(major.toLong(), minor.toLong(), patch.toLong(), nightly)
            return version.takeIf { it.code in 1..MAX_CODE }
        }
    }
}
