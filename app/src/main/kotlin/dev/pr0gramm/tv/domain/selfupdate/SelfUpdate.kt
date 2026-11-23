package dev.pr0gramm.tv.domain.selfupdate

import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.data.releases.GithubReleases
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The self-update as seen by the app: it reads the latest GitHub release and needs no server address (AC7). A checked
 * APK goes out as E2 on [events]; the end of its install comes back as E3 through [installEnded].
 */
interface SelfUpdate {
    val status: StateFlow<UpdateStatus>

    /** A check is running. */
    val checking: StateFlow<Boolean>

    /** When the last check ended (epoch ms); null before the first one. */
    val checkedAt: StateFlow<Long?>

    /**
     * E2: each checked APK exactly once, in order; an event waits in the buffer until it is collected. Only one
     * collector may collect it; a second one fails.
     */
    val events: Flow<SelfUpdateReady>

    /**
     * Starts a check unless one is running: the latest release from GitHub (asked at most hourly unless [force]),
     * then the decision on it. A newer one is downloaded, checked and sent on [events]. Call it from one thread (the
     * main thread).
     */
    fun check(force: Boolean = false)

    /**
     * E3: the install of the own app at [versionCode] ended; [failed] is true when it failed, not when cancelled.
     * A failed version is skipped by every check, also after a restart, until a newer one appears or [retry] runs.
     */
    fun installEnded(versionCode: Long, failed: Boolean)

    /**
     * The user tries the failed version again ("Try the update again"): the mark goes, and a forced check downloads
     * the version once more. The cause of the failure may be the device and not the version.
     */
    fun retry()

    companion object {
        /**
         * The update of [device] from [releases], checked in [scope]; [clock] (epoch ms) times the hourly rule.
         * [files] names where the APK and the failed version go.
         */
        fun create(
            releases: GithubReleases,
            device: PackageInstallerPort,
            scope: CoroutineScope,
            clock: () -> Long = System::currentTimeMillis,
            files: UpdateFiles = UpdateFiles(),
        ): SelfUpdate = GithubSelfUpdate(
            SelfUpdateCheck(releases, device, clock, files.work),
            scope,
            clock,
            files.failed?.let(::FailedUpdateFile),
        )
    }
}

/**
 * The files of the self-update: [work] makes the file a download goes to; [failed] keeps the failed version across
 * restarts, null keeps it in memory only (tests).
 */
class UpdateFiles(
    val work: () -> java.io.File = { java.io.File.createTempFile("update", ".apk") },
    val failed: java.io.File? = null,
)

private class GithubSelfUpdate(
    private val check: SelfUpdateCheck,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val failedStore: FailedUpdateFile?,
) : SelfUpdate {
    private val state = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    override val status: StateFlow<UpdateStatus> = state.asStateFlow()
    private val running = MutableStateFlow(false)
    override val checking: StateFlow<Boolean> = running.asStateFlow()
    private val ended = MutableStateFlow<Long?>(null)
    override val checkedAt: StateFlow<Long?> = ended.asStateFlow()
    private val buffer = Channel<SelfUpdateReady>(Channel.UNLIMITED)
    override val events: Flow<SelfUpdateReady> = buffer.consumeAsFlow()
    private val installs = MutableStateFlow(SelfInstallState(failedVersion = failedStore?.read()))
    private var job: Job? = null

    override fun check(force: Boolean) {
        if (job?.isActive == true) return
        running.value = true
        job = scope.launch {
            try {
                val checked = check.run(force, { installs.value }) { state.value = it }
                checked.handOff?.let { ready ->
                    // Marked before it goes out, so a second check does not download it again while E2 waits.
                    installs.update { it.handedOff(ready.versionCode) }
                    buffer.trySend(ready)
                }
                state.value = checked.status
                ended.value = clock()
            } catch (e: CancellationException) {
                state.value = UpdateStatus.Idle
                throw e
            } finally {
                running.value = false
            }
        }
    }

    override fun installEnded(versionCode: Long, failed: Boolean) {
        val next = installs.updateAndGet { it.ended(versionCode, failed) }
        failedStore?.write(next.failedVersion)
    }

    override fun retry() {
        if (job?.isActive == true) return
        installs.update { it.copy(failedVersion = null) }
        failedStore?.write(null)
        check(force = true)
    }
}
