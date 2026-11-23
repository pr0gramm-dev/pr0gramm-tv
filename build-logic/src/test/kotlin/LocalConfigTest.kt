import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LocalConfigTest {
    private fun parse(json: String): LocalConfig = LocalConfig.parse(json, "local.json")

    private fun errors(json: String): List<String> = assertThrows<LocalConfigException> { parse(json) }.problems

    @Test fun `an empty object is fine`() {
        val cfg = parse("{}")
        assertNull(cfg.adb)
        assertNull(cfg.blocklist)
    }

    @Test fun `adb and blocklist are read`() {
        val cfg = parse("""{ "adb": "10.0.0.2:5555", "blocklist": "https://lists.example/b.txt" }""")
        assertEquals("10.0.0.2:5555", cfg.adb)
        assertEquals("https://lists.example/b.txt", cfg.blocklist)
    }

    @Test fun `the site part is gone, sites is an unknown field`() {
        val e = errors("""{ "sites": [ { "id": "nova", "name": "Nova", "url": "https://nova.example/" } ] }""")
        assertTrue(e.single().startsWith("sites: unbekanntes Feld (erlaubt: adb, blocklist)"), e.toString())
    }

    @Test fun `adb must be a string`() {
        assertTrue(errors("""{ "adb": 5 }""").single().startsWith("adb: muss ein String sein"))
    }

    @Test fun `blocklist must be an http address`() {
        assertTrue(errors("""{ "blocklist": "ftp://x.example/l.txt" }""").single().startsWith("blocklist: muss eine http(s)-Adresse sein"))
    }

    @Test fun `top level must be an object`() {
        assertEquals(listOf("oberste Ebene muss ein Objekt sein"), errors("[]"))
    }

    @Test fun `invalid json`() {
        assertTrue(errors("{ nope").single().startsWith("kein gültiges JSON"))
    }

    @Test fun `message lists source and problems`() {
        val ex = assertThrows<LocalConfigException> { parse("""{ "adb": 5, "x": 1 }""") }
        assertTrue(ex.message!!.startsWith("local.json ist fehlerhaft:\n  "), ex.message)
    }
}
