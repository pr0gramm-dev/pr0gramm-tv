package dev.pr0gramm.tv.core.http

import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.core.updatefailure.UpdateFailure
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class VerifiedDownloadTest {
    private val bytes = "apk bytes ".repeat(100).toByteArray()
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val download = VerifiedDownload(bytes.size.toLong(), sha)

    private fun failure(body: ByteArray): Failure = assertThrows(UpdateFailure::class.java) {
        download.copy(body.inputStream(), ByteArrayOutputStream(), job = null) {}
    }.failure

    @Test
    fun theCheckedBytesArrive() {
        val out = ByteArrayOutputStream()
        val progress = mutableListOf<Int>()
        download.copy(bytes.inputStream(), out, job = null) { progress += it }
        assertArrayEquals(bytes, out.toByteArray())
        assertEquals(100, progress.last())
    }

    @Test
    fun anotherShaIsBrokenApk() {
        assertEquals(Failure.BROKEN_APK, failure(bytes.copyOf().also { it[0] = 'X'.code.toByte() }))
    }

    @Test
    fun moreOrFewerBytesAreBrokenApk() {
        assertEquals(Failure.BROKEN_APK, failure(bytes + 1))
        assertEquals(Failure.BROKEN_APK, failure(bytes.copyOf(bytes.size - 1)))
    }
}
