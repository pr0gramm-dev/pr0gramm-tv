package dev.pr0gramm.tv.app

import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.ui.launcher.settings.GeneralView

/** Which page of the launcher is shown. */
internal sealed interface LauncherScreen {
    /** What `onSaveInstanceState` stores: "home", "general/LIST" or "settings/<id>" (AC7). */
    val key: String

    data object Home : LauncherScreen {
        override val key = HOME
    }

    data class Settings(val site: SiteId) : LauncherScreen {
        override val key get() = "$SETTINGS${site.value}"
    }

    /** The general settings: the list or the page behind one of its rows. */
    data class General(val view: GeneralView) : LauncherScreen {
        override val key get() = "$GENERAL${view.name}"
    }

    companion object {
        private const val HOME = "home"
        private const val SETTINGS = "settings/"
        private const val GENERAL = "general/"

        /** The page of [key]; no key or an unknown one is [Home]. */
        fun of(key: String?): LauncherScreen = when {
            key == null -> Home
            key.startsWith(SETTINGS) -> key.removePrefix(SETTINGS).takeIf { it.isNotEmpty() }
                ?.let { Settings(SiteId(it)) }
            key.startsWith(GENERAL) -> GeneralView.entries.firstOrNull { it.name == key.removePrefix(GENERAL) }
                ?.let(::General)
            else -> null
        } ?: Home
    }
}
