package dev.pr0gramm.tv.data.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class JsonTest {
    @Test
    fun readsTheWholeTree() {
        val json = Json.parse("""{"a": [1, "x", true, false, null], "b": {}, "c": []}""")
        val expected = Json.Obj(
            mapOf(
                "a" to Json.Arr(listOf(Json.Num("1"), Json.Str("x"), Json.Bool(true), Json.Bool(false), Json.Null)),
                "b" to Json.Obj(emptyMap()),
                "c" to Json.Arr(emptyList()),
            ),
        )
        assertEquals(expected, json)
    }

    @Test
    fun keepsTheOrderOfFields() {
        val json = Json.parse("""{"z": 1, "a": 2, "m": 3}""") as Json.Obj
        assertEquals(listOf("z", "a", "m"), json.fields.keys.toList())
    }

    @Test
    fun decodesEscapes() {
        val json = Json.parse(""""q\" b\\ s\/ \b\f\n\r\t \u00e4 \ud83d\ude00"""")
        assertEquals(Json.Str("q\" b\\ s/ \b\u000c\n\r\t ä \uD83D\uDE00"), json)
    }

    @Test
    fun keepsTextOutsideAscii() {
        assertEquals(Json.Str("Grüße 😀"), Json.parse("\"Grüße 😀\""))
    }

    @Test
    fun keepsNumbersAsWritten() {
        listOf("0", "-0", "12", "-12", "1.5", "1.50", "1e3", "1E+3", "2.5e-3", "12345678901234567890123").forEach {
            assertEquals(Json.Num(it), Json.parse(it))
        }
    }

    @Test
    fun allowsWhitespaceAndALeadingByteOrderMark() {
        assertEquals(Json.Arr(listOf(Json.Num("1"))), Json.parse("\uFEFF \t\r\n[ 1 ]\n"))
    }

    @Test
    fun rejectsBrokenText() {
        val broken = listOf(
            "", " ", "{", "}", "[1,]", "{\"a\":1,}", "[1 2]", "{\"a\" 1}", "{a: 1}", "{\"a\":}", "[,1]",
            "01", "-01", "1.", ".5", "+1", "-", "1e", "1e+", "NaN", "Infinity", "0x10",
            "tru", "nul", "True", "'a'", "\"a", "\"\\x\"", "\"\\u12\"", "\"\\u12g4\"", "\"a\tb\"", "\"a\nb\"",
            "{} x", "1 2", "[]]",
        )
        broken.forEach { text ->
            try {
                Json.parse(text)
                fail("accepted: $text")
            } catch (_: JsonException) {
                // expected
            }
        }
    }

    @Test
    fun rejectsDuplicateKeys() {
        val e = assertThrows(JsonException::class.java) { Json.parse("""{"a": 1, "a": 2}""") }
        assertEquals(ManifestProblem("", ManifestProblem.Kind.JSON_DUPLICATE_KEY, listOf("a", "9")), e.problem)
    }

    @Test
    fun rejectsDeepNestingWithoutCrashing() {
        val deep = "[".repeat(10_000) + "]".repeat(10_000)
        assertThrows(JsonException::class.java) { Json.parse(deep) }
    }

    @Test
    fun allowsReasonableNesting() {
        val nested = "[".repeat(Json.MAX_DEPTH) + "]".repeat(Json.MAX_DEPTH)
        Json.parse(nested)
    }

    @Test
    fun namesThePositionOfTheProblem() {
        assertEquals(3, assertThrows(JsonException::class.java) { Json.parse("[1,]") }.at)
        assertEquals(4, assertThrows(JsonException::class.java) { Json.parse("[1, x]") }.at)
    }
}
