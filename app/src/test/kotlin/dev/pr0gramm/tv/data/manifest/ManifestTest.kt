package dev.pr0gramm.tv.data.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ManifestTest {
    private val sha = "0123456789abcdef".repeat(4)

    private fun q(text: String) = "\"$text\""

    /** A JSON object from raw JSON values; a null value leaves the field out. */
    private fun obj(fields: Map<String, String?>) =
        fields.filterValues { it != null }.entries.joinToString(", ", "{", "}") { (k, v) -> "${q(k)}: $v" }

    private fun list(items: List<String>) = items.joinToString(", ", "[", "]")

    private fun site(id: String, vararg change: Pair<String, String?>) =
        obj(mapOf("id" to q(id), "name" to q(id.uppercase()), "url" to q("https://$id.example/")) + change)

    private fun app(pkg: String, vararg change: Pair<String, String?>) = obj(
        mapOf(
            "package" to q(pkg),
            "name" to q("App"),
            "versionCode" to "3",
            "apk" to q("apks/$pkg.apk"),
            "sha256" to q(sha),
            "size" to "1234",
        ) + change,
    )

    private fun manifest(
        vararg change: Pair<String, String?>,
        sites: List<String> = listOf(site("nova")),
        apps: List<String> = listOf(app("org.kodi")),
    ): String {
        val fields = mapOf("version" to "3", "date" to q("2026-11-18"), "sites" to list(sites), "apps" to list(apps))
        return obj(fields + change)
    }

    /** [field] paired with each of [values]: the cases one test runs through. */
    private fun cases(field: String, vararg values: String?) = values.map { field to it }

    private fun valid(text: String): Manifest = when (val result = Manifest.parse(text)) {
        is ManifestResult.Valid -> result.manifest
        is ManifestResult.Invalid ->
            throw AssertionError("rejected:\n${english(result.problems).joinToString("\n")}\n$text")
    }

    private fun english(problems: List<ManifestProblem>) = problems.map(ManifestProblem::english)

    /** The problems of [text] as English lines ([ManifestProblem.english]). */
    private fun problems(text: String): List<String> = when (val result = Manifest.parse(text)) {
        is ManifestResult.Valid -> throw AssertionError("accepted: $text")
        is ManifestResult.Invalid -> english(result.problems)
    }

    /** The manifest is invalid, and one problem names [where] (`sites[0] (nova).zoom`). */
    private fun assertInvalid(text: String, where: String) {
        val problems = problems(text)
        val message = "expected a problem at $where, got:\n${problems.joinToString("\n")}"
        assertTrue(message, problems.any { it.startsWith("$where:") })
    }

    private fun resource(path: String) = ServerPath.of(path) ?: throw AssertionError("not a resource: $path")

    @Test
    fun readsEveryField() {
        val nova = site(
            "nova",
            "url" to q("https://Nova.example/start"),
            "name" to q("Nova"),
            "description" to q("Desc"),
            "category" to q("Cat"),
            "tags" to """["a", "b"]""",
            "color" to q("#8A1C1C"),
            "allowInsecureContent" to "true",
            "allow" to """["CDN.example.net", "nova.example", "localhost"]""",
            "cursor" to "false",
            "zoom" to "125",
            "desktopMode" to "true",
            "blocklist" to "false",
            "banner" to q("img/nova.png"),
        )
        val kodi = app("org.kodi", "name" to q("Kodi"), "image" to q("img/kodi.png"), "versionCode" to "21000")
        val m = valid(manifest(sites = listOf(nova), apps = listOf(kodi)))

        assertEquals(3, m.version)
        assertEquals(LocalDate.of(2026, 11, 18), m.date)
        val expected = Site(
            id = SiteId("nova"),
            name = "Nova",
            url = "https://Nova.example/start",
            description = "Desc",
            category = "Cat",
            tags = listOf("a", "b"),
            color = 0xFF8A1C1C.toInt(),
            allowInsecureContent = true,
            allow = setOf("nova.example", "cdn.example.net", "localhost"),
            defaults = SiteOptions(Control.FOCUS, Zoom.of(125)!!, Layout.DESKTOP, blocklist = false, favorite = false),
            banner = resource("img/nova.png"),
        )
        assertEquals(listOf(expected), m.sites)
        val apk = Apk(resource("apks/org.kodi.apk"), sha, 1234)
        assertEquals(listOf(CatalogApp("org.kodi", "Kodi", resource("img/kodi.png"), 21000, apk)), m.apps)
    }

    @Test
    fun appliesSiteDefaults() {
        val site = valid(manifest()).sites.single()
        assertNull(site.description)
        assertNull(site.category)
        assertEquals(emptyList<String>(), site.tags)
        assertNull(site.color)
        assertEquals(false, site.allowInsecureContent)
        assertEquals(setOf("nova.example"), site.allow)
        val defaults = SiteOptions(Control.POINTER, Zoom.of(100)!!, Layout.NORMAL, blocklist = true, favorite = false)
        assertEquals(defaults, site.defaults)
        assertNull(site.banner)
    }

    @Test
    fun blankTextsAndNullsCountAsMissing() {
        val nova = site("nova", "description" to q(" "), "category" to "null", "banner" to "null", "color" to "null")
        val m = valid(manifest(sites = listOf(nova), apps = listOf(app("org.kodi", "image" to "null"))))
        assertNull(m.sites.single().description)
        assertNull(m.sites.single().category)
        assertNull(m.sites.single().banner)
        assertNull(m.apps.single().image)
    }

    @Test
    fun keepsTheOrderOfSitesAndApps() {
        val m = valid(manifest(sites = listOf(site("b"), site("a")), apps = listOf(app("org.b"), app("org.a"))))
        assertEquals(listOf("b", "a"), m.sites.map { it.id.value })
        assertEquals(listOf("org.b", "org.a"), m.apps.map { it.pkg })
    }

    @Test
    fun emptyListsAreValid() {
        val m = valid(manifest(sites = emptyList(), apps = emptyList()))
        assertEquals(emptyList<Site>(), m.sites)
        assertEquals(emptyList<CatalogApp>(), m.apps)
    }

    @Test
    fun ignoresUnknownFieldsAndTypes() {
        val text = manifest(
            "ambient" to """[{"type": "clock", "image": "https://elsewhere.example/x.png"}]""",
            "schema" to "2.5",
            sites = listOf(site("nova", "hidden" to "true", "title" to """{"x": [1, 2]}""")),
            apps = listOf(app("org.kodi", "minSdk" to "1.5")),
        )
        assertEquals(listOf("nova"), valid(text).sites.map { it.id.value })
    }

    @Test
    fun imagesAreBannersAndAppImagesWithoutApks() {
        val text = manifest(
            sites = listOf(site("a", "banner" to q("img/a.png")), site("b"), site("c", "banner" to q("img/both.png"))),
            apps = listOf(app("org.kodi", "image" to q("img/kodi.png")), app("org.vlc", "image" to q("img/both.png"))),
        )
        val expected = setOf("img/a.png", "img/both.png", "img/kodi.png").map(::resource).toSet()
        assertEquals(expected, valid(text).images)
    }

    @Test
    fun resourcesAreRelativePathsOnly() {
        listOf("a.png", "img/nova.png", "apks/org.kodi-1_2.apk", "A/B/c.PNG").forEach {
            assertEquals(it, ServerPath.of(it)?.path)
        }
        val notRelative = listOf(
            "", "/img/a.png", "img/", "img//a.png", "../a.png", "img/../a.png", "./a.png", "img/./a.png", "..",
            "https://tv.example.org/img/a.png", "//tv.example.org/a.png", "img/a b.png", "img/a.png?x=1",
            "img/a.png#x", "img\\a.png", "img/%2e%2e/a.png", "c:/a.png", "img/ä.png",
        )
        notRelative.forEach { assertNull(it, ServerPath.of(it)) }
    }

    @Test
    fun versionIsAPositiveWholeNumber() {
        assertEquals(2_147_483_647, valid(manifest("version" to "2147483647")).version)
        cases("version", null, "0", "-1", "1.0", "1.5", "1e0", q("3"), "2147483648", "true").forEach {
            assertInvalid(manifest(it), "version")
        }
    }

    @Test
    fun dateIsAnIsoDate() {
        cases("date", null, q("18.11.2026"), q("2026-02-30"), q("2026-11-18T10:00"), "20261118").forEach {
            assertInvalid(manifest(it), "date")
        }
    }

    @Test
    fun sitesAndAppsAreRequired() {
        cases("sites", null, "{}", q("x")).forEach { assertInvalid(manifest(it), "sites") }
        cases("apps", null, "{}").forEach { assertInvalid(manifest(it), "apps") }
        assertInvalid(manifest(sites = listOf("1")), "sites[0]")
        assertInvalid(manifest(apps = listOf("null")), "apps[0]")
    }

    @Test
    fun siteIdsAreUniqueAndFollowThePattern() {
        assertInvalid(manifest(sites = listOf(site("nova"), site("haha"), site("nova"))), "sites[2] (nova).id")
        listOf("Nova", "1a", "a-b", "a b").forEach {
            assertInvalid(manifest(sites = listOf(site(it))), "sites[0] ($it).id")
        }
        assertInvalid(manifest(sites = listOf(site(""))), "sites[0].id")
        assertInvalid(manifest(sites = listOf(site("nova", "id" to "5"))), "sites[0].id")
        assertInvalid(manifest(sites = listOf(site("nova", "id" to null))), "sites[0].id")
    }

    @Test
    fun siteFieldsAreChecked() {
        val bad = listOf(
            cases("name", null, q(" "), "1"),
            cases("url", null, q("ftp://nova.example/"), q("nova.example"), q("https://"), q("https://a b/")),
            cases("zoom", "200", "49", "12.5", "1e2", q("100"), "99999999999"),
            cases("color", q("red"), q("#12345"), "123"),
            cases("cursor", q("yes")),
            cases("desktopMode", "1"),
            cases("blocklist", "0"),
            cases("allowInsecureContent", q("false")),
            cases("tags", "[1]", q("a")),
            cases("allow", """["https://x.example/"]""", """["a b"]""", "[null]"),
            cases("description", "1"),
            cases("category", "[]"),
            cases("banner", q("ftp://cdn.example/a.png"), q("/img/a.png"), q("../a.png"), q("a b.png"), q(""), "5"),
        ).flatten()
        bad.forEach { assertInvalid(manifest(sites = listOf(site("nova", it))), "sites[0] (nova).${it.first}") }
    }

    @Test
    fun theOwnPackageIsNoCatalogApp() {
        val problems = problems(manifest(apps = listOf(app("org.kodi"), app(Manifest.OWN_PACKAGE))))
        assertEquals(1, problems.size)
        assertTrue(problems.single(), problems.single().startsWith("apps[1] (dev.pr0gramm.tv).package:"))
    }

    @Test
    fun packagesAreUnique() {
        assertInvalid(manifest(apps = listOf(app("org.kodi"), app("org.kodi"))), "apps[1] (org.kodi).package")
        listOf("kodi", "org..kodi", "1org.kodi", "org.kodi!", "org.1kodi").forEach {
            assertInvalid(manifest(apps = listOf(app(it))), "apps[0] ($it).package")
        }
        assertInvalid(manifest(apps = listOf(app(""))), "apps[0].package")
    }

    @Test
    fun appFieldsAreChecked() {
        val tooBig = "9223372036854775808"
        val bad = listOf(
            cases("name", null, q("")),
            cases("sha256", null, q("abc"), q(sha.uppercase()), q("g" + sha.drop(1)), q(sha + "0")),
            cases("size", null, "0", "-1", "1.5", q("12"), tooBig),
            cases("versionCode", null, "0", "-3", "2.0", tooBig),
            cases("apk", null, q("https://user@tv.example.org/apks/x.apk"), q("/apks/x.apk")),
            cases("image", q("https://tv.example.org/img/x.png#v2"), "true"),
        ).flatten()
        bad.forEach { assertInvalid(manifest(apps = listOf(app("org.kodi", it))), "apps[0] (org.kodi).${it.first}") }
    }

    @Test
    fun largeNumbersFitLongs() {
        val kodi = app("org.kodi", "size" to "9223372036854775807", "versionCode" to "9223372036854775807")
        val entry = valid(manifest(apps = listOf(kodi))).apps.single()
        assertEquals(Long.MAX_VALUE, entry.versionCode)
        assertEquals(Long.MAX_VALUE, entry.apk.size)
    }

    @Test
    fun reportsEveryProblemAtOnce() {
        val text = manifest(
            "version" to "0",
            sites = listOf(site("nova", "zoom" to "500"), site("Bad")),
            apps = listOf(app("org.kodi", "size" to "0"), app(Manifest.OWN_PACKAGE)),
        )
        val problems = problems(text)
        val own = "apps[1] (dev.pr0gramm.tv).package"
        val expected = listOf("version", "sites[0] (nova).zoom", "sites[1] (Bad).id", "apps[0] (org.kodi).size", own)
        expected.map { "$it:" }.forEach { where ->
            assertTrue("$where missing in:\n${problems.joinToString("\n")}", problems.any { it.startsWith(where) })
        }
    }
}
