import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

const val ROOT = PackageGraph.ROOT
val noViolations = emptyList<String>()

/** The path of a main source file in [pkg] (below [ROOT]), as the task passes it. */
fun sourcePath(pkg: String) = "app/src/main/kotlin/dev/pr0gramm/tv/${pkg.replace('.', '/')}/A.kt"

/** A file in [pkg] with [lines] from line 3 on, after the package line and a blank line. */
fun sourceFile(pkg: String, vararg lines: String) = sourcePath(pkg) to "package $ROOT.$pkg\n\n" + lines.joinToString("\n") + "\n"

fun violationsIn(pkg: String, vararg lines: String) = sourceFile(pkg, *lines).let { (path, text) -> PackageGraph.violations(path, text) }

/** The line numbers of [messages]. */
fun lineNumbers(messages: List<String>) = messages.map { it.substringAfter(".kt:").substringBefore(':').toInt() }

/** One allowed and one forbidden case per rule of [PackageGraph] (AC2, AC3). */
class PackageGraphTest {
    @Test fun `a lower layer is allowed`() {
        assertEquals(noViolations, violationsIn("app", "import $ROOT.ui.launcher.Tile", "import $ROOT.core.http.Http"))
        assertEquals(noViolations, violationsIn("ui.launcher", "import $ROOT.domain.savedpages.SavedShelf", "import $ROOT.data.blocklist.Blocklist"))
        assertEquals(noViolations, violationsIn("domain.savedpages", "import $ROOT.data.savedpages.SavedPage", "import $ROOT.core.http.Http"))
        assertEquals(noViolations, violationsIn("data.savedpages", "import $ROOT.core.useragent.siteUserAgent"))
    }

    @Test fun `a higher layer is a violation`() {
        assertEquals(
            listOf(
                "${sourcePath("domain.savedpages")}:3: domain.savedpages → $ROOT.ui.launcher.Tile: " +
                    "höhere Schicht ui (app > ui > domain > data > core)",
            ),
            violationsIn("domain.savedpages", "import $ROOT.ui.launcher.Tile"),
        )
        assertEquals(listOf(4, 5), lineNumbers(violationsIn("core.http", "", "import $ROOT.data.manifest.Site", "import $ROOT.app.AppContainer")))
    }

    @Test fun `the own slice and its segments are allowed`() {
        assertEquals(noViolations, violationsIn("ui.launcher", "import $ROOT.ui.launcher.settings.SettingsScreen"))
        assertEquals(noViolations, violationsIn("ui.launcher.settings", "import $ROOT.ui.launcher.Tile"))
        assertEquals(noViolations, violationsIn("domain.appinstall", "import $ROOT.domain.appinstall.InstallQueue"))
    }

    @Test fun `another slice of ui, domain or data is a violation`() {
        assertEquals(
            listOf(
                "${sourcePath("ui.launcher")}:3: ui.launcher → $ROOT.ui.browser.BrowserArgs: " +
                    "anderer Slice ui.browser derselben Schicht (erlaubt ist nur data.* → data.manifest)",
            ),
            violationsIn("ui.launcher", "import $ROOT.ui.browser.BrowserArgs"),
        )
        assertEquals(listOf(3), lineNumbers(violationsIn("ui.launcher.settings", "import $ROOT.ui.browser.KeyRouter")))
        assertEquals(listOf(3), lineNumbers(violationsIn("domain.selfupdate", "import $ROOT.domain.appinstall.AppInstaller")))
        assertEquals(listOf(3), lineNumbers(violationsIn("data.savedpages", "import $ROOT.data.sitesettings.SiteOverrides")))
    }

    @Test fun `every data slice may reference data manifest`() {
        assertEquals(noViolations, violationsIn("data.savedpages", "import $ROOT.data.manifest.Site"))
        assertEquals(noViolations, violationsIn("data.catalogsync", "import $ROOT.data.manifest.Manifest"))
    }

