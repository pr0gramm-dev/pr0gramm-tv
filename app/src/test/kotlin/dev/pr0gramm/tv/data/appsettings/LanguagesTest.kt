package dev.pr0gramm.tv.data.appsettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * [LanguageChoice] is the one table of the app's languages (review item 44): its tags match the `values-*` folders
 * and `locales_config.xml`, and every rule follows from its order.
 */
class LanguagesTest {
    private val res = File("src/main/res")

    @Test
    fun theTableNamesExactlyTheLanguagesOfTheResources() {
        val folders = res.listFiles().orEmpty()
            .filter { it.name.startsWith("values-") && File(it, "strings.xml").isFile }
            .map { it.name.removePrefix("values-") }
        val tags = LanguageChoice.languages.map { it.tag }
        assertEquals((folders + LanguageChoice.DEFAULT.tag).sorted(), tags.sorted())

        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val config = builder.parse(File(res, "xml/locales_config.xml"))
        val nodes = config.getElementsByTagName("locale")
        val locales = (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("android:name").nodeValue }
        assertEquals(tags.sorted(), locales.sorted())
    }

    @Test
    fun everyLanguageHasAnOwnNameAndIsFoundByItsTag() {
        for (language in LanguageChoice.languages) {
            assertNotNull(language.ownName)
            assertEquals(language, LanguageChoice.of(language.tag))
            assertEquals(language, LanguageChoice.of("${language.tag}-XX,fr"))
        }
        assertEquals(LanguageChoice.SYSTEM, LanguageChoice.of(""))
        assertEquals(LanguageChoice.DEFAULT, LanguageChoice.of("fr-FR"))
    }

    @Test
    fun nextVisitsEveryChoiceOnceInTheOrderOfTheTable() {
        val seen = generateSequence(LanguageChoice.SYSTEM) { it.next() }.take(LanguageChoice.entries.size + 1).toList()
        assertEquals(LanguageChoice.entries + LanguageChoice.SYSTEM, seen)
    }
}
