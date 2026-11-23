package dev.pr0gramm.tv.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The string resources (AC2, AC3): `values/` is English, every `values-xx/` has the same keys with the same
 * placeholders, every `%` is a placeholder or `%%` next to a placeholder, `locales_config.xml` lists `en` and every
 * `values-xx/`, and the English texts keep the measurable rules of ASD-STE100. Unit tests run in the module directory.
 */
class StringsXmlTest {
    private val res = File("src/main/res")
    private val english by lazy { strings("values") }

    /** Texts with one `*` that is no bold mark: `problem_bad_id` shows the pattern `[a-z0-9]*`. */
    private val unpairedStars = setOf("problem_bad_id")

    /** The language of every `values-xx/` folder with a `strings.xml`, sorted. */
    private val languages: List<String> by lazy {
        res.listFiles().orEmpty()
            .filter { File(it, STRINGS).isFile }
            .mapNotNull { LANGUAGE_FOLDER.matchEntire(it.name)?.groupValues?.get(1) }
            .sorted()
    }

    @Test
    fun germanIsATranslation() {
        assertTrue(languages.toString(), "de" in languages)
        assertTrue(english.isNotEmpty())
    }

    @Test
    fun everyLanguageHasTheKeysOfTheDefault() {
        for (language in languages) {
            val keys = strings("values-$language").keys
            assertEquals("missing in values-$language", emptySet<String>(), english.keys - keys)
            assertEquals("not in values/", emptySet<String>(), keys - english.keys)
        }
    }

    @Test
    fun everyKeyHasTheSamePlaceholdersInEveryLanguage() {
        for (language in languages) {
            for ((key, text) in strings("values-$language")) {
                val default = english[key] ?: continue
                assertEquals("$key in values-$language", placeholders(default), placeholders(text))
            }
        }
    }

    @Test
    fun everyPercentSignIsAPlaceholderOrEscapedNextToOne() {
        for (folder in listOf("values") + languages.map { "values-$it" }) {
            val problems = strings(folder).flatMap { (key, text) -> percentProblems(key, text) }
            assertEquals(folder, emptyList<String>(), problems)
        }
    }

    @Test
    fun localesConfigListsEnglishAndEveryValuesFolder() {
        val config = File(res, "xml/locales_config.xml")
        val locales = elements(config, "locale").map { it.getAttribute("android:name") }
        assertEquals((listOf(DEFAULT_LANGUAGE) + languages).sorted(), locales.sorted())
    }

    @Test
    fun formatLocaleIsTheLanguageOfTheFolder() {
        assertEquals(DEFAULT_LANGUAGE, english[FORMAT_LOCALE])
        for (language in languages) {
            assertEquals(language, strings("values-$language")[FORMAT_LOCALE])
        }
    }

    /** Every `*` opens or closes a bold part of a panel line (`*OK* wählt`), so each text has an even number. */
    @Test
    fun everyBoldMarkIsClosed() {
        for (folder in listOf("values") + languages.map { "values-$it" }) {
            val open = strings(folder).filterValues { text -> text.count { it == '*' } % 2 != 0 }.keys
            assertEquals(folder, emptySet<String>(), open - unpairedStars)
        }
    }

    @Test
    fun englishTextsKeepTheSteRules() {
        assertEquals(emptyList<String>(), english.flatMap { (key, text) -> steProblems(key, text) })
    }

    @Test
    fun placeholdersCompareByIndexAndType() {
        assertEquals(listOf("%1\$d", "%2\$s"), placeholders("%2\$s and %1\$d, again %1\$d %%"))
        assertTrue(placeholders("%1\$s") != placeholders("%1\$d"))
    }

    @Test
    fun aLonePercentSignFails() {
        assertEquals(1, percentProblems("k", "50 % off").size)
        assertEquals(1, percentProblems("k", "%1\$d %").size)
        assertEquals(1, percentProblems("k", "%s left").size)
    }

    @Test
    fun anEscapedPercentSignNeedsAPlaceholder() {
        assertEquals(1, percentProblems("k", "100 %%").size)
        assertEquals(emptyList<String>(), percentProblems("k", "%1\$d %%"))
        assertEquals(emptyList<String>(), percentProblems("k", "no sign"))
    }

    @Test
    fun steFindsLongSentencesAndBannedWords() {
        val long = List(STE_MAX_WORDS + 1) { "word" }.joinToString(" ")
        assertEquals(1, steProblems("k", "$long. Short one.").size)
        assertEquals(emptyList<String>(), steProblems("k", List(STE_MAX_WORDS) { "word" }.joinToString(" ") + "."))
        for (banned in listOf("should", "Ideally", "if possible", "etc.", "Please", "maybe")) {
            assertEquals(banned, 1, steProblems("k", "Press OK, $banned now").size)
        }
        assertEquals(emptyList<String>(), steProblems("k", "Shoulder the etcetera."))
    }

    private fun strings(folder: String): Map<String, String> =
        elements(File(res, "$folder/$STRINGS"), "string").associate { it.getAttribute("name") to it.textContent }

    private fun elements(file: File, tag: String): List<Element> {
        assertTrue("$file missing", file.isFile)
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName(tag)
        return List(nodes.length) { nodes.item(it) as Element }
    }

    private companion object {
        const val STRINGS = "strings.xml"
        const val DEFAULT_LANGUAGE = "en"
        const val FORMAT_LOCALE = "format_locale"
        const val STE_MAX_WORDS = 25
        val LANGUAGE_FOLDER = Regex("values-([a-z]{2,3})")
        val PLACEHOLDER = Regex("""%\d+\$[sd]""")
        val SENTENCE_END = Regex("""(?<=[.!?])\s+""")
        val BANNED = Regex("""\b(should|ideally|if possible|please|maybe)\b|\betc\.""", RegexOption.IGNORE_CASE)

        /** The positional placeholders of [text], each once, sorted: `%1$d`, `%2$s`. */
        fun placeholders(text: String): List<String> =
            PLACEHOLDER.findAll(text).map { it.value }.distinct().sorted().toList()

        /**
         * A `%` that is neither `%%` nor `%n$s`/`%n$d` fails. `%%` fails in a text without placeholders, because
         * `stringResource(id)` without arguments shows it as `%%`.
         */
        fun percentProblems(key: String, text: String): List<String> {
            val rest = PLACEHOLDER.replace(text, "")
            return listOfNotNull(
                "$key: lone % in \"$text\"".takeIf { rest.replace("%%", "").contains('%') },
                "$key: %% without a placeholder in \"$text\"".takeIf { "%%" in rest && rest == text },
            )
        }

        /** At most [STE_MAX_WORDS] words per sentence and none of the [BANNED] words. */
        fun steProblems(key: String, text: String): List<String> {
            val long = text.split(SENTENCE_END).filter { words(it) > STE_MAX_WORDS }
            val banned = BANNED.findAll(text).map { it.value }.toList()
            return long.map { "$key: over $STE_MAX_WORDS words: $it" } + banned.map { "$key: \"$it\" in \"$text\"" }
        }

        fun words(sentence: String): Int = sentence.split(Regex("""\s+""")).count { it.isNotBlank() }
    }
}
