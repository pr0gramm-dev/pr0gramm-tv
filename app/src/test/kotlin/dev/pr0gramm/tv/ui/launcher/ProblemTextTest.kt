package dev.pr0gramm.tv.ui.launcher

import dev.pr0gramm.tv.core.text.Strings
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import dev.pr0gramm.tv.data.manifest.ManifestProblem.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemTextTest {
    /** [kind] at [where] with as many sample args as its arity: `arg0`, `arg1`, … */
    private fun sample(kind: Kind, where: String = "") = ManifestProblem(where, kind, List(kind.arity) { "arg$it" })

    @Test
    fun `the English resource of every kind is the text of english()`() {
        for (kind in Kind.entries) {
            for (where in listOf("", "sites[0] (nova).zoom")) {
                val problem = sample(kind, where)
                assertEquals("$problem", problem.english(), Strings.en(ProblemText.of(problem)))
            }
        }
    }

    @Test
    fun `the German text of every kind holds its place and every arg`() {
        for (kind in Kind.entries) {
            val german = Strings.de(ProblemText.of(sample(kind, "apps[1] (org.kodi).size")))
            assertTrue(german, german.startsWith("apps[1] (org.kodi).size: "))
            for (i in 0 until kind.arity) assertTrue("$kind: $german", german.contains("arg$i"))
            assertFalse("$kind: $german", german.contains('%'))
        }
    }

    @Test
    fun `every JSON problem ends with its character position, in both languages`() {
        // Swapped args keep the placeholder set. The position is the last arg, so it must end the text.
        for (kind in Kind.entries.filter { it.name.startsWith("JSON_") }) {
            val last = "arg${kind.arity - 1}"
            val german = Strings.de(ProblemText.of(sample(kind)))
            val english = Strings.en(ProblemText.of(sample(kind)))
            assertTrue("$kind: $german", german.endsWith("(Zeichen $last)"))
            assertTrue("$kind: $english", english.endsWith("(character $last)"))
        }
    }

    @Test
    fun `a problem of a field reads place, colon, reason`() {
        val zoom = ManifestProblem("sites[0] (nova).zoom", Kind.ZOOM_RANGE, listOf("200", "50", "150"))
        assertEquals("sites[0] (nova).zoom: 200 nicht in 50–150", Strings.de(ProblemText.of(zoom)))
        assertEquals("sites[0] (nova).zoom: 200 not in 50–150", Strings.en(ProblemText.of(zoom)))
    }

    @Test
    fun `a problem of the whole file is the reason alone, escapes included`() {
        val unicode = ManifestProblem("", Kind.JSON_BAD_UNICODE, listOf("3"))
        val german = "kein gültiges JSON: \\u braucht vier Hex-Ziffern (Zeichen 3)"
        assertEquals(german, Strings.de(ProblemText.of(unicode)))
        assertEquals("not valid JSON: \\u needs four hex digits (character 3)", Strings.en(ProblemText.of(unicode)))
        val char = ManifestProblem("", Kind.JSON_CHAR_EXPECTED, listOf(":", "7"))
        assertEquals("kein gültiges JSON: ':' erwartet (Zeichen 7)", Strings.de(ProblemText.of(char)))
        assertEquals("not valid JSON: ':' expected (character 7)", Strings.en(ProblemText.of(char)))
    }
}
