package dev.pr0gramm.tv.core.text

import dev.pr0gramm.tv.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** [UiText.resolve] and the test helper [StringTable] (AC10, AC11). */
class UiTextTest {
    @Test
    fun `the test table fails on an arg the resource does not use`() {
        // tile_waiting has no placeholder: String.format would ignore the arg, so the test table must not.
        assertThrows(IllegalStateException::class.java) { Strings.en(text(R.string.tile_waiting, "extra")) }
    }

    private val defaultLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun aResourceWithoutArgsIsItsText() {
        assertEquals("today", Strings.en(text(R.string.day_today)))
        assertEquals("heute", Strings.de(text(R.string.day_today)))
    }

    @Test
    fun plainTextStaysAsItIs() {
        assertEquals("nova %1\$s 50 %", UiText.Plain("nova %1\$s 50 %").resolve(fake("en")))
    }

    @Test
    fun nestedArgsResolveFirst() {
        val day = text(INNER, text(INNERMOST))
        val line = text(OUTER, day, "nova", 12, 3_000_000_000L)
        assertEquals("[gemerkt am <heute>] nova 12 3000000000", line.resolve(fake("de")))
    }

    @Test
    fun aDoublePercentSignIsOnePercentSign() {
        assertEquals("Downloading… 42%", text(PERCENT, 42).resolve(fake("en")))
    }

    @Test
    fun aDateUsesThePatternOfTheLanguage() {
        val day = LocalDate.of(2026, 11, 21)
        assertEquals("21.11.2026", Strings.de(UiText.Date(R.string.date_full, day)))
        assertEquals("Nov 21, 2026", Strings.en(UiText.Date(R.string.date_full, day)))
        val time = LocalDateTime.of(2026, 11, 21, 14, 5)
        assertEquals("21.11. 14:05", Strings.de(UiText.Date(R.string.date_day_time, time)))
        assertEquals("Nov 21, 2:05 PM", Strings.en(UiText.Date(R.string.date_day_time, time)))
    }

    @Test
    fun formatLocaleWinsOverTheDefaultLocale() {
        val day = LocalDate.of(2026, 11, 21)
        Locale.setDefault(Locale.GERMANY)
        assertEquals("Nov 21, 2026", Strings.en(UiText.Date(R.string.date_full, day)))
        assertEquals("Nov 21", Strings.en(UiText.Date(R.string.date_day, day)))
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))
        assertEquals("21.11.2026", Strings.de(UiText.Date(R.string.date_full, day)))
        assertEquals("3000000000", text(NUMBER, 3_000_000_000L).resolve(fake("de")))
    }

    @Test
    fun onlyStringIntLongAndUiTextAreArgs() {
        assertThrows(IllegalArgumentException::class.java) { text(OUTER, 1.5) }
        assertThrows(IllegalArgumentException::class.java) { UiText.Res(OUTER, listOf(LocalDate.MIN)) }
    }

    @Test
    fun theTableReadsValuesAsAapt() {
        assertEquals("it's", StringTable.unescape("it\\'s"))
        assertEquals("say \"hi\"", StringTable.unescape("say \\\"hi\\\""))
        assertEquals("a\nb\tc", StringTable.unescape("a\\nb\\tc"))
        assertEquals("50\u00A0%", StringTable.unescape("50\\u00A0%"))
        assertEquals("\\u needs", StringTable.unescape("\\\\u needs"))
        assertEquals("one two", StringTable.unescape("  one \n\t two  "))
        assertEquals("  kept  spaces ", StringTable.unescape("\"  kept  spaces \""))
        assertEquals("a b c", StringTable.unescape("a \"b\" c"))
        assertEquals("⚙\uFE0E Gear", StringTable.unescape("⚙\\uFE0E Gear"))
    }

    /** aapt2 37.0.0 (`aapt2 dump resources`) keeps these chars as they are; only ASCII space, tab and newline join. */
    @Test
    fun theTableKeepsNonAsciiSpacesAsAapt() {
        assertEquals("\u00A0a\u00A0", StringTable.unescape("\u00A0a\u00A0"))
        assertEquals("a \u00A0 b", StringTable.unescape("a \u00A0 b"))
        assertEquals("a\u202Fb a\u2009b a\u3000b", StringTable.unescape("a\u202Fb a\u2009b a\u3000b"))
    }

    @Test
    fun theTableReadsAStringsFile() {
        val dir = Files.createTempDirectory("values").toFile()
        try {
            File(dir, "strings.xml").writeText(
                """
                <resources>
                    <string name="day_today">it\'s &amp; <b>bold</b></string>
                    <string name="format_locale">en</string>
                </resources>
                """.trimIndent(),
            )
            val table = StringTable(dir.path)
            assertEquals("it's & bold", table(text(R.string.day_today)))
            assertThrows(IllegalStateException::class.java) { table(text(R.string.day_yesterday)) }
            assertThrows(IllegalStateException::class.java) { table.raw(0) }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Raw texts with made-up ids next to `format_locale`, for the format rules that no resource uses yet. */
    private fun fake(language: String): (Int) -> String = mapOf(
        R.string.format_locale to language,
        OUTER to "[%1\$s] %2\$s %3\$d %4\$d",
        INNER to "gemerkt am <%1\$s>",
        INNERMOST to "heute",
        PERCENT to "Downloading… %1\$d%%",
        NUMBER to "%1\$d",
    )::getValue

    private companion object {
        const val OUTER = 1
        const val INNER = 2
        const val INNERMOST = 3
        const val PERCENT = 4
        const val NUMBER = 5
    }
}
