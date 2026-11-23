package dev.pr0gramm.tv.domain.appinstall

import dev.pr0gramm.tv.core.packageinstaller.PackageInstallerPort
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.core.updatefailure.UpdateFailure
import dev.pr0gramm.tv.data.catalogsync.ServerClient
import dev.pr0gramm.tv.data.manifest.CatalogApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** The answer to [AppInstaller.request]. */
enum class Request { ACCEPTED, NEEDS_PERMISSION }

/**
 * The only writer of [queue]; one coroutine in its scope works it off, one install at a time. An app is downloaded
 * from [server] (size and SHA-256 checked) when its turn comes; without a server (no saved address) it fails. A
 * cancel in Android is no error. Every end goes out once as [InstallEvent.Finished] on [events]. The own update
 * arrives checked through [selfReady].
 */
class AppInstaller(
    private val server: ServerClient?,
    private val device: PackageInstallerPort,
    scope: CoroutineScope,
    private val workFile: () -> File = { File.createTempFile("install", ".apk") },
) {
    private val state = MutableStateFlow(InstallQueue())

    /** The queue to read; only the named steps of this class change it. */
    val queue: StateFlow<InstallQueue> = state.asStateFlow()

    private val buffer = Channel<InstallEvent>(Channel.UNLIMITED)

    /**
     * Each event exactly once, in order; an event waits in the buffer until it is collected. Only one collector may
     * collect it; a second one fails.
     */
    val events: Flow<InstallEvent> = buffer.consumeAsFlow()

    /** Wakes the worker after a change that may let the next install start. */
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var foreground = false

    init {
        scope.launch { work() }
    }

    /** OK on [app]: queues it; nothing when it already waits or runs. */
    fun request(app: CatalogApp): Request {
        val after = change { it.request(InstallJob.App(app), device.mayInstall) }
        return if (after.awaitingPermission?.pkg == app.pkg) Request.NEEDS_PERMISSION else Request.ACCEPTED
    }

    /**
     * The start page came to the front ([on] true) or left it. In front, a job that waited for the permission goes
     * on if the user granted it, and a ready own update may start.
     */
    fun foreground(on: Boolean) {
        foreground = on
        if (on) change { it.permission(device.mayInstall) }
    }

    /** The own update at [versionCode], downloaded and checked to [apk], waits for the start page. */
    fun selfReady(versionCode: Long, apk: File) {
        var replaced: File? = null
        change { queue ->
            replaced = queue.ready?.apk
            queue.selfReady(InstallJob.Self(device.packageName, versionCode, apk))
        }
        replaced?.takeIf { it != apk }?.delete()
    }

    private fun change(transform: (InstallQueue) -> InstallQueue): InstallQueue =
        state.updateAndGet(transform).also { wake.trySend(Unit) }

    private suspend fun work() {
        while (true) {
            val job = state.updateAndGet { it.next(foreground) }.running
            if (job == null) {
                wake.receive()
            } else {
                val outcome = install(job)
                state.update { it.finished(job.pkg, outcome) }
                buffer.trySend(InstallEvent.Finished(job, outcome))
            }
        }
    }

    private suspend fun install(job: InstallJob): InstallOutcome {
        var file: File? = (job as? InstallJob.Self)?.apk
        return try {
            if (job is InstallJob.App) file = download(job.app)
            when {
                file == null -> InstallOutcome.FAILED
                else -> outcomeOf(device.install(file, job.pkg))
            }
        } catch (_: UpdateFailure) {
            InstallOutcome.FAILED
        } catch (_: IOException) {
            InstallOutcome.FAILED
        } finally {
            file?.delete()
        }
    }

    private fun outcomeOf(failure: Failure?) = when (failure) {
        null -> InstallOutcome.INSTALLED
        Failure.CANCELLED -> InstallOutcome.CANCELLED
        else -> InstallOutcome.FAILED
    }

    /** The checked APK of [app], or null without a server. */
    private suspend fun download(app: CatalogApp): File? {
        val server = server ?: return null
        val file = workFile()
        server.download(server.entry(app), file) { percent -> state.update { it.progress(app.pkg, percent) } }
        return file
    }
}
