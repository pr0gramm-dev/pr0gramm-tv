package dev.pr0gramm.tv.domain.appinstall

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.data.catalogsync.ServerAddress
import dev.pr0gramm.tv.data.catalogsync.ServerClient
import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.ServerPath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class AppInstallerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val kodiBytes = "kodi bytes ".repeat(50).toByteArray()
    private val store = URI("https://tv.example/store/manifest.json")

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun kodi(sha256: String = sha(kodiBytes)) =
        CatalogApp("org.kodi", "Kodi", null, 3, Apk(ServerPath.of("kodi.apk")!!, sha256, kodiBytes.size.toLong()))

    private class FakeHttp(private val bytes: ByteArray) : Http {
        override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T =
            read(bytes.inputStream())
    }

    private class FakeDevice(
        var result: Failure? = null,
        override var mayInstall: Boolean = true,
        val versions: MutableMap<String, Long> = mutableMapOf(),
    ) : PackageInstallerPort {
        val installed = mutableListOf<Pair<String, ByteArray>>()
        override val packageName = "dev.pr0gramm.tv.app"
        override val versionCode = 5L
        override val updatable = true

        override suspend fun install(apk: File, pkg: String): Failure? {
            installed += pkg to apk.readBytes()
            return result
        }

        override fun installedVersion(pkg: String): Long? = versions[pkg]
    }

    private fun TestScope.installs(device: FakeDevice, bytes: ByteArray = kodiBytes) =
        AppInstaller(server(FakeHttp(bytes)), device, backgroundScope) { tmp.newFile() }

    private fun server(http: FakeHttp) = ServerClient(ServerAddress.parse(store.toString())!!, http) { "en" }

    private suspend fun AppInstaller.settled() = queue.first { it.running == null && it.waiting.isEmpty() }

    @Test
    fun `an app is installed under its own package`() = runTest {
        val device = FakeDevice()
        val installs = installs(device)
        assertEquals(Request.ACCEPTED, installs.request(kodi()))
        installs.settled()
        val (pkg, bytes) = device.installed.single()
        assertEquals("org.kodi", pkg)
        assertTrue(kodiBytes.contentEquals(bytes))
        assertTrue(installs.queue.value.failed.isEmpty())
    }

    @Test
    fun `a wrong hash is an error and nothing is installed`() = runTest {
        val device = FakeDevice()
        val installs = installs(device)
        installs.request(kodi(sha256 = "0".repeat(64)))
        val queue = installs.settled()
        assertEquals(mapOf("org.kodi" to 3L), queue.failed)
        assertTrue(device.installed.isEmpty())
    }

    @Test
    fun `cancelling in Android leaves no error and clears an earlier one`() = runTest {
        val device = FakeDevice(result = Failure.INSTALL_FAILED)
        val installs = installs(device)
        installs.request(kodi())
        assertEquals(mapOf("org.kodi" to 3L), installs.settled().failed)

        device.result = Failure.CANCELLED
        installs.request(kodi())
        val queue = installs.settled()
        assertTrue(queue.failed.isEmpty())
        assertNull(queue.running)
        assertEquals(2, device.installed.size)
    }

    @Test
    fun `without the permission the request needs it and installs nothing`() = runTest {
        val device = FakeDevice(mayInstall = false)
        val installs = installs(device)
        assertEquals(Request.NEEDS_PERMISSION, installs.request(kodi()))
        assertEquals("org.kodi", installs.queue.value.awaitingPermission?.pkg)
        assertTrue(device.installed.isEmpty())
    }

    @Test
    fun `the own update is installed under the own package once the start page is in front`() = runTest {
        val device = FakeDevice()
        val installs = installs(device)
        val apk = tmp.newFile().apply { writeBytes(kodiBytes) }
        installs.selfReady(7, apk)
        val ready = installs.queue.value.ready
        assertEquals("dev.pr0gramm.tv.app", ready?.pkg)
        assertEquals(7L, ready?.versionCode)

        installs.foreground(true)
        installs.queue.first { it.ready == null && it.running == null }
        assertEquals("dev.pr0gramm.tv.app", device.installed.single().first)
    }

    @Test
    fun `finishing an install reports package and outcome`() = runTest {
        val seen = mutableListOf<Pair<String, InstallOutcome>>()
        val installs = installs(FakeDevice())
        installs.request(kodi())
        installs.settled()
        installs.events.take(1).collect { event ->
            when (event) {
                is InstallEvent.Finished -> seen += event.job.pkg to event.outcome
            }
        }
        assertEquals(listOf("org.kodi" to InstallOutcome.INSTALLED), seen)
    }
}