    @Test fun `segments of app and core may reference each other`() {
        assertEquals(noViolations, violationsIn("app", "import $ROOT.app.EventRoutes"))
        assertEquals(noViolations, violationsIn("core.designsystem", "import $ROOT.core.remotekeys.OK_KEYS"))
        assertEquals(noViolations, violationsIn("core.remotekeys", "import $ROOT.core.designsystem.Night"))
    }

    @Test fun `every package of the table is allowed`() {
        for ((layer, names) in PackageGraph.TABLE) {
            for (pkg in names.map { "${layer.id}.$it" }.ifEmpty { listOf(layer.id) }) {
                assertEquals(noViolations, violationsIn(pkg, "val x = 1"), pkg)
            }
        }
    }

    @Test fun `a package outside the table is a violation`() {
        for (pkg in listOf("player", "ui.player", "ui.launcher.player", "core", "app.shell")) {
            val path = sourcePath(pkg)
            assertEquals(
                listOf("$path:1: Package $ROOT.$pkg steht nicht in der Tabelle (PackageGraph.TABLE)"),
                PackageGraph.violations(path, "package $ROOT.$pkg\n"),
                pkg,
            )
        }
        assertEquals(
            listOf("app/src/main/kotlin/other/app/A.kt:1: Package other.app steht nicht in der Tabelle (PackageGraph.TABLE)"),
            PackageGraph.violations("app/src/main/kotlin/other/app/A.kt", "package other.app\n"),
        )
    }

    @Test fun `a file without a package is a violation`() {
        val path = "app/src/main/kotlin/A.kt"
        assertEquals(
            listOf("$path:1: Package fehlt, erlaubt sind nur die Packages der Tabelle (PackageGraph.TABLE)"),
            PackageGraph.violations(path, "// no package\nval x = 1\n"),
        )
    }

    @Test fun `the directory must match the package`() {
        val text = "/** KDoc. */\n@file:JvmName(\"A\")\n\npackage $ROOT.ui.browser\n"
        assertEquals(noViolations, PackageGraph.violations("app/src/main/kotlin/dev/pr0gramm/tv/ui/browser/A.kt", text))
        val path = "app/src/main/kotlin/dev/pr0gramm/tv/ui/launcher/A.kt"
        assertEquals(
            listOf("$path:4: Verzeichnis dev/pr0gramm/tv/ui/launcher passt nicht zum Package $ROOT.ui.browser"),
            PackageGraph.violations(path, text),
        )
    }

    @Test fun `android and androidx are allowed outside domain`() {
        assertEquals(noViolations, violationsIn("ui.browser", "import android.webkit.WebView", "import androidx.compose.runtime.Composable"))
        assertEquals(noViolations, violationsIn("data.savedpages", "import androidx.datastore.core.DataStore"))
        assertEquals(noViolations, violationsIn("core.packageinstaller", "import android.content.pm.PackageInstaller"))
    }

    @Test fun `android or androidx in domain is a violation`() {
        assertEquals(
            listOf(
                "${sourcePath("domain.savedpages")}:3: domain.savedpages → android.content.Context: android oder androidx in domain",
                "${sourcePath("domain.savedpages")}:4: domain.savedpages → androidx.lifecycle.ViewModel: android oder androidx in domain",
                "${sourcePath("domain.savedpages")}:5: domain.savedpages → android.util.Log: android oder androidx in domain",
            ),
            violationsIn(
                "domain.savedpages",
                "import android.content.Context",
                "import androidx.lifecycle.ViewModel",
                "val log = android.util.Log::class",
            ),
        )
        assertEquals(noViolations, violationsIn("domain.savedpages", "val android = 1", "val y = android + kotlin.math.abs(-1)"))
    }

    @Test fun `data manifest may use Kotlin and itself only`() {
        assertEquals(
            noViolations,
            violationsIn("data.manifest", "import kotlin.math.max", "import java.net.URI", "import $ROOT.data.manifest.Json"),
        )
    }

