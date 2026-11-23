package dev.pr0gramm.tv.ui.launcher.settings

import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.data.appsettings.LanguageChoice
import dev.pr0gramm.tv.data.appsettings.PointerLook
import dev.pr0gramm.tv.data.catalogsync.ServerAddress
import dev.pr0gramm.tv.domain.selfupdate.UpdateStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class GeneralItem { CHECK, SERVER, POINTER, LANGUAGE, CONTROLS, ABOUT }

/** What the general settings page shows: the list, or one of the pages behind a row. */
enum class GeneralView { LIST, ABOUT, CONTROLS }

data class GeneralRow(val item: GeneralItem, val label: UiText, val value: UiText, val help: Help = Help())

/** What OK does on a row. */
enum class GeneralPress {
    CHECK,
    RETRY_UPDATE,
    ALLOW_INSTALLS,
    OPEN_SERVER,
    SWITCH_POINTER,
    SWITCH_LANGUAGE,
    OPEN_CONTROLS,
    OPEN_ABOUT,
    NOTHING,
}

/** Version and date of the shown catalog. */
data class CatalogStamp(val version: Int, val date: LocalDate)

/**
 * Everything the general settings page shows. [catalog] is null while no catalog is there; [server] is the saved
 * server address, null without one. [language] is the app language that `LocaleManager` has now.
 */
data class GeneralInfo(
    val status: UpdateStatus,
    val checking: Boolean,
    val appVersion: String,
    val catalog: CatalogStamp?,
    val server: ServerAddress?,
    val checkedAt: Long?,
    val zone: ZoneId = ZoneId.systemDefault(),
    val pointer: PointerLook = PointerLook.HAND,
    val language: LanguageChoice = LanguageChoice.SYSTEM,
)

/** The name of a pointer look in the settings: row value and help option name (AC9b). */
internal val PointerLook.label: UiText
    get() = when (this) {
        PointerLook.HAND -> text(R.string.pointer_hand)
        PointerLook.GLOW -> text(R.string.pointer_glow)
    }

/**
 * The name of an app language: row value and help option name. A language keeps its own name
 * ([LanguageChoice.ownName]) in every app language (AC5).
 */
internal val LanguageChoice.shown: UiText
    get() = ownName?.let(UiText::Plain) ?: text(R.string.language_system)

/** The own update did not install: every check skips that version until the user tries it again. */
private val GeneralInfo.installFailed: Boolean
    get() = status == UpdateStatus.Failed(Failure.INSTALL_FAILED)

/** "Check for updates", or "Try the update again" after a failed install, while no check runs. */
private fun checkLabel(info: GeneralInfo): UiText =
    text(if (!info.checking && info.installFailed) R.string.general_retry else R.string.general_check)

/** The rows of the general settings page (AC10) and what OK does on them, and the status texts. Pure. */
object General {
    /** The value of a row that opens a page. */
    private val MORE = UiText.Plain("›")

    fun rows(info: GeneralInfo): List<GeneralRow> = listOf(
        GeneralRow(GeneralItem.CHECK, checkLabel(info), statusLine(info)),
        GeneralRow(GeneralItem.SERVER, text(R.string.general_server), serverName(info.server)),
        GeneralRow(GeneralItem.POINTER, text(R.string.general_pointer), info.pointer.label),
        GeneralRow(GeneralItem.LANGUAGE, text(R.string.general_language), info.language.shown),
        GeneralRow(GeneralItem.CONTROLS, text(R.string.general_controls), MORE),
        GeneralRow(GeneralItem.ABOUT, text(R.string.general_about_title), MORE),
    ).map { it.copy(help = SettingsHelp.general(it.item, info.pointer, info.language)) }

    fun press(item: GeneralItem, info: GeneralInfo): GeneralPress = when {
        item == GeneralItem.ABOUT -> GeneralPress.OPEN_ABOUT
        item == GeneralItem.SERVER -> GeneralPress.OPEN_SERVER
        item == GeneralItem.CONTROLS -> GeneralPress.OPEN_CONTROLS
        item == GeneralItem.POINTER -> GeneralPress.SWITCH_POINTER
        item == GeneralItem.LANGUAGE -> GeneralPress.SWITCH_LANGUAGE
        info.checking -> GeneralPress.NOTHING
        info.status == UpdateStatus.NotAllowed -> GeneralPress.ALLOW_INSTALLS
        info.installFailed -> GeneralPress.RETRY_UPDATE
        else -> GeneralPress.CHECK
    }

    /** The lines under "About the app": label to value; dates follow `format_locale` (AC10). */
    fun about(info: GeneralInfo): List<Pair<UiText, UiText>> = listOf(
        text(R.string.general_about_version) to UiText.Plain(info.appVersion),
        text(R.string.general_about_catalog) to catalog(info.catalog),
        text(R.string.general_server) to serverName(info.server),
        text(R.string.general_about_checked) to checked(info),
    )

    private fun catalog(stamp: CatalogStamp?): UiText = stamp
        ?.let { text(R.string.general_about_catalog_value, it.version, UiText.Date(R.string.date_full, it.date)) }
        ?: text(R.string.general_none)

    private fun checked(info: GeneralInfo): UiText = info.checkedAt
        ?.let { UiText.Date(R.string.date_day_time, Instant.ofEpochMilli(it).atZone(info.zone)) }
        ?: text(R.string.general_not_yet)

    /** The address as the user types it, or "none" without one. */
    private fun serverName(server: ServerAddress?): UiText =
        server?.let { UiText.Plain(it.shown) } ?: text(R.string.general_none)

    /** The state of the app update only; the catalog has its own refresh. */
    private fun statusLine(info: GeneralInfo): UiText = when {
        info.checking -> text(R.string.general_checking)
        else -> when (val status = info.status) {
            UpdateStatus.Idle -> text(R.string.general_version, info.appVersion)
            UpdateStatus.UpToDate -> text(R.string.general_up_to_date, info.appVersion)
            is UpdateStatus.Downloading -> text(R.string.general_downloading, status.percent)
            UpdateStatus.Ready -> text(R.string.general_ready)
            UpdateStatus.DeployOnly -> text(R.string.general_deploy_only)
            UpdateStatus.NotAllowed -> text(R.string.general_not_allowed)
            is UpdateStatus.Failed -> updateFailure(status.failure)
        }
    }

    /**
     * The status text of a self-update [failure]: the release comes from GitHub. A failed install says that the
     * update did not install; the row is then "Try the update again" ([checkLabel]).
     */
    internal fun updateFailure(failure: Failure): UiText = when (failure) {
        Failure.UNREACHABLE -> text(R.string.failure_github_unreachable)
        Failure.BAD_INDEX -> text(R.string.failure_bad_release)
        Failure.INSTALL_FAILED -> text(R.string.general_install_failed)
        else -> failure(failure)
    }

    /** The status text of a catalog or install [failure]: the catalog comes from the server. */
    internal fun failure(failure: Failure): UiText = text(
        when (failure) {
            Failure.UNREACHABLE -> R.string.failure_server_unreachable
            Failure.BAD_INDEX -> R.string.failure_bad_catalog
            Failure.BROKEN_APK -> R.string.failure_broken_apk
            Failure.OTHER_KEY -> R.string.failure_other_key
            Failure.INSTALL_FAILED -> R.string.failure_install_failed
            Failure.CANCELLED -> R.string.failure_cancelled
        },
    )
}
