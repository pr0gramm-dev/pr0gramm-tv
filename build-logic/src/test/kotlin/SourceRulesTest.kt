import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SourceRulesTest {
    private fun lines(count: Int) = (1..count).joinToString("") { "val x$it = $it\n" }

    @Test fun `301 lines is a violation for kt, kts and js`() {
        for (path in listOf("src/main/kotlin/A.kt", "build.gradle.kts", "src/main/assets/js/a.js")) {
            val v = SourceRules.violations(path, lines(301))
            assertEquals(listOf("$path: 301 Zeilen, erlaubt sind höchstens 300"), v)
        }
    }

    @Test fun `300 lines is fine, with or without a trailing newline`() {
        assertEquals(emptyList<String>(), SourceRules.violations("A.kt", lines(300)))
        assertEquals(emptyList<String>(), SourceRules.violations("A.kt", lines(300).trimEnd('\n')))
        assertEquals(emptyList<String>(), SourceRules.violations("A.kt", ""))
    }

    @Test fun `other file types are not checked`() {
        assertEquals(emptyList<String>(), SourceRules.violations("README.md", lines(400)))
        assertEquals(emptyList<String>(), SourceRules.violations("a.json", "@Suppress(\"X\")\n"))
    }

    @Test fun `Suppress without reason is a violation`() {
        val text = "package a\n\n@Suppress(\"MagicNumber\")\nval x = 7\n"
        assertEquals(
            listOf("A.kt:3: @Suppress ohne Begründung (\"// reason: …\" in derselben Zeile oder darüber)"),
            SourceRules.violations("A.kt", text),
        )
    }

    @Test fun `every unexplained suppression form is found`() {
        val text = listOf(
            "@file:Suppress(\"TooManyFunctions\")",
            "@SuppressWarnings(\"unchecked\")",
            "fun f(@Suppress(\"UNUSED_PARAMETER\") a: Int) = 1",
            "@get:Suppress(\"X\") val y = 2",
            "@Suppress // reason:",
            "@SuppressLint(\"RestrictedApi\")",
            "@file:SuppressLint(\"X\")",
        ).joinToString("\n")
        val v = SourceRules.violations("build.gradle.kts", text)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), v.map { it.substringAfter(".kts:").substringBefore(':').toInt() }, v.toString())
    }

    @Test fun `Suppress with reason on the same line or the line above is fine`() {
        val text = """
            @Suppress("MagicNumber") // reason: 600 ms is the spec's hold time
            val hold = 600
            // reason: Compose names composables in PascalCase
            @Suppress("FunctionNaming")
            fun Tile() = Unit
        """.trimIndent()
        assertEquals(emptyList<String>(), SourceRules.violations("A.kt", text))
    }

    @Test fun `a reason belongs to its own suppression only`() {
        val text = "@Suppress(\"A\") // reason: needed for A\n@Suppress(\"B\")\nval x = 1\n"
        assertEquals(listOf(2), SourceRules.violations("A.kt", text).map { it.split(':')[1].toInt() })
    }

    @Test fun `mentions inside strings and identifiers are not suppressions`() {
        val text = "val s = \"@Suppress(\\\"X\\\")\"\nval mySuppress = 1\nfun suppressAll() = Unit\n"
        assertEquals(emptyList<String>(), SourceRules.violations("A.kt", text))
    }

    private val app = "app/src/main/kotlin/dev/pr0gramm/tv/ui/A.kt"

    @Test fun `a literal with a German letter or quote mark is a violation in the app code`() {
        for (literal in listOf("Größe", "Fuß", "Ärger", "„Zitat“")) {
            val v = SourceRules.violations(app, "val s =\n    \"$literal\"\n", allowed = emptySet())
            assertEquals(listOf("$app:2: deutscher Text \"$literal\" $HINT"), v)
        }
    }

    @Test fun `a literal with a German word is a violation, in any case`() {
        val literals = listOf("\$file nicht ersetzbar", "Die Seite", "Meine LISTE", "kein HTTP: \$url", "x\\nnicht", "mehr als 5")
        for (literal in literals) {
            assertEquals(1, SourceRules.violations(app, "val s = \"$literal\"", allowed = emptySet()).size, literal)
        }
    }

    @Test fun `German UI texts without an umlaut from before the localisation are violations`() {
        val literals = listOf(
            "Wiederholen", "Server eingeben", "Nach Updates suchen", "Installation fehlgeschlagen",
            "Werbung blockiert", "Vollbild verlassen", "Zu Maus wechseln", "App verlassen", "Verbinden",
            "Aktualisieren", "Gespeichert auf \$site", "muss ein String sein", "Wert erwartet",
        )
        for (literal in literals) {
            assertEquals(1, SourceRules.violations(app, "val s = \"$literal\"", allowed = emptySet()).size, literal)
        }
    }

    @Test fun `an escaped umlaut is a German literal too`() {
        for (literal in listOf("Gr\\u00f6\\u00dfe", "Men\\u00fc", "\\u00dcber", "Sch\\u00fctzen")) {
            assertEquals(1, SourceRules.violations(app, "val s = \"$literal\"", allowed = emptySet()).size, literal)
        }
    }

    @Test fun `English literals, parts of words, comments and code are fine`() {
        val text = """
            // nicht für "die" Liste
            /** "Größe" */
            val diet = "not HTTP: ${'$'}url" // gestern
            val isDas = "distance, undo, dieser"
            val x = "${'$'}{nicht}"
        """.trimIndent()
        assertEquals(emptyList<String>(), SourceRules.violations(app, text, allowed = emptySet()))
    }

    @Test fun `German literals outside the app's main code are not checked`() {
        for (path in listOf("app/src/test/kotlin/A.kt", "app/build.gradle.kts", "app/src/main/assets/js/a.js")) {
            val v = SourceRules.violations(path, "val s = \"nicht für\"", allowed = emptySet())
            assertEquals(emptyList<String>(), v, path)
        }
    }

    @Test fun `an allowed literal is fine in its own file only`() {
        val allowed = setOf("ui/A.kt:Deutsch für")
        assertEquals(emptyList<String>(), SourceRules.violations(app, "val s = \"Deutsch für\"", allowed))
        val other = app.replace("A.kt", "B.kt")
        assertEquals(1, SourceRules.violations(other, "val s = \"Deutsch für\"", allowed).size)
    }

    @Test fun `an allowed entry that matches no German literal is a violation`() {
        val files = mapOf(app to "val s = \"Deutsch für\"\nval t = \"Deutsch\"")
        val allowed = setOf("ui/A.kt:Deutsch für", "ui/A.kt:Deutsch", "ui/A.kt:nicht", "ui/Gone.kt:für")
        assertEquals(
            listOf(
                "GERMAN_ALLOWED: \"ui/A.kt:Deutsch\" passt auf kein deutsches Literal",
                "GERMAN_ALLOWED: \"ui/A.kt:nicht\" passt auf kein deutsches Literal",
                "GERMAN_ALLOWED: \"ui/Gone.kt:für\" passt auf kein deutsches Literal",
            ),
            SourceRules.unusedAllowed(files, allowed),
        )
    }

    @Test fun `both rules report together`() {
        val text = "@Suppress(\"X\")\n" + lines(300)
        val v = SourceRules.violations("A.kt", text)
        assertEquals(2, v.size, v.toString())
        assertTrue(v.any { it.startsWith("A.kt: 301 Zeilen") }, v.toString())
        assertTrue(v.any { it.startsWith("A.kt:1: @Suppress") }, v.toString())
    }

    private companion object {
        const val HINT = "(String-Ressource nutzen oder in GERMAN_ALLOWED)"
    }
}
