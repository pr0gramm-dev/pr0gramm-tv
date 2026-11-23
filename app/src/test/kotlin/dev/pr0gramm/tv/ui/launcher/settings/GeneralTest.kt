package dev.pr0gramm.tv.ui.launcher.settings

import dev.pr0gramm.tv.core.text.StringTable
import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.data.appsettings.LanguageChoice
import dev.pr0gramm.tv.data.appsettings.PointerLook
import dev.pr0gramm.tv.data.catalogsync.ServerAddress
import dev.pr0gramm.tv.domain.selfupdate.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rows of the general settings page, their help and what OK does on them. */
class GeneralTest {
    private val de = Strings.de
    private val en = Strings.en

    private fun info(
        status: UpdateStatus = UpdateStatus.Idle,
        checking: Boolean = false,
        server: ServerAddress? = ServerAddress.parse("tv.example"),
        pointer: PointerLook = PointerLook.HAND,
        language: LanguageChoice = LanguageChoice.SYSTEM,
    ) = GENERAL_INFO.copy(
        status = status,
        checking = checking,
        server = server,
        pointer = pointer,
        language = language,
    )

    @Test
    fun rowsAreCheckServerPointerLanguageControlsAbout() {
        val rows = General.rows(info())
        assertEquals(
            listOf(
                GeneralItem.CHECK,
                GeneralItem.SERVER,
                GeneralItem.POINTER,
                GeneralItem.LANGUAGE,
                GeneralItem.CONTROLS,
                GeneralItem.ABOUT,
            ),
            rows.map { it.item },
        )
        assertEquals(
            listOf("Nach Updates suchen", "Server", "Aussehen der Maus", "Sprache", "Bedienung", "Über die App"),
            rows.map { de(it.label) },
        )
        assertEquals(
            listOf("Check for updates", "Server", "Pointer look", "Language", "Remote keys", "About the app"),
            rows.map { en(it.label) },
        )
    }

    @Test
    fun okMapsEachRowToItsPress() {
        val presses = GeneralItem.entries.associateWith { General.press(it, info()) }
        assertEquals(
            mapOf(
                GeneralItem.CHECK to GeneralPress.CHECK,
                GeneralItem.SERVER to GeneralPress.OPEN_SERVER,
                GeneralItem.POINTER to GeneralPress.SWITCH_POINTER,
                GeneralItem.LANGUAGE to GeneralPress.SWITCH_LANGUAGE,
                GeneralItem.CONTROLS to GeneralPress.OPEN_CONTROLS,
                GeneralItem.ABOUT to GeneralPress.OPEN_ABOUT,
            ),
            presses,
        )
    }

    @Test
    fun languageRowShowsTheChoiceAndOkSwitchesItEvenWhileChecking() {
        fun value(choice: LanguageChoice, table: StringTable) =
            table(General.rows(info(language = choice)).single { it.item == GeneralItem.LANGUAGE }.value)
        assertEquals("Systemsprache", value(LanguageChoice.SYSTEM, de))
        assertEquals("System language", value(LanguageChoice.SYSTEM, en))
        // The language names stay in their own language (AC5).
        for (table in listOf(de, en)) {
            assertEquals("English", value(LanguageChoice.ENGLISH, table))
            assertEquals("Deutsch", value(LanguageChoice.GERMAN, table))
        }
        assertEquals(GeneralPress.SWITCH_LANGUAGE, General.press(GeneralItem.LANGUAGE, info(checking = true)))
        assertEquals(GeneralPress.SWITCH_LANGUAGE, General.press(GeneralItem.LANGUAGE, info(UpdateStatus.NotAllowed)))
    }

    @Test
    fun languageHelpNamesEveryChoiceAndMarksTheActiveOne() {
        fun help(choice: LanguageChoice) =
            General.rows(info(language = choice)).single { it.item == GeneralItem.LANGUAGE }.help
        assertEquals(listOf(true, false, false), help(LanguageChoice.SYSTEM).options.map { it.active })
        assertEquals(listOf(false, true, false), help(LanguageChoice.ENGLISH).options.map { it.active })
        assertEquals(listOf(false, false, true), help(LanguageChoice.GERMAN).options.map { it.active })
        val names = help(LanguageChoice.SYSTEM).options.map { it.name }
        assertEquals(listOf("Systemsprache", "English", "Deutsch"), names.map(Strings.de::invoke))
        assertEquals(listOf("System language", "English", "Deutsch"), names.map(Strings.en::invoke))
        val german = de.words(help(LanguageChoice.SYSTEM))
        assertTrue(german, german.startsWith("Die Sprache aller Texte der App."))
        assertTrue(german, "Alle Texte der App auf Deutsch." in german)
        assertTrue(german, german.endsWith("Texte aus dem Katalog ändern sich nicht."))
        val english = en.words(help(LanguageChoice.SYSTEM))
        assertTrue(english, english.startsWith("The language of all app texts. Press OK to set the next language."))
        assertTrue(english, "If the app does not have that language, the app uses English." in english)
        assertTrue(english, "All app texts in German." in english)
        val note = "The app asks sites for the app language. Texts from the catalog do not change."
        assertTrue(english, english.endsWith(note))
    }

    @Test
    fun serverRowShowsTheAddressAsTyped() {
        val path = ServerAddress.parse("https://Example.org/tv/manifest.json")
        fun value(server: ServerAddress?, table: StringTable = de) =
            table(General.rows(info(server = server)).single { it.item == GeneralItem.SERVER }.value)
        assertEquals("tv.example", value(info().server))
        assertEquals("tv.example", value(info().server, en))
        assertEquals("example.org/tv", value(path))
        assertEquals("keiner", value(null))
        assertEquals("none", value(null, en))
    }

