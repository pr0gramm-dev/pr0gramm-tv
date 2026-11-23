import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlocklistFormatTest {
    private val url = "https://lists.example.org/domains.txt"

    private fun golden() = javaClass.getResource("/blocklist.golden.txt")!!.readText()

    private val raw = "# a comment\r\n  Ads.Example.COM  \r\ntracker.example.org\r\n\r\nlocalhost\r\nhttps://x.example/p\r\n" +
        "bad domain.com\r\nads.example.com\r\n_dmarc.example.net # trailing\r\ncdn-1.example.io\r\n"

    @Test fun `domains trims lowercases and drops comments, junk and duplicates`() {
        assertEquals(
            listOf("ads.example.com", "tracker.example.org", "cdn-1.example.io"),
            BlocklistFormat.domains(raw).toList(),
        )
    }

    @Test fun `asset matches the golden file`() {
        assertEquals(golden(), BlocklistFormat.asset(BlocklistFormat.domains(raw)))
    }

    @Test fun `asset has no carriage return and ends with a newline`() {
        val asset = BlocklistFormat.asset(BlocklistFormat.domains(raw))
        assertFalse(asset.contains('\r'))
        assertTrue(asset.endsWith("\n"))
    }

    private fun list(n: Int) = (1..n).joinToString("\n") { "d$it.example.org" }

    @Test fun `a new list with 1000 domains is used`() {
        assertEquals(BlocklistFormat.Choice.UseCandidate(1000), BlocklistFormat.choose(list(1000), null, url))
    }

    @Test fun `a short list falls back to the copy from the same url with a warning`() {
        val copy = BlocklistFormat.copy(list(5000), url)
        val choice = BlocklistFormat.choose(list(999), copy, url)
        assertTrue(choice is BlocklistFormat.Choice.UseCopy, choice.toString())
        val warning = (choice as BlocklistFormat.Choice.UseCopy).warning
        assertTrue(warning.contains("nur 999 Domains"), warning)
        assertTrue(warning.contains(url), warning)
    }

    @Test fun `a failed download falls back to the copy`() {
        val choice = BlocklistFormat.choose(null, BlocklistFormat.copy(list(1000), url), url)
        assertTrue(choice is BlocklistFormat.Choice.UseCopy, choice.toString())
        assertTrue((choice as BlocklistFormat.Choice.UseCopy).warning.contains("Download fehlgeschlagen"))
    }

    @Test fun `a copy from another address is no fallback`() {
        val copy = BlocklistFormat.copy(list(1000), "https://other.example/list.txt")
        assertTrue(BlocklistFormat.choose(null, copy, url) is BlocklistFormat.Choice.Fail)
    }

    @Test fun `a copy without a source line is no fallback`() {
        assertTrue(BlocklistFormat.choose(list(10), list(2000), url) is BlocklistFormat.Choice.Fail)
    }

    @Test fun `no candidate and no copy fails with a message`() {
        val choice = BlocklistFormat.choose(null, null, url)
        assertTrue(choice is BlocklistFormat.Choice.Fail, choice.toString())
        assertTrue((choice as BlocklistFormat.Choice.Fail).message.contains("keine Kopie"))
    }

    @Test fun `copy keeps the source line first and the list after it`() {
        val text = BlocklistFormat.copy("a.example.org\n", url)
        assertEquals("# source: $url\na.example.org\n", text)
        assertEquals(setOf("a.example.org"), BlocklistFormat.domains(text))
    }
}
