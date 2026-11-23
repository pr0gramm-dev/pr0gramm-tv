package dev.pr0gramm.tv.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import kotlin.system.exitProcess

/**
 * A full process restart (AC4). [AppContainer] builds the server client and the stores for the address it read at
 * process start, so a new address takes effect only in a new process.
 */
internal object ProcessRestart {
    /** Asks the system for a new [LauncherActivity] task, then ends this process; the system starts a new one. */
    fun restart(activity: Activity) {
        activity.startActivity(Intent.makeRestartActivityTask(ComponentName(activity, LauncherActivity::class.java)))
        exitProcess(0)
    }
}
