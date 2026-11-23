package dev.pr0gramm.tv.data.manifest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A hostile file cannot fill the memory: the problem list and its texts stay bounded (see [Fields]). */
class ManifestLimitsTest {
    @Test
    fun aHostileFileKeepsItsProblemsBounded() {
        // A long id is a valid id. Each bad allow entry of that site is one problem that names the site.
        val longId = "x".repeat(100_000)
        val allow = List(2_000) { "\"!\"" }.joinToString(", ", "[", "]")
        val site = """{"id": "$longId", "name": "N", "url": "https://a.example/", "allow": $allow}"""
        val text = """{"version": 3, "date": "2026-11-18", "sites": [$site], "apps": []}"""
        val problems = (Manifest.parse(text) as ManifestResult.Invalid).problems
        assertEquals(Fields.MAX_PROBLEMS, problems.size)
        val longest = problems.maxOf { it.where.length + it.args.sumOf(String::length) }
        assertTrue("$longest characters", longest < Fields.MAX_NAME + Fields.MAX_ARG + 100)
    }
}
