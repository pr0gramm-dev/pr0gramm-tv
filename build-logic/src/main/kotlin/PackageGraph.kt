/** The layers of the app, top to bottom (FSD with the names of the Android Architecture Guide). */
enum class Layer {
    APP, UI, DOMAIN, DATA, CORE;

    /** The package part below [PackageGraph.ROOT]. */
    val id: String get() = name.lowercase()
}

/**
 * The package rules of the app (AC2), checked by the task `packageGraph`. A file may reference its own layer and
 * every layer below it. In `ui`, `domain` and `data` a file may reference only its own slice, and every `data` slice
 * may reference `data.manifest`. The segments of `app` and `core` may reference each other, but `core` segments
 * must not form a cycle. `domain` uses no `android` or `androidx`. `data.manifest` and `core.appversion` stay pure
 * Kotlin, because build-logic compiles them. Only code counts: [KotlinCode] blanks comments and literal text first.
 */
object PackageGraph {
    const val ROOT = "dev.pr0gramm.tv"

    /** Paths below this part name the directory that must match the package. */
    const val SOURCE_ROOT = "src/main/kotlin/"

    /**
     * The only list of slices and segments below [ROOT]. `app` has no slices; a `.` in a name marks a segment of a
     * slice (`launcher.settings` belongs to slice `launcher`). `docs/architecture.md` §5 shows the same table
     * (PackageGraphDocsTest).
     */
    val TABLE: Map<Layer, List<String>> = mapOf(
        Layer.APP to emptyList(),
        Layer.UI to listOf("launcher", "launcher.settings", "browser"),
        Layer.DOMAIN to listOf("savedpages", "catalogsync", "selfupdate", "appinstall"),
        Layer.DATA to listOf(
            "manifest", "catalogsync", "savedpages", "appsettings", "sitesettings", "recentlyopened", "installedapps",
            "blocklist", "releases",
        ),
        Layer.CORE to listOf(
            "designsystem", "remotekeys", "http", "updatefailure", "packageinstaller", "useragent", "appversion",
            "text",
        ),
    )

    /** The full name of every package of [TABLE], in its order. */
    val PACKAGES: List<String> get() = PLACES.keys.toList()

    private val PLACES: Map<String, Part> = TABLE.entries.flatMap { (layer, names) ->
        names.ifEmpty { listOf("") }.map { Part(layer, it) }
    }.associateBy { "$ROOT.$it".removeSuffix(".") }

    /** Generated classes in the root package, allowed from every layer except [PURE]. */
    private val GENERATED = setOf("R", "BuildConfig")
    private val MANIFEST = Part(Layer.DATA, "manifest")

    /** Packages that build-logic compiles: they reference only Kotlin, Java and themselves. */
    private val PURE = setOf(MANIFEST, Part(Layer.CORE, "appversion"))
    private val SEGMENTED = setOf(Layer.APP, Layer.CORE)

    private val PACKAGE = Regex("""^[ \t]*package[ \t]+([\w.]+)""", RegexOption.MULTILINE)

    /** A qualified name below [ROOT], `android` or `androidx`; a `*` ends a star import. */
    private val REFERENCE =
        Regex("""(?<![\w.])(?:dev\.pr0gramm\.tv(?!\w)(?:\.(?:\w+|\*))*|androidx?(?:\.(?:\w+|\*))+)""")

    /**
     * One German message "path:line: rule" per problem of the file at [path]. [path] is relative and contains
     * [SOURCE_ROOT]; the directory after it must match the package.
     */
    fun violations(path: String, text: String): List<String> {
        val file = Source.of(text)
        val pkg = file.pkg ?: return listOf("$path:1: Package fehlt, erlaubt sind nur die Packages der Tabelle $TABLE_NAME")
        val problems = mutableListOf<String>()
        val at = "$path:${pkg.line}"
        val from = PLACES[pkg.name]
        if (from == null) problems += "$at: Package ${pkg.name} steht nicht in der Tabelle $TABLE_NAME"
        val dir = path.substringAfter(SOURCE_ROOT).substringBeforeLast('/', "")
        if (dir != pkg.name.replace('.', '/')) problems += "$at: Verzeichnis $dir passt nicht zum Package ${pkg.name}"
        if (from == null) return problems
        for (ref in file.references) {
            rule(from, ref)?.let { problems += "$path:${ref.line}: $from → ${ref.name}: $it" }
        }
        return problems
    }

    /**
     * Every group of `core` segments that reference each other in a circle, one message per group over all [files]
     * (path → text). The message names one cycle of the group and sits at the reference that closes it.
     */
    fun cycles(files: Map<String, String>): List<String> {
        val edges = sortedMapOf<String, MutableMap<String, String>>()
        for ((path, text) in files.toSortedMap()) {
            val file = Source.of(text)
            val from = file.pkg?.let { PLACES[it.name] }?.takeIf { it.layer == Layer.CORE } ?: continue
            for (ref in file.references) {
                val to = (resolve(ref.name) as? Target.In)?.part ?: continue
                if (to.layer == Layer.CORE && to != from) {
                    edges.getOrPut(from.toString()) { sortedMapOf() }.putIfAbsent(to.toString(), "$path:${ref.line}")
                }
            }
        }
        val next = { node: String -> edges[node]?.keys.orEmpty() }
        val reached = edges.keys.associateWith { reachable(it, next) }
        val groups = edges.keys
            .map { a -> edges.keys.filter { b -> b in reached.getValue(a) && a in reached.getValue(b) } }
            .filter { it.size > 1 }
            .distinct()
        return groups.map { group ->
            val cycle = shortestCycle(group.first(), group.toSet(), next)
            val at = edges.getValue(cycle[cycle.size - 2]).getValue(cycle.last())
            "$at: Zyklus zwischen Segmenten von core: ${cycle.joinToString(" → ")}"
        }
    }

