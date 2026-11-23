package dev.pr0gramm.tv.domain.appinstall

/** The facts [AppInstaller] publishes on [AppInstaller.events]. */
sealed interface InstallEvent {
    /** The install of [job] ended with [outcome]. */
    data class Finished(val job: InstallJob, val outcome: InstallOutcome) : InstallEvent
}
