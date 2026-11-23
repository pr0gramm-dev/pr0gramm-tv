package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import dev.pr0gramm.tv.domain.catalogsync.Connect
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDialogTest {
    @Test
    fun `a text that is no address is named`() {
        assertEquals("Keine gültige Adresse", Strings.de(ServerDialog.reason(Connect.BadAddress)))
        assertEquals("Address not valid", Strings.en(ServerDialog.reason(Connect.BadAddress)))
    }

    @Test
    fun `an unreachable server is named`() {
        assertEquals("Server nicht erreichbar", Strings.de(ServerDialog.reason(Connect.Unreachable)))
        assertEquals("Cannot reach the server", Strings.en(ServerDialog.reason(Connect.Unreachable)))
    }

    @Test
    fun `an invalid catalog names the parser's first problem`() {
        val problem = ManifestProblem("sites[0] (nova).url", ManifestProblem.Kind.BAD_URL, listOf("ftp://x"))
        val reason = ServerDialog.reason(Connect.Rejected(problem))
        val german = "Katalog ungültig: sites[0] (nova).url: \"ftp://x\" ist keine gültige http(s)-URL"
        assertEquals(german, Strings.de(reason))
        val english = "Catalog not valid: sites[0] (nova).url: \"ftp://x\" is not a valid http(s) URL"
        assertEquals(english, Strings.en(reason))
    }
}
