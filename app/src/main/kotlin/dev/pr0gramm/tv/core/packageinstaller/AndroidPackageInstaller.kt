package dev.pr0gramm.tv.core.packageinstaller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import dev.pr0gramm.tv.core.updatefailure.Failure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [PackageInstallerPort] over PackageManager and a PackageInstaller session. The result of the session comes back
 * through [InstallReceiver]. [updatable] is true when this build is signed with the release key.
 */
class AndroidPackageInstaller(context: Context, override val updatable: Boolean) : PackageInstallerPort {
    private val app = context.applicationContext
    private val pm = app.packageManager

    override val packageName: String get() = app.packageName
    override val versionCode: Long get() = pm.getPackageInfo(app.packageName, 0).longVersionCode
    override val mayInstall: Boolean get() = pm.canRequestPackageInstalls()

    override fun installedVersion(pkg: String): Long? =
        try {
            pm.getPackageInfo(pkg, 0).longVersionCode
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    override suspend fun install(apk: File, pkg: String): Failure? = withContext(Dispatchers.IO) {
        try {
            commit(apk, pkg)
        } catch (_: IOException) {
            Failure.INSTALL_FAILED
        } catch (_: SecurityException) {
            Failure.INSTALL_FAILED
        }
    }

    private suspend fun commit(apk: File, pkg: String): Failure? {
        val installer = pm.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(pkg)
            setSize(apk.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        val result = CompletableDeferred<Failure?>()
        pending = Pending(id, result)
        try {
            write(installer, id, apk)
            return result.await()
        } catch (e: CancellationException) {
            abandon(installer, id)
            throw e
        } catch (e: IOException) {
            abandon(installer, id)
            throw e
        } finally {
            pending = null
        }
    }

    private fun write(installer: PackageInstaller, id: Int, apk: File) {
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(statusSender(id))
        }
    }

    private fun abandon(installer: PackageInstaller, id: Int) {
        runCatching { installer.abandonSession(id) }
    }

    private fun statusSender(id: Int): IntentSender {
        // The system adds the status extras to the intent, so it has to be mutable.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(app, id, Intent(app, InstallReceiver::class.java), flags).intentSender
    }

    private class Pending(val session: Int, val result: CompletableDeferred<Failure?>)

    internal companion object {
        @Volatile
        private var pending: Pending? = null

        /** Called by [InstallReceiver] for every status of a session. Other sessions are ignored. */
        fun onStatus(context: Context, intent: Intent) {
            val current = pending ?: return
            if (intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) != current.session) return
            when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> confirm(context, intent, current)
                PackageInstaller.STATUS_SUCCESS -> current.result.complete(null)
                PackageInstaller.STATUS_FAILURE_ABORTED -> current.result.complete(Failure.CANCELLED)
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                -> current.result.complete(Failure.OTHER_KEY)
                else -> current.result.complete(Failure.INSTALL_FAILED)
            }
        }

        private fun confirm(context: Context, status: Intent, current: Pending) {
            val dialog = status.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            val started = dialog != null &&
                runCatching { context.startActivity(dialog.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
            if (!started) current.result.complete(Failure.INSTALL_FAILED)
        }
    }
}
