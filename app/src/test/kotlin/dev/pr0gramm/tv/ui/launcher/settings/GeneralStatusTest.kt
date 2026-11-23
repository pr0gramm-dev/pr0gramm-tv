package dev.pr0gramm.tv.ui.launcher.settings

import dev.pr0gramm.tv.core.text.StringTable
import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.domain.selfupdate.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The status line of the check row, the failure texts and the lines under "About the app". */
class GeneralStatusTest {
    private val de = Strings.de
    private val en = Strings.en

    /** The status line of the check row in [table]. */
    private fun status(info: GeneralInfo, table: StringTable = de) = table(General.rows(info).first().value)

    /** The about lines in [table]: label to value. */
    private fun about(info: GeneralInfo, table: StringTable = de) =
        General.about(info).map { (label, value) -> table(label) to table(value) }

    private fun info(status: UpdateStatus = UpdateStatus.Idle, checking: Boolean = false) =
        GENERAL_INFO.copy(status = status, checking = checking)

    @Test
    fun statusLineShowsTheAppVersionBeforeAnyCheck() {
        assertEquals("Version 1.0 (7)", status(info()))
        assertEquals("Version 1.0 (7)", status(info(), en))
    }

    @Test
    fun statusLineIsAboutTheAppEvenWithoutCatalog() {
        assertEquals("Version 1.0 (7)", status(info().copy(catalog = null)))
        assertEquals("Version 1.0 (7) · aktuell", status(info(UpdateStatus.UpToDate).copy(catalog = null)))
    }

    @Test
    fun statusLineSaysCurrentWithTheAppVersion() {
        assertEquals("Version 1.0 (7) · aktuell", status(info(UpdateStatus.UpToDate)))
        assertEquals("Version 1.0 (7) · up to date", status(info(UpdateStatus.UpToDate), en))
    }

    @Test
    fun statusLineSaysDeployOnlyForBuildsWithoutReleaseKey() {
        assertEquals("Neue Version, nur per Deploy installierbar", status(info(UpdateStatus.DeployOnly)))
        assertEquals("New version, installs only with a deploy", status(info(UpdateStatus.DeployOnly), en))
    }

    @Test
    fun statusLineWhileCheckingBeatsTheOldStatus() {
        assertEquals("Suche …", status(info(UpdateStatus.UpToDate, checking = true)))
        assertEquals("Checking…", status(info(UpdateStatus.UpToDate, checking = true), en))
    }

    @Test
    fun statusLineShowsDownloadProgress() {
        assertEquals("App-Update wird geladen · 42 %", status(info(UpdateStatus.Downloading(42))))
        assertEquals("Downloading app update · 42%", status(info(UpdateStatus.Downloading(42)), en))
    }

    @Test
    fun statusLineSaysReadyUntilTheNextVisitHome() {
        assertEquals("App-Update bereit, kommt beim nächsten Besuch der Startseite", status(info(UpdateStatus.Ready)))
        assertEquals(
            "App update ready, installs on the next visit to the home screen",
            status(info(UpdateStatus.Ready), en),
        )
    }

    @Test
    fun statusLineSaysNotAllowedWhenInstallsAreBlocked() {
        assertEquals("Installation nicht erlaubt", status(info(UpdateStatus.NotAllowed)))
        assertEquals("Installation not allowed", status(info(UpdateStatus.NotAllowed), en))
    }

    @Test
    fun statusLineNamesEachFailure() {
        for (table in listOf(de, en)) {
            val lines = Failure.entries.map { status(info(UpdateStatus.Failed(it)), table) }
            assertEquals(Failure.entries.size, lines.toSet().size)
        }
    }

    @Test
    fun statusLineNamesGitHubNotTheCatalogServer() {
        assertEquals("GitHub nicht erreichbar", de(General.updateFailure(Failure.UNREACHABLE)))
        assertEquals("Release auf GitHub ungültig", de(General.updateFailure(Failure.BAD_INDEX)))
        assertEquals("Cannot reach GitHub", en(General.updateFailure(Failure.UNREACHABLE)))
        assertEquals("Release on GitHub not valid", en(General.updateFailure(Failure.BAD_INDEX)))
    }

    @Test
    fun aFailedSelfUpdateSaysItDidNotInstall() {
        val failed = General.updateFailure(Failure.INSTALL_FAILED)
        assertEquals("Update nicht installiert", de(failed))
        assertEquals("Update did not install", en(failed))
    }

    @Test
    fun catalogFailuresStillNameTheServer() {
        assertEquals("Server nicht erreichbar", de(General.failure(Failure.UNREACHABLE)))
        assertEquals("Katalog ungültig", de(General.failure(Failure.BAD_INDEX)))
        assertEquals("Cannot reach the server", en(General.failure(Failure.UNREACHABLE)))
        assertEquals("Catalog not valid", en(General.failure(Failure.BAD_INDEX)))
    }

    @Test
    fun installFailuresInBothLanguages() {
        val texts = mapOf(
            Failure.BROKEN_APK to ("Update beschädigt" to "Update damaged"),
            Failure.OTHER_KEY to ("Update hat einen anderen Schlüssel" to "Update has a different signature"),
            Failure.INSTALL_FAILED to ("Installation fehlgeschlagen" to "Installation failed"),
            Failure.CANCELLED to ("Installation abgebrochen" to "Installation canceled"),
        )
        texts.forEach { (failure, text) ->
            assertEquals(text, de(General.failure(failure)) to en(General.failure(failure)))
        }
    }

    @Test
    fun aboutListsVersionCatalogServerAndLastCheck() {
        val at = LocalDate.of(2026, 11, 18).atTime(14, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals(
            listOf(
                "Version" to "1.0 (7)",
                "Katalog" to "3 vom 18.11.2026",
                "Server" to "tv.example",
                "Zuletzt geprüft" to "18.11. 14:05",
            ),
            about(GENERAL_INFO.copy(checkedAt = at)),
        )
        assertEquals(
            listOf(
                "Version" to "1.0 (7)",
                "Catalog" to "3 from Nov 18, 2026",
                "Server" to "tv.example",
                "Last check" to "Nov 18, 2:05 PM",
            ),
            about(GENERAL_INFO.copy(checkedAt = at), en),
        )
    }

    @Test
    fun aboutWithoutCheckOrServer() {
        val lines = about(GENERAL_INFO.copy(server = null)).toMap()
        assertEquals("keiner", lines["Server"])
        assertEquals("noch nicht", lines["Zuletzt geprüft"])
        val english = about(GENERAL_INFO.copy(server = null), en).toMap()
        assertEquals("none", english["Server"])
        assertEquals("not yet", english["Last check"])
    }

    @Test
    fun aboutWithoutCatalog() {
        assertEquals("keiner", about(info().copy(catalog = null)).toMap()["Katalog"])
        assertEquals("none", about(info().copy(catalog = null), en).toMap()["Catalog"])
    }
}
