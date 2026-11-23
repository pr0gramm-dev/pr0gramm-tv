package dev.pr0gramm.tv.data.manifest

import dev.pr0gramm.tv.data.manifest.ManifestProblem.Kind

/**
 * Reads the fields of one JSON object of the manifest and records each problem under [where] (`sites[0] (nova)`,
 * empty at the top). A field set to `null` counts as missing. A reader returns null (or the default) on a problem;
 * [failed] tells whether there was one.
 */
internal class Fields(
    private val where: String,
    private val fields: Map<String, Json>,
    val problems: MutableList<ManifestProblem>,
) {
    var failed = false
        private set

    /**
     * Records [kind] with [args] (as text) at [field] below [where]. A hostile file must not fill the memory, so
     * the list keeps at most [MAX_PROBLEMS] problems and each argument at most [MAX_ARG] characters. [failed] is set
     * for every problem, also for the ones that are not kept.
     */
    fun problem(field: String, kind: Kind, vararg args: Any) {
        failed = true
        if (problems.size >= MAX_PROBLEMS) return
        problems += ManifestProblem(
            if (where.isEmpty()) field else "$where.$field",
            kind,
            args.map { it.toString().take(MAX_ARG) },
        )
    }

    fun value(field: String, required: Boolean = false): Json? =
        fields[field]?.takeIf { it != Json.Null } ?: null.also { if (required) problem(field, Kind.MISSING) }

    /** A required text must not be blank; an optional blank text is null. */
    fun text(field: String, required: Boolean = false): String? {
        val v = value(field, required)
        return when {
            v == null -> null
            v !is Json.Str -> null.also { problem(field, Kind.NOT_STRING) }
            v.value.isNotBlank() -> v.value
            required -> null.also { problem(field, Kind.BLANK) }
            else -> null
        }
    }

    fun bool(field: String, default: Boolean): Boolean = when (val v = value(field)) {
        null -> default
        is Json.Bool -> v.value
        else -> default.also { problem(field, Kind.NOT_BOOLEAN) }
    }

    /** A whole number as written (`12`, not `12.0` or `1e1`) that fits a Long. */
    fun whole(field: String, required: Boolean = false): Long? {
        val v = value(field, required)
        val raw = (v as? Json.Num)?.raw
        return when {
            v == null -> null
            raw == null || !INTEGER.matches(raw) -> null.also { problem(field, Kind.NOT_WHOLE) }
            else -> raw.toLongOrNull() ?: null.also { problem(field, Kind.TOO_BIG, raw) }
        }
    }

    /** A required whole number > 0. */
    fun positive(field: String): Long? = whole(field, required = true)?.let { n ->
        n.takeIf { it > 0 } ?: null.also { problem(field, Kind.NOT_POSITIVE, n) }
    }

    /** An optional list of strings, empty when missing. */
    fun strings(field: String): List<String> {
        val v = value(field)
        val items = (v as? Json.Arr)?.items
        val strings = items?.mapNotNull { (it as? Json.Str)?.value }
        return when {
            v == null -> emptyList()
            strings == null || strings.size != items.size ->
                emptyList<String>().also { problem(field, Kind.NOT_STRING_LIST) }
            else -> strings
        }
    }

    /** A [Resource] ([Resource.of]). Unlike [text], a blank value is a problem, not a missing one. */
    fun resource(field: String, required: Boolean = false): Resource? {
        val v = value(field, required)
        return when {
            v == null -> null
            v !is Json.Str -> null.also { problem(field, Kind.NOT_STRING) }
            else -> Resource.of(v.value) ?: null.also { problem(field, Kind.BAD_RESOURCE, v.value) }
        }
    }

    companion object {
        /** Problems kept per file; more are counted by [failed] only. */
        const val MAX_PROBLEMS = 50

        /** Characters kept per problem argument. */
        const val MAX_ARG = 200

        /** Characters kept of the name of an entry in a place text (`sites[0] (name)`). */
        const val MAX_NAME = 40

        private val INTEGER = Regex("-?(0|[1-9][0-9]*)")
    }
}
