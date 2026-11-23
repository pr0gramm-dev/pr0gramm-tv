import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** What [PackageGraph] reads as a reference (AC2, last paragraph; AC3). */
class PackageGraphReferencesTest {
    private val higher = "höhere Schicht ui (app > ui > domain > data > core)"

    @Test fun `a qualified name in code counts`() {
        assertEquals(
            listOf("${sourcePath("data.savedpages")}:4: data.savedpages → $ROOT.ui.launcher.Tile.Site: $higher"),
            violationsIn("data.savedpages", "", "val x: $ROOT.ui.launcher.Tile.Site? = null"),
        )
        assertEquals(listOf(3, 4), lineNumbers(violationsIn("domain.appinstall", "fun f() = $ROOT.ui.browser.open(1)", "val y = listOf($ROOT.app.A)")))
    }

    @Test fun `comments do not count`() {
        val text = listOf(
            "// import $ROOT.ui.launcher.Tile",
            "/* $ROOT.ui.launcher.Tile /* nested $ROOT.ui.browser.A */ still $ROOT.ui.browser.B */",
            "/** KDoc: [$ROOT.ui.launcher.Tile] */",
            "val x = 1 // $ROOT.app.A",
        )
        assertEquals(noViolations, violationsIn("domain.savedpages", *text.toTypedArray()))
    }

    @Test fun `string and char literals do not count`() {
        val text = listOf(
            "val a = \"$ROOT.ui.launcher.Tile\"",
            "val b = \"\"\"",
            "    $ROOT.ui.launcher.Tile \"\"\"",
            "val c = '\"'.toString() + \"$ROOT.app.A\"",
            "val d = \"\\\"$ROOT.app.A\"",
        )
        assertEquals(noViolations, violationsIn("domain.savedpages", *text.toTypedArray()))
    }

    @Test fun `code in a template counts`() {
        val text = listOf(
            "val a = \"x \${$ROOT.ui.launcher.Tile.NAME} y\"",
            "val b = \"\"\"x \${$ROOT.ui.browser.A.B}\"\"\"",
        )
        assertEquals(listOf(3, 4), lineNumbers(violationsIn("domain.savedpages", *text.toTypedArray())))
    }

    @Test fun `an import with as counts by its path`() {
        assertEquals(
            listOf("${sourcePath("domain.savedpages")}:3: domain.savedpages → $ROOT.ui.launcher.Tile: $higher"),
            violationsIn("domain.savedpages", "import $ROOT.ui.launcher.Tile as Shown"),
        )
        assertEquals(noViolations, violationsIn("ui.launcher", "import $ROOT.data.manifest.Site as CatalogSite"))
    }

    @Test fun `a star import of a slice counts by its slice`() {
        assertEquals(noViolations, violationsIn("ui.launcher", "import $ROOT.data.manifest.*", "import $ROOT.ui.launcher.settings.*"))
        assertEquals(
            listOf(
                "${sourcePath("ui.launcher")}:3: ui.launcher → $ROOT.ui.browser.*: " +
                    "anderer Slice ui.browser derselben Schicht (erlaubt ist nur data.* → data.manifest)",
            ),
            violationsIn("ui.launcher", "import $ROOT.ui.browser.*"),
        )
    }

    @Test fun `a star import of a layer without a slice is a violation`() {
        for (layer in listOf("ui", "domain", "data", "core")) {
            assertEquals(
                listOf("${sourcePath("app")}:3: app → $ROOT.$layer.*: Referenz ohne Slice oder Segment"),
                violationsIn("app", "import $ROOT.$layer.*"),
                layer,
            )
        }
        assertEquals(listOf(3), lineNumbers(violationsIn("app", "val x = $ROOT.core")))
        assertEquals(noViolations, violationsIn("app", "import $ROOT.app.*"))
    }

    @Test fun `R and BuildConfig are allowed from every layer`() {
        for (pkg in listOf("app", "ui.launcher", "domain.selfupdate", "data.catalogsync", "core.designsystem")) {
            val text = listOf("import $ROOT.R", "import $ROOT.BuildConfig", "val s = $ROOT.R.string.app_name")
            assertEquals(noViolations, violationsIn(pkg, *text.toTypedArray()), pkg)
        }
    }

    @Test fun `a name without a known layer is a violation`() {
        val unknown = "keine bekannte Schicht (app, ui, domain, data, core) und nicht R oder BuildConfig"
        assertEquals(
            listOf(
                "${sourcePath("app")}:3: app → $ROOT.catalog.Site: $unknown",
                "${sourcePath("app")}:4: app → $ROOT.application.A: $unknown",
                "${sourcePath("app")}:5: app → $ROOT.*: $unknown",
                "${sourcePath("app")}:6: app → $ROOT: $unknown",
            ),
            violationsIn("app", "import $ROOT.catalog.Site", "import $ROOT.application.A", "import $ROOT.*", "val p = $ROOT"),
        )
    }

    @Test fun `a slice or segment outside the table is a violation`() {
        assertEquals(
            listOf(
                "${sourcePath("app")}:3: app → $ROOT.ui.player.A: ui.player steht nicht in der Tabelle (PackageGraph.TABLE)",
                "${sourcePath("app")}:4: app → $ROOT.core.Theme: core.Theme steht nicht in der Tabelle (PackageGraph.TABLE)",
            ),
            violationsIn("app", "import $ROOT.ui.player.A", "import $ROOT.core.Theme"),
        )
    }

    @Test fun `similar names are not references`() {
        val text = listOf(
            "val a = mydev.pr0gramm.tv.ui.launcher.A",
            "val b = x.dev.pr0gramm.tv.ui.launcher.A",
            "val c = dev.pr0gramm.tvx.ui.launcher.A",
            "val d = x.android.content.Context",
            "val e = androidy.content.Context",
        )
        assertEquals(noViolations, violationsIn("domain.savedpages", *text.toTypedArray()))
    }

    @Test fun `every violation of a file is reported with its line`() {
        val text = listOf(
            "import $ROOT.ui.launcher.Tile",
            "import $ROOT.data.manifest.Site",
            "import $ROOT.domain.appinstall.AppInstaller",
            "",
            "fun f(c: android.content.Context) = $ROOT.app.A",
        )
        assertEquals(listOf(3, 5, 7, 7), lineNumbers(violationsIn("domain.savedpages", *text.toTypedArray())))
    }
}
