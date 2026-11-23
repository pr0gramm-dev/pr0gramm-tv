/**
 * The GitHub repo of the self-update, `owner/repo` (AC6). app/build.gradle.kts takes it from `-PupdateRepo` and puts it
 * into `BuildConfig.UPDATE_REPO`, which the app puts into the path of an API URL. GitHub names: the owner is letters
 * and digits with single inner hyphens; the repo is letters, digits, '.', '_' and '-', but not "." or "..".
 */
object UpdateRepo {
    /** The repo of the upstream project, the default of `-PupdateRepo`. */
    const val DEFAULT = "pr0gramm-dev/pr0gramm-tv"

    private val FORM = Regex("""[A-Za-z0-9](-?[A-Za-z0-9])*/(?!\.\.?$)[A-Za-z0-9._-]+""")

    /** True when [text] is `owner/repo` and nothing else. */
    fun valid(text: String): Boolean = FORM.matches(text)
}
