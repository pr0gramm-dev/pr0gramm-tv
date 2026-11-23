package dev.pr0gramm.tv.app

import dev.pr0gramm.tv.data.manifest.SiteId
import dev.pr0gramm.tv.ui.launcher.settings.GeneralView
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherScreenTest {
    @Test
    fun everyPageComesBackFromItsKey() {
        val pages = listOf(LauncherScreen.Home, LauncherScreen.Settings(SiteId("nova"))) +
            GeneralView.entries.map { LauncherScreen.General(it) }
        pages.forEach { assertEquals(it, LauncherScreen.of(it.key)) }
    }

    @Test
    fun keysAreHomeGeneralAndSettings() {
        assertEquals("home", LauncherScreen.Home.key)
        assertEquals("general/LIST", LauncherScreen.General(GeneralView.LIST).key)
        assertEquals("settings/nova", LauncherScreen.Settings(SiteId("nova")).key)
    }

    @Test
    fun noOrAnUnknownKeyIsHome() {
        listOf(null, "", "nope", "general/", "general/NOPE", "settings/").forEach {
            assertEquals(it, LauncherScreen.Home, LauncherScreen.of(it))
        }
    }
}
