package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.domain.catalogsync.Absence
import dev.pr0gramm.tv.domain.catalogsync.CatalogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoCatalogTest {
    /** The notice of [absence] in German and English, and its button. */
    private fun notice(absence: Absence): Triple<String, String, NoCatalogButton> {
        val notice = requireNotNull(NoCatalogNotice.of(CatalogState.Missing(absence)))
        return Triple(Strings.de(notice.text), Strings.en(notice.text), notice.button)
    }

    @Test
    fun `while the first refresh runs the page is neutral`() {
        assertNull(NoCatalogNotice.of(CatalogState.Loading))
    }

    @Test
    fun `an unreachable server is named and can be asked again`() {
        assertEquals(
            Triple("Server nicht erreichbar", "Cannot reach the server", NoCatalogButton.RETRY),
            notice(Absence.UNREACHABLE),
        )
    }

    @Test
    fun `an invalid manifest is named and can be asked again`() {
        assertEquals(
            Triple("Katalog ungültig", "Catalog not valid", NoCatalogButton.RETRY),
            notice(Absence.REJECTED),
        )
    }

    @Test
    fun `without a server the button asks for an address`() {
        assertEquals(
            Triple("Kein Server eingerichtet", "No server set up", NoCatalogButton.ENTER_SERVER),
            notice(Absence.NO_SERVER),
        )
    }

    @Test
    fun `each button says what it does`() {
        assertEquals("Wiederholen", Strings.de(NoCatalogButton.RETRY.label))
        assertEquals("Server eingeben", Strings.de(NoCatalogButton.ENTER_SERVER.label))
        assertEquals("Try again", Strings.en(NoCatalogButton.RETRY.label))
        assertEquals("Enter server address", Strings.en(NoCatalogButton.ENTER_SERVER.label))
    }
}
