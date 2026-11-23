package dev.pr0gramm.tv.data.appsettings

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageChoiceTest {
    @Test
    fun noAppLanguageIsTheSystemLanguage() {
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.of(""))
    }

    @Test
    fun aFirstTagInGermanCountsAsGerman() {
        assertEquals(LanguageChoice.GERMAN, LanguageChoice.of("de"))
        assertEquals(LanguageChoice.GERMAN, LanguageChoice.of("de-AT"))
        assertEquals(LanguageChoice.GERMAN, LanguageChoice.of("de-DE,fr"))
    }

    @Test
    fun everyOtherListCountsAsEnglish() {
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.of("en"))
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.of("en-GB"))
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.of("fr"))
        // Only the first entry counts (AC5).
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.of("fr,de"))
    }

    @Test
    fun nextGoesSystemEnglishGermanSystem() {
        assertEquals(LanguageChoice.ENGLISH, LanguageChoice.SYSTEM.next())
        assertEquals(LanguageChoice.GERMAN, LanguageChoice.ENGLISH.next())
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.GERMAN.next())
    }

    @Test
    fun eachChoiceSetsItsTagAndReadsBackAsItself() {
        assertEquals(listOf("", "en", "de"), LanguageChoice.entries.map { it.tag })
        LanguageChoice.entries.forEach { assertEquals(it, LanguageChoice.of(it.tag)) }
    }
}
