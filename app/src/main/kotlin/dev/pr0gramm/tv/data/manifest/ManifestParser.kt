package dev.pr0gramm.tv.data.manifest

import dev.pr0gramm.tv.data.manifest.ManifestProblem.Kind
import java.net.URI
import java.net.URISyntaxException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The manifest's JSON shape and all its rules, with one [ManifestProblem] per broken rule. */
internal object ManifestParser {
    fun parse(text: String): ManifestResult {
        val problems = mutableListOf<ManifestProblem>()
        val root = try {
            Json.parse(text)
        } catch (e: JsonException) {
            problems += e.problem
            null
        }
        val manifest = when (root) {
            null -> null
            is Json.Obj -> manifest(Fields("", root.fields, problems))
            else -> null.also { problems += ManifestProblem("", Kind.ROOT_NOT_OBJECT) }
        }
        return if (manifest != null && problems.isEmpty()) {
            ManifestResult.Valid(manifest)
        } else {
            ManifestResult.Invalid(problems)
        }
    }

    private fun manifest(top: Fields): Manifest? {
        val version = version(top)
        val date = date(top)
        val ids = mutableSetOf<String>()
        val packages = mutableSetOf<String>()
        val sites = list(top, "sites", hint = "id") { SiteReader(it, ids).read() }
        val apps = list(top, "apps", hint = "package") { AppReader(it, packages).read() }
        if (version == null || date == null || sites == null) return null
        return apps?.let { Manifest(version, date, sites, it) }
    }

    private fun version(top: Fields): Int? = top.whole("version", required = true)?.let { n ->
        n.takeIf { it in 1L..Int.MAX_VALUE.toLong() }?.toInt()
            ?: null.also { top.problem("version", Kind.VERSION_RANGE, n, Int.MAX_VALUE) }
    }

    private fun date(top: Fields): LocalDate? = top.text("date", required = true)?.let { d ->
        try {
            LocalDate.parse(d)
        } catch (_: DateTimeParseException) {
            null.also { top.problem("date", Kind.BAD_DATE, d) }
        }
    }

    /** The required list [field] of objects, each read under `field[i] (<value of [hint]>)`; null on any problem. */
    private fun <T : Any> list(top: Fields, field: String, hint: String, read: (Fields) -> T?): List<T>? {
        val items = top.value(field, required = true)?.let { v ->
            (v as? Json.Arr)?.items ?: null.also { top.problem(field, Kind.NOT_LIST) }
        }
        val entries = items.orEmpty().mapIndexedNotNull { i, item ->
            val fields = (item as? Json.Obj)?.fields
            if (fields == null) {
                null.also { top.problem("$field[$i]", Kind.NOT_OBJECT) }
            } else {
                val name = (fields[hint] as? Json.Str)?.value?.takeIf { it.isNotBlank() }
                val shown = name?.take(Fields.MAX_NAME)
                read(Fields("$field[$i]" + shown?.let { " ($it)" }.orEmpty(), fields, top.problems))
            }
        }
        return entries.takeIf { items != null && it.size == items.size }
    }
}

/** One entry of `sites`; [ids] are the ids seen so far. */
private class SiteReader(private val f: Fields, private val ids: MutableSet<String>) {
    fun read(): Site? {
        val id = id()
        val name = f.text("name", required = true)
        val url = url()
        // The placeholders for missing values never leave: each one has been recorded as a problem.
        val site = Site(
            id = SiteId(id.orEmpty()),
            name = name.orEmpty(),
            url = url?.toString().orEmpty(),
            description = f.text("description"),
            category = f.text("category"),
            tags = f.strings("tags"),
            color = color(),
            allowInsecureContent = f.bool("allowInsecureContent", false),
            allow = (listOfNotNull(url?.host?.lowercase()) + allow()).toSet(),
            defaults = SiteOptions(
                control = if (f.bool("cursor", true)) Control.POINTER else Control.FOCUS,
                zoom = zoom(),
                layout = if (f.bool("desktopMode", false)) Layout.DESKTOP else Layout.NORMAL,
                blocklist = f.bool("blocklist", true),
                favorite = false,
            ),
            banner = f.resource("banner"),
        )
        return site.takeUnless { f.failed }
    }

