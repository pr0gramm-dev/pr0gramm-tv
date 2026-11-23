package dev.pr0gramm.tv.data.catalogsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedAddressTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "server-address")

    @Test fun `without a saved address load is null`() {
        assertNull(SavedAddress(file()).load())
    }

    @Test fun `a saved address loads again, also in a new instance`() {
        val address = ServerAddress.parse("tv.example.org/tv")!!
        SavedAddress(file()).save(address)
        assertEquals(address, SavedAddress(file()).load())
    }

    @Test fun `save replaces the address and leaves no temporary file`() {
        val saved = SavedAddress(file())
        saved.save(ServerAddress.parse("one.example.org")!!)
        val two = ServerAddress.parse("two.example.org")!!
        saved.save(two)
        assertEquals(two, saved.load())
        assertEquals(listOf("server-address"), tmp.root.list()!!.toList())
    }

    @Test fun `a file that is no address loads as null`() {
        file().writeText("not an address")
        assertNull(SavedAddress(file()).load())
    }

    @Test fun `a saved http address loads again in a new instance`() {
        val address = ServerAddress.parse("http://192.168.1.5:8080/tv")!!
        SavedAddress(file()).save(address)
        assertEquals(address, SavedAddress(file()).load())
    }
}
