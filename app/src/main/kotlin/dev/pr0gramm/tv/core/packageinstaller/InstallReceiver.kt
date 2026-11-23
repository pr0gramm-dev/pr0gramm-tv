package dev.pr0gramm.tv.core.packageinstaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the status of the install session committed by [AndroidPackageInstaller]. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AndroidPackageInstaller.onStatus(context, intent)
    }
}
