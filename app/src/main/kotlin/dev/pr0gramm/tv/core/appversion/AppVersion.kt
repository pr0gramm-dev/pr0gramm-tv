package dev.pr0gramm.tv.core.appversion

/**
 * A version `MAJOR.MINOR.PATCH` of the app (AC10). The Gradle build sets `versionCode` and `versionName` from it, and
 * the self-update reads the release tag `vMAJOR.MINOR.PATCH` with it. Pure Kotlin, also compiled into `build-logic`.
 */
class AppVersion private constructor(val major: Long, val minor: Long, val patch: Long) {
    /** The Android `versionCode`: MAJOR·10 000 + MINOR·100 + PATCH. One code names one version. */
    val code: Long get() = major * MAJOR_STEP + minor * MINOR_STEP + patch

    /** The `versionName` `MAJOR.MINOR.PATCH`, without `v`. */
    override fun toString() = "$major.$minor.$patch"

    override fun equals(other: Any?) = other is AppVersion && other.code == code

    override fun hashCode() = code.hashCode()

    companion object {
        private const val MAJOR_STEP = 10_000L
        private const val MINOR_STEP = 100L

        /** The highest `versionCode` Android accepts. */
        private const val MAX_CODE = 2_100_000_000L

        /** SemVer numbers without leading zeros; a tag may start with `v`. */
        private val FORM = Regex("""v?(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)""")

        /**
         * The version in [text] (`1.2.3` or the tag `v1.2.3`), or null: another form, MINOR or PATCH over 99, or a
         * code outside 1 to 2 100 000 000.
         */
        fun parse(text: String): AppVersion? {
            val (major, minor, patch) = FORM.matchEntire(text)?.destructured ?: return null
            return AppVersion(major.toLong(), minor.toLong(), patch.toLong()).takeIf { it.code in 1..MAX_CODE }
        }
    }
}
