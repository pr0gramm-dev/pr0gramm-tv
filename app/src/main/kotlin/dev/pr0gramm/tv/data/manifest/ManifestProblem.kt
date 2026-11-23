package dev.pr0gramm.tv.data.manifest

/**
 * One problem of a manifest: the place [where] (`sites[0] (nova).zoom`, `version`, or empty for the whole file), the
 * [kind] and its [args] (as many as [Kind.arity]). Pure Kotlin: the app translates it (`ui.launcher.ProblemText`),
 * `check-manifest` and the logs use [english].
 */
data class ManifestProblem(val where: String, val kind: Kind, val args: List<String> = emptyList()) {
    init {
        require(args.size == kind.arity) { "$kind takes ${kind.arity} args, got $args" }
    }

    /**
     * What is wrong. The constructor names the args in their order; [arity] is their number. Each `JSON_*` kind ends
     * with the position `at` in the text.
     */
    enum class Kind(vararg names: String) {
        JSON_CHAR_EXPECTED("char", "at"),
        JSON_VALUE_EXPECTED("at"),
        JSON_TEXT_AFTER_END("at"),
        JSON_TOO_DEEP("maxDepth", "at"),
        JSON_KEY_EXPECTED("at"),
        JSON_DUPLICATE_KEY("key", "at"),
        JSON_UNCLOSED_STRING("at"),
        JSON_CONTROL_CHAR("at"),
        JSON_ESCAPE_END("at"),
        JSON_UNKNOWN_ESCAPE("char", "at"),
        JSON_BAD_UNICODE("at"),
        ROOT_NOT_OBJECT,
        TOO_LARGE,
        MISSING,
        NOT_STRING,
        BLANK,
        NOT_BOOLEAN,
        NOT_WHOLE,
        TOO_BIG("number"),
        NOT_POSITIVE("number"),
        NOT_STRING_LIST,
        BAD_RESOURCE("value"),
        VERSION_RANGE("number", "max"),
        BAD_DATE("value"),
        NOT_LIST,
        NOT_OBJECT,
        BAD_ID("id"),
        DUPLICATE("value"),
        BAD_URL("value"),
        BAD_COLOR("value"),
        ZOOM_RANGE("value", "min", "max"),
        BAD_DOMAIN("value"),
        BAD_PACKAGE("value"),
        OWN_PACKAGE("package"),
        BAD_SHA256,
        ;

        /** The number of args a problem of this kind takes. */
        val arity: Int = names.size
    }

    /** English line for `check-manifest` and logs: `sites[0] (nova).zoom: 200 not in 50–150`. */
    fun english(): String = if (where.isEmpty()) reason() else "$where: ${reason()}"

    /** The English text of [kind] with [args]; the same text as the English resource of the kind. */
    @Suppress("CyclomaticComplexMethod") // reason: one branch per Kind; the compiler keeps the when exhaustive
    private fun reason(): String {
        val a = args
        return when (kind) {
            Kind.JSON_CHAR_EXPECTED -> "not valid JSON: '${a[0]}' expected (character ${a[1]})"
            Kind.JSON_VALUE_EXPECTED -> "not valid JSON: value expected (character ${a[0]})"
            Kind.JSON_TEXT_AFTER_END -> "not valid JSON: text after the end (character ${a[0]})"
            Kind.JSON_TOO_DEEP -> "not valid JSON: nested deeper than ${a[0]} levels (character ${a[1]})"
            Kind.JSON_KEY_EXPECTED -> "not valid JSON: field name in quotes expected (character ${a[0]})"
            Kind.JSON_DUPLICATE_KEY -> "not valid JSON: field name \"${a[0]}\" is not unique (character ${a[1]})"
            Kind.JSON_UNCLOSED_STRING -> "not valid JSON: string without a closing quote (character ${a[0]})"
            Kind.JSON_CONTROL_CHAR -> "not valid JSON: control character in a string (character ${a[0]})"
            Kind.JSON_ESCAPE_END -> "not valid JSON: escape without a character (character ${a[0]})"
            Kind.JSON_UNKNOWN_ESCAPE -> "not valid JSON: unknown escape \\${a[0]} (character ${a[1]})"
            Kind.JSON_BAD_UNICODE -> "not valid JSON: \\u needs four hex digits (character ${a[0]})"
            Kind.ROOT_NOT_OBJECT -> "the top level must be an object"
            Kind.TOO_LARGE -> "manifest.json is larger than 2 MiB"
            Kind.MISSING -> "missing"
            Kind.NOT_STRING -> "must be a string"
            Kind.BLANK -> "must not be empty"
            Kind.NOT_BOOLEAN -> "must be true or false (without quotes)"
            Kind.NOT_WHOLE -> "must be a whole number"
            Kind.TOO_BIG -> "${a[0]} is too large"
            Kind.NOT_POSITIVE -> "${a[0]} must be more than 0"
            Kind.NOT_STRING_LIST -> "must be a list of strings"
            Kind.BAD_RESOURCE -> "\"${a[0]}\" must be a relative path such as img/a.png, or an http(s) URL with a " +
                "host, without a user and without a fragment"
            Kind.VERSION_RANGE -> "${a[0]} must be between 1 and ${a[1]}"
            Kind.BAD_DATE -> "\"${a[0]}\" is not a date such as 2026-11-18"
            Kind.NOT_LIST -> "must be a list"
            Kind.NOT_OBJECT -> "must be an object"
            Kind.BAD_ID -> "\"${a[0]}\" must match [a-z][a-z0-9]*"
            Kind.DUPLICATE -> "\"${a[0]}\" is not unique"
            Kind.BAD_URL -> "\"${a[0]}\" is not a valid http(s) URL"
            Kind.BAD_COLOR -> "\"${a[0]}\" must be #RRGGBB"
            Kind.ZOOM_RANGE -> "${a[0]} not in ${a[1]}–${a[2]}"
            Kind.BAD_DOMAIN -> "\"${a[0]}\" is not a domain (no scheme, path or spaces)"
            Kind.BAD_PACKAGE -> "\"${a[0]}\" is not a package name such as org.a.b"
            Kind.OWN_PACKAGE -> "\"${a[0]}\" is the package of this app"
            Kind.BAD_SHA256 -> "must be 64 hex characters in lower case"
        }
    }
}
