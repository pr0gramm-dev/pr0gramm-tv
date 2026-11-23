import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `docs/architecture.md` §5 shows the table of [PackageGraph] (AC8), so the docs cannot drift from the check. */
class PackageGraphDocsTest {
    private val doc = File("../docs/architecture.md").absoluteFile

    @Test fun `section 5 lists every package of the table in its order`() {
        val section = doc.readText().substringAfter("\n## 5. ", "").substringBefore("\n## 6. ")
        assertTrue(HEADING in section, "$doc: §5 has no \"$HEADING\"")
        val table = section.substringAfter(HEADING).substringBefore("\n### ")
        val documented = table.lines().filter { it.startsWith("| `") }.map { it.substringAfter("| `").substringBefore('`') }
        assertEquals(PackageGraph.PACKAGES, documented)
    }

    private companion object {
        const val HEADING = "\n### Layers, slices and segments"
    }
}
