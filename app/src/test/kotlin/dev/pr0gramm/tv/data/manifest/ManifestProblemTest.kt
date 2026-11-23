package dev.pr0gramm.tv.data.manifest

import dev.pr0gramm.tv.data.manifest.ManifestProblem.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The problems of [Manifest.parse] as types and their English lines for `check-manifest` (AC15). */
class ManifestProblemTest {
    private fun invalid(text: String): List<ManifestProblem> = when (val result = Manifest.parse(text)) {
        is ManifestResult.Valid -> throw AssertionError("accepted: $text")
        is ManifestResult.Invalid -> result.problems
    }

    private fun problems(text: String): List<String> = invalid(text).map(ManifestProblem::english)

    @Test
    fun brokenJsonIsOneProblemOfTheWholeFile() {
        val problem = ManifestProblem("", Kind.JSON_KEY_EXPECTED, listOf("14"))
        assertEquals(listOf(problem), invalid("""{"version": 3,"""))
        assertEquals("not valid JSON: field name in quotes expected (character 14)", problem.english())
    }

    @Test
    fun rootMustBeAnObject() {
        assertEquals(listOf("the top level must be an object"), problems("[]"))
        assertEquals(listOf("the top level must be an object"), problems("\"manifest\""))
    }

    @Test
    fun problemsReadInEnglish() {
        val text = """{"version": 0, "date": "18.11.2026",
            "sites": [{"id": "nova", "name": "Nova", "url": "https://nova.example/", "color": "red", "zoom": 200},
                      {"id": "nova", "name": "Nova", "url": "https://nova.example/"}],
            "apps": [{"package": "org.kodi", "versionCode": 1, "apk": "a.apk", "sha256": "abc", "size": 0}]}"""
        val expected = listOf(
            "version: 0 must be between 1 and 2147483647",
            "date: \"18.11.2026\" is not a date such as 2026-11-18",
            "sites[0] (nova).color: \"red\" must be #RRGGBB",
            "sites[0] (nova).zoom: 200 not in 50–150",
            "sites[1] (nova).id: \"nova\" is not unique",
            "apps[0] (org.kodi).name: missing",
            "apps[0] (org.kodi).sha256: must be 64 hex characters in lower case",
            "apps[0] (org.kodi).size: 0 must be more than 0",
        )
        assertEquals(expected, problems(text))
    }

    @Test
    fun aProblemTakesAsManyArgsAsItsKind() {
        for (kind in Kind.entries) {
            ManifestProblem("", kind, List(kind.arity) { "x" })
            val wrong = List(kind.arity + 1) { "x" }
            assertTrue(kind.name, runCatching { ManifestProblem("", kind, wrong) }.isFailure)
        }
    }

    @Test
    fun anInvalidManifestNamesAProblem() {
        assertTrue(runCatching { ManifestResult.Invalid(emptyList()) }.isFailure)
    }
}
