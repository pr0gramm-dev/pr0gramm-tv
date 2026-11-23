/**
 * The source rules detekt cannot express (AC13): no `.kt`, `.kts` or `.js` file over [MAX_LINES] lines,
 * and no `@Suppress` or `@SuppressLint` in Kotlin without a `// reason: …` comment on the same line or the line above.
 * In the app's main code ([APP_CODE]) no string literal holds German text (localisation AC9): the app shows text
 * from the string resources only.
 */
object SourceRules {
    const val MAX_LINES = 300

    val EXTENSIONS = setOf("kt", "kts", "js")
    private val KOTLIN = setOf("kt", "kts")

    // `@Suppress`, `@file:Suppress`, `@get:Suppress`, `@SuppressWarnings`, `@SuppressLint`; not inside a string or an
    // identifier.
    private val SUPPRESS = Regex("""(^|[\s(,])@(\w+:)?Suppress(Warnings|Lint)?\b""")
    private val REASON = Regex("""//\s*reason:\s*\S""")
    private val REASON_LINE = Regex("""^\s*//\s*reason:\s*\S""")

    /** The German-literal rule checks the `.kt` files below this directory. */
    const val APP_CODE = "app/src/main/kotlin/"

    /** [GERMAN_ALLOWED] names files relative to this directory. */
    const val APP_PACKAGE = "app/src/main/kotlin/dev/pr0gramm/tv/"

    /**
     * German literals the app may keep, as `path:literal` with the path relative to [APP_PACKAGE] and the literal's
     * text as [KotlinCode.literals] gives it. An entry that matches no German literal is a violation.
     */
    val GERMAN_ALLOWED: Set<String> = emptySet()

    private val GERMAN_LETTERS = Regex("[äöüÄÖÜß„“]")
    private val ESCAPE = Regex("""\\(u[0-9a-fA-F]{4}|.)""")
    private val NOT_LETTER = Regex("""\P{L}+""")
    private val UNICODE = Regex("""\\u([0-9a-fA-F]{4})""")

    /** German words of the app's literals before the localisation; a literal with one of them is German. */
    private val GERMAN_WORDS = setOf(
        "nicht", "wird", "ist", "der", "die", "das", "und", "für", "kein", "keine", "keiner", "noch", "oder", "mit",
        "heute", "gestern", "suche", "seite", "liste", "meine", "datei", "fehlt", "fehler", "bild", "einstellungen",
        "installieren", "weiterleitung", "anlegbar", "ersetzbar", "als", "mehr",
        // Words that are not English, from the old literals without an umlaut (review 2026-11-22).
        "auf", "aus", "zu", "zur", "zum", "vom", "nach", "muss", "sein", "ein", "eine", "einen", "wert",
        "wechseln", "verlassen", "eingeben", "blockiert", "gespeichert", "erwartet", "fehlgeschlagen", "suchen",
        "verbinden", "aktualisieren", "wiederholen", "halten", "startseite", "werbung", "vollbild", "maus",
    )
    private const val GERMAN_HINT = "(String-Ressource nutzen oder in GERMAN_ALLOWED)"

    /**
     * One German message per problem; [path] (relative to the root project) is used for the file type and in the
     * messages. [allowed] is the allowlist of the German-literal rule.
     */
    fun violations(path: String, text: String, allowed: Set<String> = GERMAN_ALLOWED): List<String> {
        val extension = path.substringAfterLast('.', "")
        if (extension !in EXTENSIONS) return emptyList()
        val lines = text.removeSuffix("\n").takeIf { it.isNotEmpty() }?.lines().orEmpty()
        val problems = mutableListOf<String>()
        if (lines.size > MAX_LINES) problems += "$path: ${lines.size} Zeilen, erlaubt sind höchstens $MAX_LINES"
        if (extension in KOTLIN) {
            lines.forEachIndexed { i, line ->
                val explained = REASON.containsMatchIn(line) || (i > 0 && REASON_LINE.containsMatchIn(lines[i - 1]))
                if (SUPPRESS.containsMatchIn(line) && !explained) {
                    problems += "$path:${i + 1}: @Suppress ohne Begründung (\"// reason: …\" in derselben Zeile oder darüber)"
                }
            }
        }
        if (extension == "kt" && path.startsWith(APP_CODE)) {
            problems += germanLiterals(text).filter { allowKey(path, it.text) !in allowed }
                .map { "$path:${it.line}: deutscher Text \"${it.text}\" $GERMAN_HINT" }
        }
        return problems
    }

    /** One message per entry of [allowed] that matches no German literal of [files] (path relative to the root). */
    fun unusedAllowed(files: Map<String, String>, allowed: Set<String> = GERMAN_ALLOWED): List<String> {
        val used = files.filterKeys { it.startsWith(APP_CODE) && it.endsWith(".kt") }
            .flatMap { (path, text) -> germanLiterals(text).map { allowKey(path, it.text) } }.toSet()
        return allowed.sorted().filter { it !in used }
            .map { "GERMAN_ALLOWED: \"$it\" passt auf kein deutsches Literal" }
    }

    private fun germanLiterals(text: String) = KotlinCode.literals(text).filter { isGerman(it.text) }

    private fun allowKey(path: String, literal: String) = "${path.removePrefix(APP_PACKAGE)}:$literal"

    /** The literal with its unicode escapes decoded, so an escaped umlaut counts as the umlaut it is. */
    private fun isGerman(literal: String): Boolean {
        val text = literal.replace(UNICODE) { it.groupValues[1].toInt(16).toChar().toString() }
        return GERMAN_LETTERS.containsMatchIn(text) ||
            text.replace(ESCAPE, " ").split(NOT_LETTER).any { it.lowercase() in GERMAN_WORDS }
    }
}
