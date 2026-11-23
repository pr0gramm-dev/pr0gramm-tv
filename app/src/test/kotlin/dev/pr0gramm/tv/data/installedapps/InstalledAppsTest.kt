package dev.pr0gramm.tv.data.installedapps

import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class InstalledAppsTest {
    private class FakeDevice(val versions: MutableMap<String, Long>) : PackageInstallerPort {
        override val packageName = "dev.pr0gramm.tv.app"
        override val versionCode = 5L
        override val updatable = true
        override val mayInstall = true

        override suspend fun install(apk: File, pkg: String): Failure? = null

        override fun installedVersion(pkg: String): Long? = versions[pkg]
    }

    @Test
    fun `installed versions are read per package and refreshed`() = runTest {
        val device = FakeDevice(versions = mutableMapOf("org.kodi" to 3L))
        val packages = InstalledApps(device, flowOf(listOf("org.kodi", "org.vlc")), backgroundScope)
        runCurrent()
        assertEquals(mapOf("org.kodi" to 3L), packages.versions.value)

        device.versions["org.vlc"] = 9
        device.versions.remove("org.kodi")
        packages.refresh()
        assertEquals(mapOf("org.vlc" to 9L), packages.versions.value)
    }

    @Test
    fun `a new catalog's apps are looked up at once`() = runTest {
        val device = FakeDevice(versions = mutableMapOf("org.kodi" to 3L, "org.mx" to 5L))
        val wanted = MutableStateFlow<Collection<String>>(listOf("org.kodi"))
        val packages = InstalledApps(device, wanted, backgroundScope)
        runCurrent()
        assertEquals(mapOf("org.kodi" to 3L), packages.versions.value)

        // The new catalog adds org.mx, which is installed already, and drops org.kodi.
        wanted.value = listOf("org.mx")
        runCurrent()
        assertEquals(mapOf("org.mx" to 5L), packages.versions.value)
    }
}
