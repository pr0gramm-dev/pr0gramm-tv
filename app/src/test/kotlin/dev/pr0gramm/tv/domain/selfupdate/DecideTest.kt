package dev.pr0gramm.tv.domain.selfupdate

import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DecideTest {
    private class FakeDevice(
        override val versionCode: Long = 5,
        override val updatable: Boolean = true,
        override val mayInstall: Boolean = true,
        override val packageName: String = "dev.pr0gramm.tv.app",
    ) : PackageInstallerPort {
        override fun installedVersion(pkg: String): Long? = error("decide never asks")
        override suspend fun install(apk: File, pkg: String): Failure? = error("decide never installs")
    }

    @Test
    fun `newer version is downloaded`() {
        assertEquals(UpdateStatus.Downloading(0), decide(6, FakeDevice(versionCode = 5), null))
    }

    @Test
    fun `same or older version is up to date`() {
        assertEquals(UpdateStatus.UpToDate, decide(5, FakeDevice(versionCode = 5), null))
        assertEquals(UpdateStatus.UpToDate, decide(4, FakeDevice(versionCode = 5), null))
    }

    @Test
    fun `skipped version keeps its status, a newer one is tried again`() {
        val skipped = 6L to UpdateStatus.Failed(Failure.INSTALL_FAILED)
        assertEquals(skipped.second, decide(6, FakeDevice(), skipped))
        assertEquals(UpdateStatus.Downloading(0), decide(7, FakeDevice(), skipped))
    }

    @Test
    fun `build without release key is DeployOnly`() {
        assertEquals(UpdateStatus.DeployOnly, decide(6, FakeDevice(updatable = false), null))
    }

    @Test
    fun `missing install permission is NotAllowed`() {
        assertEquals(UpdateStatus.NotAllowed, decide(6, FakeDevice(mayInstall = false), null))
    }
}
