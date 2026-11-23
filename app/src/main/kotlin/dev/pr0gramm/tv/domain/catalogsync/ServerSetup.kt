package dev.pr0gramm.tv.domain.catalogsync

import dev.pr0gramm.tv.core.http.Http
import dev.pr0gramm.tv.data.catalogsync.SavedAddress
import dev.pr0gramm.tv.data.catalogsync.ServerAddress
import dev.pr0gramm.tv.data.catalogsync.ServerAnswer
import dev.pr0gramm.tv.data.catalogsync.ServerClient
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * The only owner of the server address (AC3). The process reads [current] once at start; a changed address takes
 * effect with the next process. [connect] saves an address only after its server gave a valid manifest.
 */
class ServerSetup internal constructor(
    private val saved: SavedAddress,
    private val http: Http,
    private val language: () -> String,
    private val io: CoroutineDispatcher,
) {
    /** The saved address at process start; null when none is saved. */
    val current: ServerAddress? = saved.load()

    /**
     * Checks the address the user [typed] and saves it when its `manifest.json` is valid. [Connect.Saved] tells
     * whether it differs from [current]. Any [Connect.Refused] saves nothing.
     */
    suspend fun connect(typed: String): Connect {
        val address = ServerAddress.parse(typed) ?: return Connect.BadAddress
        return when (val answer = ServerClient(address, http, language).manifest()) {
            ServerAnswer.Unreachable -> Connect.Unreachable
            is ServerAnswer.Invalid -> Connect.Rejected(answer.problems.first())
            is ServerAnswer.Valid -> save(address)
        }
    }

    /** A local write error counts as [Connect.Unreachable], as in [CatalogSync]. */
    private suspend fun save(address: ServerAddress): Connect {
        val changed = address != current
        if (!changed) return Connect.Saved(changed = false)
        return try {
            withContext(io) { saved.save(address) }
            Connect.Saved(changed = true)
        } catch (_: IOException) {
            Connect.Unreachable
        }
    }

    companion object {
        /**
         * The address in [saved]; [ServerAddress.parse] decides which typed addresses are allowed. [language] goes to
         * the [ServerClient] of each check.
         */
        fun create(saved: SavedAddress, http: Http, language: () -> String): ServerSetup =
            ServerSetup(saved, http, language, Dispatchers.IO)
    }
}

/** How [ServerSetup.connect] ended. */
sealed interface Connect {
    /** The address is saved; [changed] when it differs from the current one, so the process must restart. */
    data class Saved(val changed: Boolean) : Connect

    /** The address is not saved; the dialog stays open and names the reason. */
    sealed interface Refused : Connect

    /** The text is no https address (in debug builds also http). */
    data object BadAddress : Refused

    /** No network, an HTTP answer but 200, or the address could not be written. */
    data object Unreachable : Refused

    /** The manifest is invalid; [problem] is the parser's first problem. */
    data class Rejected(val problem: ManifestProblem) : Refused
}