    @Test
    fun okOnServerOpensTheServerDialogEvenWhileChecking() {
        assertEquals(GeneralPress.OPEN_SERVER, General.press(GeneralItem.SERVER, info()))
        assertEquals(GeneralPress.OPEN_SERVER, General.press(GeneralItem.SERVER, info(checking = true)))
        assertEquals(GeneralPress.OPEN_SERVER, General.press(GeneralItem.SERVER, info(UpdateStatus.NotAllowed)))
    }

    @Test
    fun serverHelpSaysTheCatalogComesFromThereAndTheAppRestarts() {
        val help = General.rows(info()).single { it.item == GeneralItem.SERVER }.help
        val de = Strings.de.words(help)
        assertTrue(de, "Katalog" in de)
        assertTrue(de, "neu" in de)
        val en = Strings.en.words(help)
        assertTrue(en, "catalog" in en)
        assertTrue(en, "the app restarts" in en)
    }

    @Test
    fun controlsRowOpensTheKeyPageAndSaysTheMainKeys() {
        val row = General.rows(info()).single { it.item == GeneralItem.CONTROLS }
        assertEquals(UiText.Plain("›"), row.value)
        val de = Strings.de.words(row.help)
        assertTrue(de, de.contains("Zahnrad"))
        assertTrue(de, de.contains("Zurück halten"))
        val en = Strings.en.words(row.help)
        assertTrue(en, en.startsWith("Shows what each key does"))
        assertTrue(en, en.contains("The basics Gear opens the menu"))
        assertTrue(en, en.contains("Hold Back to go to the home screen."))
        assertEquals(GeneralPress.OPEN_CONTROLS, General.press(GeneralItem.CONTROLS, info(checking = true)))
    }

    @Test
    fun pointerRowShowsTheLookAndOkSwitchesIt() {
        fun row(look: PointerLook) = General.rows(info(pointer = look)).single { it.item == GeneralItem.POINTER }
        assertEquals("Hand", de(row(PointerLook.HAND).value))
        assertEquals("Leuchtpunkt", de(row(PointerLook.GLOW).value))
        assertEquals("Hand", en(row(PointerLook.HAND).value))
        assertEquals("Bright dot", en(row(PointerLook.GLOW).value))
        assertEquals(listOf(true, false), row(PointerLook.HAND).help.options.map { it.active })
        assertEquals(listOf(false, true), row(PointerLook.GLOW).help.options.map { it.active })
        val names = row(PointerLook.HAND).help.options.map { it.name }
        assertEquals(listOf("Hand", "Leuchtpunkt"), names.map(Strings.de::invoke))
        assertEquals(listOf("Hand", "Bright dot"), names.map(Strings.en::invoke))
        assertEquals(GeneralPress.SWITCH_POINTER, General.press(GeneralItem.POINTER, info(checking = true)))
    }

    @Test
    fun everyRowHasHelp() {
        for (table in listOf(Strings.de, Strings.en)) {
            assertTrue(General.rows(info()).all { table.words(it.help).isNotBlank() })
        }
    }

    @Test
    fun checkHelpIsAboutTheAppOnly() {
        val help = General.rows(info()).first().help
        assertEquals(emptyList<HelpOption>(), help.options)
        val de = Strings.de.words(help)
        assertTrue(de, "GitHub" in de)
        assertTrue(de, "Katalog" !in de)
        val en = Strings.en.words(help)
        assertTrue(en, "GitHub" in en)
        assertTrue(en, "catalog" !in en)
    }

    @Test
    fun okOnCheckAsksAgainWithForce() {
        assertEquals(GeneralPress.CHECK, General.press(GeneralItem.CHECK, info(UpdateStatus.UpToDate)))
    }

    @Test
    fun okOnCheckWhileCheckingDoesNothing() {
        assertEquals(GeneralPress.NOTHING, General.press(GeneralItem.CHECK, info(checking = true)))
    }

    @Test
    fun okOnCheckWhenNotAllowedJumpsToTheAndroidSetting() {
        assertEquals(GeneralPress.ALLOW_INSTALLS, General.press(GeneralItem.CHECK, info(UpdateStatus.NotAllowed)))
    }

    @Test
    fun afterAFailedInstallTheRowTriesTheUpdateAgain() {
        val failed = info(UpdateStatus.Failed(Failure.INSTALL_FAILED))
        assertEquals(GeneralPress.RETRY_UPDATE, General.press(GeneralItem.CHECK, failed))
        val row = General.rows(failed).first { it.item == GeneralItem.CHECK }
        assertEquals("Update erneut versuchen", Strings.de(row.label))
        assertEquals("Try the update again", Strings.en(row.label))
        assertEquals(GeneralPress.NOTHING, General.press(GeneralItem.CHECK, info(failed.status, checking = true)))
    }

    @Test
    fun okOnCheckAsksInEveryStatusButNotAllowed() {
        val asks = listOf(UpdateStatus.Idle, UpdateStatus.DeployOnly, UpdateStatus.Failed(Failure.UNREACHABLE))
        assertTrue(asks.all { General.press(GeneralItem.CHECK, info(it)) == GeneralPress.CHECK })
    }

    @Test
    fun okOnAboutOpensTheDetails() {
        assertEquals(GeneralPress.OPEN_ABOUT, General.press(GeneralItem.ABOUT, info()))
    }
}
