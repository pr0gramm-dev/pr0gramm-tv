import dev.pr0gramm.tv.data.manifest.Manifest
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import dev.pr0gramm.tv.data.manifest.ManifestResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** build-logic compiles the app's `data/manifest/` directory, so the build judges a manifest with the app's parser. */
class SharedCatalogTest {
    @Test fun `a valid manifest parses`() {
        val text = """{"version": 1, "date": "2026-11-18", "sites": [{"id": "nova", "name": "Nova", "url": "https://nova.example/"}],
            "apps": []}"""
        val result = Manifest.parse(text)
        assertTrue(result is ManifestResult.Valid, result.toString())
        assertEquals(listOf("nova"), (result as ManifestResult.Valid).manifest.sites.map { it.id.value })
    }

    @Test fun `an invalid manifest lists its problems`() {
        val result = Manifest.parse("""{"version": 0, "date": "2026-11-18", "sites": [], "apps": []}""")
        val problem = ManifestProblem("version", ManifestProblem.Kind.VERSION_RANGE, listOf("0", "2147483647"))
        assertEquals(ManifestResult.Invalid(listOf(problem)), result)
        assertEquals("version: 0 must be between 1 and 2147483647", problem.english())
    }
}
