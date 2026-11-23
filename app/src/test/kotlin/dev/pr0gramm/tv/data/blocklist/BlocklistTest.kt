package dev.pr0gramm.tv.data.blocklist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch

class BlocklistTest {
    /** The golden asset of build-logic's BlocklistFormatTest: the contract between build and app. */
    private fun golden(): ByteArray = javaClass.getResourceAsStream("/blocklist.golden.txt")!!.readBytes()

    private fun loaded(open: () -> InputStream? = { ByteArrayInputStream(golden()) }) =
        Blocklist(open).also { it.start() }

    private fun awaitThenGolden(gate: CountDownLatch): InputStream {
        gate.await()
        return ByteArrayInputStream(golden())
    }

    @Test
    fun blocksListedDomainAndItsSubdomains() {
        val list = loaded()
        assertTrue(list.blocked("ads.example.com", emptyList()))
        assertTrue(list.blocked("img.cdn.ads.example.com", emptyList()))
        assertTrue(list.blocked("CDN-1.example.io.", emptyList()))
    }

    @Test
    fun suffixMustStartAtADot() {
        val list = loaded()
        assertFalse(list.blocked("notads.example.com", emptyList()))
        assertFalse(list.blocked("example.com", emptyList()))
        assertFalse(list.blocked("ads.example.com.evil.net", emptyList()))
    }

    @Test
    fun allowOverridesTheListIncludingParents() {
        val list = loaded()
        assertFalse(list.blocked("ads.example.com", listOf("ads.example.com")))
        assertFalse(list.blocked("x.tracker.example.org", listOf("tracker.example.org")))
        assertTrue(list.blocked("x.tracker.example.org", listOf("other.org")))
    }

    @Test
    fun ipAddressesCountOnlyWhenListedExactly() {
        val list = loaded { ByteArrayInputStream("1.2.3.4\n".toByteArray()) }
        assertTrue(list.blocked("1.2.3.4", emptyList()))
        assertFalse(list.blocked("9.2.3.4", emptyList()))
    }

    @Test
    fun nullAndEmptyHostsPass() {
        val list = loaded()
        assertFalse(list.blocked(null, emptyList()))
        assertFalse(list.blocked("", emptyList()))
    }

    @Test
    fun failsOpenWhenTheListIsNotReadyInTime() {
        val never = CountDownLatch(1)
        val list = Blocklist({ awaitThenGolden(never) }, waitMs = 100)
        list.start()
        val began = System.nanoTime()
        assertFalse(list.blocked("ads.example.com", emptyList()))
        val tookMs = (System.nanoTime() - began) / NANOS_PER_MS
        assertTrue("waited $tookMs ms", tookMs in MIN_WAIT_MS..MAX_WAIT_MS)
        never.countDown()
    }

    @Test
    fun failsOpenWhenLoadingFailsOrThereIsNoList() {
        assertFalse(loaded { error("broken") }.blocked("ads.example.com", emptyList()))
        assertFalse(loaded { null }.blocked("ads.example.com", emptyList()))
    }

    @Test
    fun loadsInTheBackgroundAndWaitsForTheList() {
        val gate = CountDownLatch(1)
        val list = Blocklist({ awaitThenGolden(gate) })
        list.start()
        Thread {
            Thread.sleep(50)
            gate.countDown()
        }.start()
        assertTrue(list.blocked("ads.example.com", emptyList()))
    }

    @Test
    fun blockedNowNeverWaitsAndBlocksNothingBeforeTheListIsThere() {
        val gate = CountDownLatch(1)
        val list = Blocklist({ awaitThenGolden(gate) })
        list.start()
        val began = System.nanoTime()
        assertFalse(list.blockedNow("ads.example.com", emptyList()))
        // Not waiting at all would be 0 ms; a loaded test machine can take far longer than the 80 ms of the old bound.
        assertTrue((System.nanoTime() - began) / NANOS_PER_MS < MAX_WAIT_MS / 2)
        gate.countDown()
        assertTrue(list.blocked("ads.example.com", emptyList()))
        assertTrue(list.blockedNow("ads.example.com", emptyList()))
        assertTrue(list.blockedNow("img.ads.example.com", emptyList()))
        assertFalse(list.blockedNow("ads.example.com", listOf("example.com")))
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val MIN_WAIT_MS = 80L
        const val MAX_WAIT_MS = 1_000L
    }
}
