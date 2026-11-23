package dev.pr0gramm.tv.data.appsettings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    private class FakeStore(var stored: String) : LocaleStore {
        override var tags: String
            get() = stored
            set(value) {
                stored = value
            }
    }

    @Test
    fun switchCyclesSystemEnglishGermanAndWritesTheStore() {
        val store = FakeStore("")
        val language = AppLanguage(store)
        assertEquals(LanguageChoice.ENGLISH, language.switch())
        assertEquals("en", store.stored)
        assertEquals(LanguageChoice.GERMAN, language.switch())
        assertEquals("de", store.stored)
        assertEquals(LanguageChoice.SYSTEM, language.switch())
        assertEquals("", store.stored)
    }

    @Test
    fun switchReadsTheStoreNotAnEarlierCopy() {
        val store = FakeStore("de")
        val language = AppLanguage(store)
        store.stored = "en"
        assertEquals(LanguageChoice.GERMAN, language.switch())
        assertEquals(LanguageChoice.GERMAN, language.current)
    }
}
