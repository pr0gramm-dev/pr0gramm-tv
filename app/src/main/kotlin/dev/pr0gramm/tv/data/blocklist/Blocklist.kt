package dev.pr0gramm.tv.data.blocklist

import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The domains of the asset `blocklist.txt` (format: lowercase, one domain per line, see BlocklistFormat in
 * build-logic). [start] loads it once in the background; [blocked] waits for it for at most [waitMs] after the
 * start and then fails open, so a slow or broken list never stops a page.
 *
 * [open] returns the list, or null when the build has none. It runs on the loading thread.
 */
class Blocklist(
    private val open: () -> InputStream?,
    private val waitMs: Long = WAIT_MS,
) {
    private val ready = CountDownLatch(1)

    /** Sorted FNV-1a hashes of the listed domains; a HashSet of strings took seconds to fill on the TV. */
    @Volatile private var hashes: LongArray? = null

    @Volatile private var startedAt = 0L

    private var started = false

    /** Loads the list in the background. Only the first call does anything. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        startedAt = System.nanoTime()
        Thread({ load() }, "blocklist").apply { isDaemon = true }.start()
    }

    /**
     * [host] or one of its parent domains is listed, and neither it nor a parent is in [allow].
     * IP addresses count only when listed exactly. Waits for the list up to [waitMs] after [start]; a list that is
     * not ready by then, or failed to load, blocks nothing. Call it off the UI thread.
     */
    fun blocked(host: String?, allow: Collection<String>): Boolean = matches(host, allow) { awaited() }

    /** Like [blocked], but never waits: before the list is loaded it blocks nothing. For the UI thread. */
    fun blockedNow(host: String?, allow: Collection<String>): Boolean = matches(host, allow) { hashes }

    private inline fun matches(host: String?, allow: Collection<String>, list: () -> LongArray?): Boolean {
        val name = host?.lowercase()?.trimEnd('.').orEmpty()
        val sorted = if (name.isEmpty() || listed(name) { it in allow }) null else list()
        return when {
            sorted == null -> false
            name.startsWith('[') || IPV4.matches(name) -> sorted.binarySearch(hash(name)) >= 0
            else -> listed(name) { sorted.binarySearch(hash(it)) >= 0 }
        }
    }

    private fun awaited(): LongArray? {
        val left = waitMs - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
        if (startedAt != 0L && left > 0) ready.await(left, TimeUnit.MILLISECONDS)
        return hashes
    }

    private fun load() {
        try {
            val bytes = open()?.use { it.readBytes() }
            if (bytes != null) hashes = hashLines(bytes)
        } catch (_: Exception) {
            // reason: a missing or broken list must not stop browsing; the list stays empty and blocks nothing.
        } catch (_: OutOfMemoryError) {
            // reason: same as above, a huge list on a small device.
        } finally {
            ready.countDown()
        }
    }

    /** Hashes each line of [bytes] (ASCII, `\n` separated); a last line without `\n` counts. */
    private fun hashLines(bytes: ByteArray): LongArray {
        val newline = '\n'.code.toByte()
        val all = LongArray(bytes.count { it == newline } + 1)
        var count = 0
        var x = FNV_OFFSET
        var empty = true
        for (b in bytes) {
            if (b == newline) {
                if (!empty) all[count++] = x
                x = FNV_OFFSET
                empty = true
            } else {
                x = (x xor (b.toLong() and BYTE_MASK)) * FNV_PRIME
                empty = false
            }
        }
        if (!empty) all[count++] = x
        return all.copyOf(count).also { it.sort() }
    }

    /** [host] or one of its parent domains is [inSet]: a suffix at a '.' boundary, never a substring. */
    private inline fun listed(host: String, inSet: (String) -> Boolean): Boolean {
        var h = host
        while (true) {
            if (inSet(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /** FNV-1a 64 over the low byte of each char: the value [hashLines] computes from the ASCII bytes. */
    private fun hash(s: String): Long {
        var x = FNV_OFFSET
        for (c in s) x = (x xor (c.code.toLong() and BYTE_MASK)) * FNV_PRIME
        return x
    }

    private companion object {
        const val WAIT_MS = 2_000L
        const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        const val FNV_PRIME = 0x100000001b3L
        const val BYTE_MASK = 0xffL
        val IPV4 = Regex("\\d{1,3}(\\.\\d{1,3}){3}")
    }
}
