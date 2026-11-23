package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.ServerPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAppsTest {
    private val kodi = CatalogApp(
        "org.kodi",
        "org.kodi",
        null,
        2,
        Apk(ServerPath.of("org.kodi.apk")!!, "ab".repeat(32), 10),
    )

    @Test
    fun menuAndPrimaryOfApps() {
        fun tile(state: AppState) = Tile.App(kodi, state)
        assertEquals(Action.Install(kodi), Home.primary(tile(AppState.Missing)))
        // A short OK on a missing or failed app opens its menu; Installieren is the first entry there.
        assertTrue(Home.opensMenu(tile(AppState.Missing)))
        assertTrue(Home.opensMenu(tile(AppState.Failed)))
        assertFalse(Home.opensMenu(tile(AppState.Installed)))
        assertFalse(Home.opensMenu(tile(AppState.Update)))
        assertEquals(listOf(Action.Install(kodi)), Home.menu(tile(AppState.Failed)))
        assertEquals(Action.Launch("org.kodi"), Home.primary(tile(AppState.Installed)))
        assertEquals(listOf(Action.Launch("org.kodi")), Home.menu(tile(AppState.Installed)))
        assertEquals(
            listOf(Action.Install(kodi, update = true), Action.Launch("org.kodi")),
            Home.menu(tile(AppState.Update)),
        )
        // OK does nothing while the app waits or loads.
        assertNull(Home.primary(tile(AppState.Waiting)))
        assertNull(Home.primary(tile(AppState.Running(3))))
        assertTrue(Home.menu(tile(AppState.Waiting)).isEmpty())
    }
}
