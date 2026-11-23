/**
 * The blocklist: list syntax, the asset format and the policy for adopting a downloaded list (AC6).
 * [DOMAIN] is the domain rule of the build; the manifest parser keeps its own copy of the rule.
 *
 * The asset format is a contract with the app's `Blocklist`: lowercase ASCII, one domain per line,
 * each ended by `\n`, no `\r`, no comments. A break here builds fine but blocks nothing.
 */
object BlocklistFormat {
    /** A host name without scheme, path, port or spaces; lowercase. */
    val DOMAIN = Regex("[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)+")

    const val MIN_DOMAINS = 1000

    /** The first line of `blocklist.txt` names the address the copy came from. */
    const val SOURCE_PREFIX = "# source: "

    /** Trim, lowercase, drop `#` comments, blank and non-domain lines, dedupe (first occurrence wins). */
    fun domains(text: String): Set<String> =
        text.lineSequence().map { it.trim().lowercase() }
            .filter { !it.startsWith("#") && DOMAIN.matches(it) }
            .toCollection(LinkedHashSet())

    /** The app asset `blocklist.txt` for [domains]. */
    fun asset(domains: Set<String>): String = domains.joinToString("\n", postfix = "\n")

    /** The copy file text: the source line followed by the list as downloaded. */
    fun copy(candidate: String, url: String): String = "$SOURCE_PREFIX$url\n$candidate"

    /**
     * New list ([candidate], at least [MIN_DOMAINS] domains), else the old [copy] if it came from the same [url]
     * (with a warning), else an error.
     */
    fun choose(candidate: String?, copy: String?, url: String): Choice {
        val reason = when {
            candidate == null -> "Download fehlgeschlagen"
            else -> {
                val count = domains(candidate).size
                if (count >= MIN_DOMAINS) return Choice.UseCandidate(count)
                "nur $count Domains, mindestens $MIN_DOMAINS nötig"
            }
        }
        val fromSameUrl = copy?.lineSequence()?.first()?.trim() == "$SOURCE_PREFIX$url"
        return if (fromSameUrl) {
            Choice.UseCopy("Blockliste von $url nicht geladen ($reason), nehme die alte Kopie blocklist.txt.")
        } else {
            Choice.Fail("Blockliste von $url nicht geladen ($reason), und es gibt keine Kopie blocklist.txt von dieser Adresse.")
        }
    }

    sealed interface Choice {
        data class UseCandidate(val count: Int) : Choice
        data class UseCopy(val warning: String) : Choice
        data class Fail(val message: String) : Choice
    }
}