    private fun id(): String? = f.text("id", required = true)?.let { id ->
        when {
            !ID.matches(id) -> null.also { f.problem("id", Kind.BAD_ID, id) }
            !ids.add(id) -> null.also { f.problem("id", Kind.DUPLICATE, id) }
            else -> id
        }
    }

    private fun url(): URI? = f.text("url", required = true)?.let { url ->
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            null
        }
        uri?.takeIf { it.scheme in SCHEMES && !it.host.isNullOrEmpty() }
            ?: null.also { f.problem("url", Kind.BAD_URL, url) }
    }

    private fun color(): Int? = f.text("color")?.let { c ->
        if (COLOR.matches(c)) {
            (OPAQUE or c.drop(1).toLong(HEX)).toInt()
        } else {
            null.also { f.problem("color", Kind.BAD_COLOR, c) }
        }
    }

    private fun zoom(): Zoom {
        val percent = f.whole("zoom") ?: return Zoom(DEFAULT_ZOOM)
        val range = Zoom.RANGE.first.toLong()..Zoom.RANGE.last.toLong()
        return Zoom.of(percent.toInt()).takeIf { percent in range }
            ?: Zoom(DEFAULT_ZOOM).also {
                f.problem("zoom", Kind.ZOOM_RANGE, percent, Zoom.RANGE.first, Zoom.RANGE.last)
            }
    }

    private fun allow(): List<String> = f.strings("allow").mapNotNull { d ->
        d.lowercase().takeIf { DOMAIN.matches(it) || HOST_LABEL.matches(it) }
            ?: null.also { f.problem("allow", Kind.BAD_DOMAIN, d) }
    }

    private companion object {
        const val DEFAULT_ZOOM = 100
        const val HEX = 16
        const val OPAQUE = 0xFF000000L
        val SCHEMES = setOf("http", "https")
        val ID = Regex("[a-z][a-z0-9]*")
        val COLOR = Regex("#[0-9a-fA-F]{6}")

        // The domain rule of build-logic's BlocklistFormat.DOMAIN; `allow` also takes a host without a dot
        // (`localhost`, a LAN name).
        val DOMAIN = Regex("[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?(\\.[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?)+")
        val HOST_LABEL = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?")
    }
}

/** One entry of `apps`; [packages] are the packages seen so far. */
private class AppReader(private val f: Fields, private val packages: MutableSet<String>) {
    fun read(): CatalogApp? {
        val pkg = pkg()
        val name = f.text("name", required = true)
        val banner = f.resource("banner")
        val versionCode = f.positive("versionCode")
        val sha256 = sha256()
        val size = f.positive("size")
        // The placeholders for missing values never leave: each one has been recorded as a problem.
        val entry = f.resource("apk", required = true)?.let { file ->
            CatalogApp(pkg.orEmpty(), name.orEmpty(), banner, versionCode ?: 0, Apk(file, sha256.orEmpty(), size ?: 0))
        }
        return entry.takeUnless { f.failed }
    }

    private fun pkg(): String? = f.text("package", required = true)?.let { pkg ->
        when {
            !PACKAGE.matches(pkg) -> null.also { f.problem("package", Kind.BAD_PACKAGE, pkg) }
            pkg == Manifest.OWN_PACKAGE -> null.also { f.problem("package", Kind.OWN_PACKAGE, pkg) }
            !packages.add(pkg) -> null.also { f.problem("package", Kind.DUPLICATE, pkg) }
            else -> pkg
        }
    }

    private fun sha256(): String? = f.text("sha256", required = true)?.let { sha ->
        sha.takeIf { SHA256.matches(it) }
            ?: null.also { f.problem("sha256", Kind.BAD_SHA256) }
    }

    private companion object {
        val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