    @Test fun `data manifest referencing the app or Android is a violation`() {
        val pure = "data.manifest bleibt reines Kotlin (build-logic kompiliert es)"
        assertEquals(
            listOf(
                "${sourcePath("data.manifest")}:3: data.manifest → $ROOT.core.http.Http: $pure",
                "${sourcePath("data.manifest")}:4: data.manifest → $ROOT.R: $pure",
                "${sourcePath("data.manifest")}:5: data.manifest → $ROOT.BuildConfig: $pure",
                "${sourcePath("data.manifest")}:6: data.manifest → android.net.Uri: $pure",
                "${sourcePath("data.manifest")}:7: data.manifest → androidx.annotation.Keep: $pure",
            ),
            violationsIn(
                "data.manifest",
                "import $ROOT.core.http.Http",
                "import $ROOT.R",
                "import $ROOT.BuildConfig",
                "import android.net.Uri",
                "import androidx.annotation.Keep",
            ),
        )
    }

    @Test fun `core appversion may use Kotlin and itself only`() {
        val pure = "core.appversion bleibt reines Kotlin (build-logic kompiliert es)"
        assertEquals(noViolations, violationsIn("core.appversion", "import kotlin.math.max", "import $ROOT.core.appversion.A"))
        assertEquals(
            listOf(
                "${sourcePath("core.appversion")}:3: core.appversion → $ROOT.core.http.Http: $pure",
                "${sourcePath("core.appversion")}:4: core.appversion → $ROOT.BuildConfig: $pure",
                "${sourcePath("core.appversion")}:5: core.appversion → android.os.Build: $pure",
            ),
            violationsIn("core.appversion", "import $ROOT.core.http.Http", "import $ROOT.BuildConfig", "import android.os.Build"),
        )
    }

    @Test fun `core segments without a cycle are allowed`() {
        val files = mapOf(
            sourceFile("core.designsystem", "import $ROOT.core.remotekeys.OK_KEYS"),
            sourceFile("core.http", "import $ROOT.core.updatefailure.UpdateFailure"),
            sourceFile("core.packageinstaller", "import $ROOT.core.http.Http"),
        )
        assertEquals(noViolations, PackageGraph.cycles(files))
    }

    @Test fun `a cycle between core segments is a violation that names the cycle`() {
        val files = mapOf(
            sourceFile("core.http", "import $ROOT.core.updatefailure.UpdateFailure"),
            sourceFile("core.updatefailure", "import $ROOT.core.packageinstaller.PackageInstallerPort"),
            sourceFile("core.packageinstaller", "", "import $ROOT.core.http.Http"),
            sourceFile("core.designsystem", "import $ROOT.core.remotekeys.OK_KEYS"),
            sourceFile("ui.launcher", "import $ROOT.core.http.Http"),
        )
        assertEquals(
            listOf(
                "${sourcePath("core.packageinstaller")}:4: Zyklus zwischen Segmenten von core: " +
                    "core.http → core.updatefailure → core.packageinstaller → core.http",
            ),
            PackageGraph.cycles(files),
        )
    }

    @Test fun `a group of core segments in cycles is reported once`() {
        val files = mapOf(
            sourceFile("core.http", "import $ROOT.core.useragent.A", "import $ROOT.core.updatefailure.B"),
            sourceFile("core.useragent", "import $ROOT.core.http.C"),
            sourceFile("core.updatefailure", "import $ROOT.core.http.D"),
            sourceFile("core.designsystem", "import $ROOT.core.remotekeys.E"),
            sourceFile("core.remotekeys", "import $ROOT.core.designsystem.F"),
        )
        val cycles = PackageGraph.cycles(files).map { it.substringAfter("core: ") }
        assertEquals(listOf("core.designsystem → core.remotekeys → core.designsystem", "core.http → core.updatefailure → core.http"), cycles)
    }
}
