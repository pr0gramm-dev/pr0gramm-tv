package dev.pr0gramm.tv.core.http

import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.core.updatefailure.UpdateFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** A download that must have exactly [size] bytes with the SHA-256 [sha256] (lower-case hex). */
class VerifiedDownload(private val size: Long, private val sha256: String) {
    /**
     * Writes the body that [get] hands to its reader into [into], checked like [copy]. The one place of the checked
     * APK download: on any failure [into] is deleted. A [RedirectRefused] is [Failure.BAD_INDEX], any other
     * [IOException] is [Failure.UNREACHABLE], a mismatch is [Failure.BROKEN_APK]. [progress] gets each new percent on
     * the thread that reads the body.
     */
    internal suspend fun save(
        into: File,
        progress: (Int) -> Unit,
        get: suspend (read: (InputStream) -> Unit) -> Unit,
    ) {
        val job = currentCoroutineContext()[Job]
        var done = false
        try {
            get { input -> into.outputStream().use { copy(input, it, job, progress) } }
            done = true
        } catch (e: RedirectRefused) {
            throw UpdateFailure(Failure.BAD_INDEX, e.toString(), e)
        } catch (e: IOException) {
            throw UpdateFailure(Failure.UNREACHABLE, e.toString(), e)
        } finally {
            if (!done) into.delete()
        }
    }

    /**
     * Copies [input] to [out] while checking size and SHA-256. A mismatch is [Failure.BROKEN_APK]. A cancelled
     * [job] stops the copy before the next buffer. [progress] gets each new percent on the calling thread.
     */
    internal fun copy(input: InputStream, out: OutputStream, job: Job?, progress: (Int) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var total = 0L
        var shown = -1
        while (true) {
            job?.ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > size) throw UpdateFailure(Failure.BROKEN_APK, "larger than size")
            digest.update(buffer, 0, n)
            out.write(buffer, 0, n)
            val percent = (PERCENT * total / size).toInt()
            if (percent != shown) {
                progress(percent)
                shown = percent
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (total != size || actual != sha256) throw UpdateFailure(Failure.BROKEN_APK, "size/sha256")
    }

    private companion object {
        const val BUFFER = 64 * 1024
        const val PERCENT = 100L
    }
}
