package dev.pr0gramm.tv.domain.selfupdate

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.core.http.HttpFailure
import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.data.releases.GithubReleases
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

/** The self-update over a real [GithubReleases]; HTTP, the device and the clock are the only fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class SelfUpdateTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
    }

    private val apk = "apk bytes ".repeat(100).toByteArray()

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val api = URI("https://api.github.com/repos/owner/repo/releases/latest")

    private fun asset(tag: String) = URI("https://github.com/owner/repo/releases/download/$tag/pr0gramm-tv.apk")

    /** The answer of the GitHub API for the release [tag] with the asset `pr0gramm-tv.apk`. */
    private fun release(tag: String = "v0.0.6", sha256: String = sha(apk)) = """
        {"tag_name": "$tag", "assets": [{"name": "pr0gramm-tv.apk", "size": ${apk.size},
         "digest": "sha256:$sha256", "browser_download_url": "${asset(tag)}"}]}
    """.trimIndent().toByteArray()

    /** Answers by URL: bytes for 200, an HTTP code or an [IOException] to throw, else 404. Records every request. */
    private class FakeHttp(val responses: MutableMap<URI, Any>) : Http {
        val requests = mutableListOf<URI>()

        override suspend fun <T> get(url: URI, headers: Map<String, String>, read: (InputStream) -> T): T {
            requests += url
            return when (val response = responses[url] ?: 404) {
                is ByteArray -> read(response.inputStream())
                is Int -> throw HttpFailure(response)
                is IOException -> throw response
                else -> error("bad fake response $response")
            }
        }
    }

    private class FakeDevice : PackageInstallerPort {
        override val packageName = "dev.pr0gramm.tv.app"
        override val versionCode = 5_000L // 0.0.5
        override var updatable = true
        override var mayInstall = true

        override fun installedVersion(pkg: String): Long? = null

        override suspend fun install(apk: File, pkg: String): Failure? = error("the self-update never installs")
    }

    private val files = mutableMapOf<URI, Any>(api to release(), asset("v0.0.6") to apk)

    private var now = 0L

    /** A self-update over [http] and [device] in [scope]; [now] is its clock. */
    private inner class Setup(
        val http: FakeHttp,
        val device: FakeDevice,
        scope: CoroutineScope,
        failedFile: File? = null,
    ) {
        val updates = SelfUpdate.create(
            GithubReleases("owner/repo", http),
            device,
            scope,
            clock = { now },
            files = UpdateFiles(failed = failedFile),
        )

        fun asked() = http.requests.count { it == api }

        fun downloads(tag: String = "v0.0.6") = http.requests.count { it == asset(tag) }
    }

    private fun TestScope.setup(
        http: FakeHttp = FakeHttp(files),
        device: FakeDevice = FakeDevice(),
        failedFile: File? = null,
    ) = Setup(http, device, backgroundScope, failedFile)

    /** Runs one check to its end and returns its status. */
    private suspend fun checked(s: Setup, force: Boolean = false): UpdateStatus {
        s.updates.check(force)
        s.updates.checking.first { !it }
        return s.updates.status.value
    }

    @Test
    fun `a newer release is downloaded, checked and handed off`() = runTest {
        val s = setup()
        assertEquals(UpdateStatus.Ready, checked(s))
        val ready = s.updates.events.first()
        assertEquals(6_000L, ready.versionCode)
        assertEquals(apk.toList(), ready.apk.readBytes().toList())
    }

    @Test
    fun `GitHub is asked at most hourly unless forced`() = runTest {
        val s = setup(device = FakeDevice().apply { updatable = false })
        checked(s)
        now = HOUR_MS - 1
        checked(s)
        assertEquals(1, s.asked())
        checked(s, force = true)
        assertEquals(2, s.asked())
        now += HOUR_MS
        checked(s)
        assertEquals(3, s.asked())
    }

    @Test
    fun `an unreachable GitHub is asked again at the next check`() = runTest {
        val s = setup(http = FakeHttp(files.apply { put(api, 403) }))
        assertEquals(UpdateStatus.Failed(Failure.UNREACHABLE), checked(s))
        s.http.responses[api] = release()
        assertEquals(UpdateStatus.Ready, checked(s))
        assertEquals(2, s.asked())
    }

    @Test
    fun `a release without the asset is BAD_INDEX`() = runTest {
        val s = setup(http = FakeHttp(mutableMapOf(api to """{"tag_name": "v0.0.6", "assets": []}""".toByteArray())))
        assertEquals(UpdateStatus.Failed(Failure.BAD_INDEX), checked(s))
    }

    @Test
    fun `a BAD_INDEX answer is kept for the hour, unless forced`() = runTest {
        val s = setup(http = FakeHttp(mutableMapOf(api to """{"tag_name": "v0.0.6", "assets": []}""".toByteArray())))
        assertEquals(UpdateStatus.Failed(Failure.BAD_INDEX), checked(s))
        now = HOUR_MS - 1
        assertEquals(UpdateStatus.Failed(Failure.BAD_INDEX), checked(s))
        assertEquals(1, s.asked())
        assertEquals(UpdateStatus.Failed(Failure.BAD_INDEX), checked(s, force = true))
        assertEquals(2, s.asked())
    }

    @Test
    fun `a tag that is no version is ignored`() = runTest {
        val s = setup(http = FakeHttp(files.apply { put(api, release(tag = "nightly")) }))
        assertEquals(UpdateStatus.UpToDate, checked(s))
        assertEquals(0, s.downloads())
    }

    @Test
    fun `a build without release key is DeployOnly and downloads nothing`() = runTest {
        val s = setup(device = FakeDevice().apply { updatable = false })
        assertEquals(UpdateStatus.DeployOnly, checked(s))
        assertEquals(0, s.downloads())
    }

    @Test
    fun `NotAllowed downloads once the permission is there, without asking GitHub again`() = runTest {
        val device = FakeDevice().apply { mayInstall = false }
        val s = setup(device = device)
        assertEquals(UpdateStatus.NotAllowed, checked(s))
        assertEquals(0, s.downloads())

        device.mayInstall = true
        assertEquals(UpdateStatus.Ready, checked(s))
        assertEquals(1, s.asked())
        assertEquals(1, s.downloads())
    }

    @Test
    fun `a ready version is not downloaded again`() = runTest {
        val s = setup()
        checked(s)
        assertEquals(UpdateStatus.Ready, checked(s, force = true))
        assertEquals(1, s.downloads())
    }

    @Test
    fun `a version that failed to install is skipped by every check until a newer one appears`() = runTest {
        val s = setup()
        checked(s)
        s.updates.installEnded(6_000, failed = true)
        assertEquals(UpdateStatus.Failed(Failure.INSTALL_FAILED), checked(s, force = true))
        assertEquals(1, s.downloads())

        s.http.responses[api] = release(tag = "v0.0.7")
        s.http.responses[asset("v0.0.7")] = apk
        assertEquals(UpdateStatus.Ready, checked(s, force = true))
        assertEquals(1, s.downloads("v0.0.7"))
    }

    @Test
    fun `retry downloads the failed version again, the cause may be the device`() = runTest {
        val file = File(tmp.root, "self-update-failed")
        val s = setup(failedFile = file)
        checked(s)
        s.updates.installEnded(6_000, failed = true)
        s.updates.retry()
        s.updates.checking.first { !it }
        assertEquals(UpdateStatus.Ready, s.updates.status.value)
        assertEquals(2, s.downloads())
        assertEquals(false, file.exists())
    }

    @Test
    fun `the failed version is still skipped after a restart`() = runTest {
        val file = File(tmp.root, "self-update-failed")
        val first = setup(failedFile = file)
        checked(first)
        first.updates.installEnded(6_000, failed = true)
        assertEquals("6000", file.readText())

        val restarted = setup(failedFile = file)
        assertEquals(UpdateStatus.Failed(Failure.INSTALL_FAILED), checked(restarted))
        assertEquals(0, restarted.downloads())
    }

    @Test
    fun `a successful install clears the mark`() = runTest {
        val file = File(tmp.root, "self-update-failed").apply { writeText("6000") }
        val s = setup(failedFile = file)
        s.updates.installEnded(6_000, failed = false)
        assertEquals(false, file.exists())
    }

    @Test
    fun `a broken apk asks GitHub once more`() = runTest {
        val s = setup(http = FakeHttp(files.apply { put(asset("v0.0.6"), "other".toByteArray()) }))
        assertEquals(UpdateStatus.Failed(Failure.BROKEN_APK), checked(s))
        assertEquals(listOf(api, asset("v0.0.6"), api, asset("v0.0.6")), s.http.requests)
    }
}