    private fun rule(from: Part, ref: Reference): String? {
        if (!ref.name.startsWith(ROOT)) {
            return when {
                from in PURE -> pure(from)
                from.layer == Layer.DOMAIN -> "android oder androidx in domain"
                else -> null
            }
        }
        val target = resolve(ref.name)
        if (from in PURE && (target as? Target.In)?.part != from) return pure(from)
        val to = when (target) {
            Target.Generated -> return null
            is Target.Unknown -> return target.reason
            is Target.In -> target.part
        }
        return when {
            to.layer < from.layer -> "höhere Schicht ${to.layer.id} (app > ui > domain > data > core)"
            to.layer != from.layer || from.layer in SEGMENTED || to.slice == from.slice -> null
            from.layer == Layer.DATA && to == MANIFEST -> null
            else -> "anderer Slice ${to.layer.id}.${to.slice} derselben Schicht ($ONLY_MANIFEST)"
        }
    }

    /** The part of the table [name] points to; [name] starts with [ROOT]. */
    private fun resolve(name: String): Target {
        val parts = name.removePrefix(ROOT).split('.').filter { it.isNotEmpty() }
        val first = parts.firstOrNull()
        if (first in GENERATED) return Target.Generated
        val layer = Layer.entries.find { it.id == first }
            ?: return Target.Unknown("keine bekannte Schicht (app, ui, domain, data, core) und nicht R oder BuildConfig")
        val names = TABLE.getValue(layer)
        if (names.isEmpty()) return Target.In(Part(layer, ""))
        val rest = parts.drop(1)
        if (rest.firstOrNull().let { it == null || it == "*" }) return Target.Unknown("Referenz ohne Slice oder Segment")
        val known = names.filter { it.split('.').let { parts -> rest.take(parts.size) == parts } }.maxByOrNull { it.length }
            ?: return Target.Unknown("${layer.id}.${rest.first()} steht nicht in der Tabelle $TABLE_NAME")
        return Target.In(Part(layer, known))
    }

    private fun reachable(start: String, next: (String) -> Set<String>): Set<String> {
        val seen = mutableSetOf<String>()
        val todo = ArrayDeque(next(start))
        while (todo.isNotEmpty()) todo.removeFirst().let { if (seen.add(it)) todo += next(it) }
        return seen
    }

    /** The shortest way from [start] back to [start] inside [group], as a list that begins and ends with [start]. */
    private fun shortestCycle(start: String, group: Set<String>, next: (String) -> Set<String>): List<String> {
        val before = mutableMapOf<String, String>()
        val todo = ArrayDeque(listOf(start))
        while (todo.isNotEmpty()) {
            val node = todo.removeFirst()
            for (to in next(node).filter { it in group }) {
                if (to == start) return generateSequence(node) { before[it] }.toList().reversed().plus(start)
                if (to !in before) {
                    before[to] = node
                    todo += to
                }
            }
        }
        error("$start is in no cycle of $group")
    }

    private fun pure(part: Part) = "$part bleibt reines Kotlin (build-logic kompiliert es)"

    private const val ONLY_MANIFEST = "erlaubt ist nur data.* → data.manifest"
    private const val TABLE_NAME = "(PackageGraph.TABLE)"

    /** A slice or segment of the table; [name] is empty for `app`. */
    private data class Part(val layer: Layer, val name: String) {
        val slice: String get() = name.substringBefore('.')

        override fun toString() = if (name.isEmpty()) layer.id else "${layer.id}.$name"
    }

    private sealed interface Target {
        data object Generated : Target

        data class In(val part: Part) : Target

        data class Unknown(val reason: String) : Target
    }

    private data class Declaration(val name: String, val line: Int)

    /** A name as written in the code: an import path or a qualified name, `dev.pr0gramm.tv…` or `android…`. */
    private data class Reference(val name: String, val line: Int)

    /** The package and the references of one file, read from its code without comments and literal text. */
    private class Source(val pkg: Declaration?, val references: List<Reference>) {
        companion object {
            fun of(text: String): Source {
                val code = KotlinCode.code(text)
                val breaks = code.indices.filter { code[it] == '\n' }
                val line = { offset: Int -> breaks.binarySearch(offset).let { if (it < 0) -it - 1 else it } + 1 }
                val pkg = PACKAGE.find(code)
                val references = REFERENCE.findAll(code)
                    .filter { pkg == null || it.range.first !in pkg.range }
                    .map { Reference(it.value, line(it.range.first)) }
                    .toList()
                return Source(pkg?.let { Declaration(it.groupValues[1], line(it.groups[1]!!.range.first)) }, references)
            }
        }
    }
}
