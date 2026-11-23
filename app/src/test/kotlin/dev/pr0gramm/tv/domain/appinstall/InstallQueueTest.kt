package dev.pr0gramm.tv.domain.appinstall

import dev.pr0gramm.tv.data.manifest.Apk
import dev.pr0gramm.tv.data.manifest.CatalogApp
import dev.pr0gramm.tv.data.manifest.ServerPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class InstallQueueTest {
    private fun app(pkg: String, versionCode: Long = 1) =
        CatalogApp(pkg, pkg, null, versionCode, Apk(ServerPath.of("$pkg.apk")!!, "ab".repeat(32), 10))

    private val kodi = InstallJob.App(app("org.kodi"))
    private val vlc = InstallJob.App(app("org.vlc"))
    private val self = InstallJob.Self("dev.pr0gramm.tv.app", 7, File("update.apk"))

    private val empty = InstallQueue()

    @Test
    fun `installs run one at a time in the order they were asked for`() {
        var queue = empty.request(kodi, allowed = true).request(vlc, allowed = true).next(foreground = true)
        assertEquals(kodi, queue.running)
        assertEquals(listOf(vlc), queue.waiting)
        assertEquals(queue, queue.next(foreground = true))

        queue = queue.finished("org.kodi", InstallOutcome.INSTALLED).next(foreground = true)
        assertEquals(vlc, queue.running)
        assertEquals(emptyList<InstallJob>(), queue.waiting)
    }

    @Test
    fun `asking again for a waiting or running app does nothing`() {
        val waiting = empty.request(kodi, allowed = true).request(vlc, allowed = true)
        assertEquals(waiting, waiting.request(vlc, allowed = true))
        val running = waiting.next(foreground = true)
        assertEquals(running, running.request(kodi, allowed = true))
        assertEquals(running, running.request(kodi, allowed = false))
    }

    @Test
    fun `a failure is kept with the versionCode until the app is asked for again`() {
        val failed = empty.request(kodi, allowed = true).next(foreground = true)
            .finished("org.kodi", InstallOutcome.FAILED)
        assertNull(failed.running)
        assertEquals(mapOf("org.kodi" to 1L), failed.failed)

        val retried = failed.request(kodi, allowed = true)
        assertEquals(emptyMap<String, Long>(), retried.failed)
        assertEquals(listOf(kodi), retried.waiting)
        assertEquals(emptyMap<String, Long>(), retried.next(true).finished("org.kodi", InstallOutcome.INSTALLED).failed)
    }

    @Test
    fun `finishing a package that does not run changes nothing`() {
        val running = empty.request(kodi, allowed = true).next(foreground = true)
        assertEquals(running, running.finished("org.vlc", InstallOutcome.FAILED))
    }

    @Test
    fun `without permission the app waits for it and goes on once it is granted`() {
        val asked = empty.request(kodi, allowed = false)
        assertEquals(kodi, asked.awaitingPermission)
        assertEquals(emptyList<InstallJob>(), asked.waiting)
        assertNull(asked.next(foreground = true).running)

        val granted = asked.permission(allowed = true)
        assertNull(granted.awaitingPermission)
        assertEquals(listOf(kodi), granted.waiting)

        val refused = asked.permission(allowed = false)
        assertEquals(empty, refused)
    }

    @Test
    fun `the own update waits for the start page and for every app before it`() {
        val ready = empty.selfReady(self)
        assertEquals(self, ready.ready)
        assertEquals(ready, ready.next(foreground = false))

        var queue = ready.request(kodi, allowed = true).next(foreground = true)
        assertEquals(kodi, queue.running)
        queue = queue.finished("org.kodi", InstallOutcome.INSTALLED).next(foreground = true)
        assertEquals(self, queue.running)
        assertNull(queue.ready)
    }

    @Test
    fun `progress of the running download is kept in the queue`() {
        val queue = InstallQueue().request(kodi, allowed = true).next(foreground = true)
        assertEquals(0, queue.percent)
        assertEquals(40, queue.progress("org.kodi", 40).percent)
        assertEquals(0, queue.progress("org.vlc", 40).percent)
        assertEquals(0, queue.progress("org.kodi", 40).finished("org.kodi", InstallOutcome.INSTALLED).percent)
    }

    @Test
    fun `a newer own update replaces the ready one`() {
        val newer = InstallJob.Self("dev.pr0gramm.tv.app", 8, File("update-8.apk"))
        assertEquals(newer, empty.selfReady(self).selfReady(newer).ready)
    }

    @Test
    fun `a failed own update is kept with its versionCode`() {
        val failed = empty.selfReady(self).next(foreground = true)
            .finished("dev.pr0gramm.tv.app", InstallOutcome.FAILED)
        assertEquals(mapOf("dev.pr0gramm.tv.app" to 7L), failed.failed)
    }
}
