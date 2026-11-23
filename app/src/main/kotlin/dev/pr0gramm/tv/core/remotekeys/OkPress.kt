package dev.pr0gramm.tv.core.remotekeys

/**
 * The one OK-button rule: a short press is a [Press.TAP], holding it for [holdMs] fires once through [poll].
 * Callers feed key down and up events and poll while the key is held.
 */
class OkPress(private val clock: () -> Long, private val holdMs: Long = LONG_PRESS_MS) {
    private var downAt: Long? = null
    private var fired = false

    /** Repeated downs while the key is held are ignored. */
    fun down() {
        if (downAt == null) {
            downAt = clock()
            fired = false
        }
    }

    /** [Press.TAP] if released before [holdMs]; [Press.NONE] when the hold already fired or there was no down. */
    fun up(): Press {
        val start = downAt
        downAt = null
        return if (start != null && !fired && clock() - start < holdMs) Press.TAP else Press.NONE
    }

    /** Ends the press without a result, for when the release goes elsewhere (the focus moved while held). */
    fun cancel() {
        downAt = null
        fired = false
    }

    /** True exactly once per press, as soon as [holdMs] has passed since [down]. */
    fun poll(): Boolean {
        val start = downAt
        val due = start != null && !fired && clock() - start >= holdMs
        if (due) fired = true
        return due
    }

    enum class Press { TAP, NONE }

    companion object {
        const val LONG_PRESS_MS = 600L
    }
}
