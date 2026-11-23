import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinCodeTest {
    private val ref = "dev.pr0gramm.tv.ui.launcher.Tile"

    /** [KotlinCode.code] of [text]; every result keeps the length and the line breaks of [text]. */
    private fun code(text: String): String {
        val result = KotlinCode.code(text)
        assertEquals(text.length, result.length, "length of $text")
        assertEquals(text.indices.filter { text[it] == '\n' }, result.indices.filter { result[it] == '\n' }, "line breaks of $text")
        return result
    }

    private fun blank(text: String) = " ".repeat(text.length)

    @Test fun `code without comments and literals stays unchanged`() {
        val text = "package a\n\nimport $ref\nimport $ref as T\n\nval x = $ref(1) { it + 2 }\n"
        assertEquals(text, code(text))
    }

    @Test fun `a line comment is blanked up to the line break`() {
        assertEquals("val x = 1 ${blank("// $ref")}\nval y = $ref", code("val x = 1 // $ref\nval y = $ref"))
    }

    @Test fun `a block comment is blanked over lines`() {
        assertEquals("a ${blank("/* x")}\n${blank("$ref */")}b", code("a /* x\n$ref */b"))
    }

    @Test fun `a nested block comment ends at its own end`() {
        val comment = "/* x /* $ref */ y */"
        assertEquals("a ${blank(comment)} $ref", code("a $comment $ref"))
    }

    @Test fun `KDoc is blanked`() {
        val text = "/**\n * See [$ref].\n */\nclass A"
        assertEquals("${blank("/**")}\n${blank(" * See [$ref].")}\n${blank(" */")}\nclass A", code(text))
    }

    @Test fun `the text of a string is blanked, the quotes stay`() {
        assertEquals("val s = \"${blank(ref)}\"", code("val s = \"$ref\""))
    }

    @Test fun `an escaped quote does not end a string`() {
        val content = "a\\\"$ref"
        assertEquals("val s = \"${blank(content)}\"; $ref", code("val s = \"$content\"; $ref"))
    }

    @Test fun `comment markers inside a string are text`() {
        assertEquals("val u = \"${blank("http://x")}\"; $ref", code("val u = \"http://x\"; $ref"))
        assertEquals("val u = \"  \"; $ref", code("val u = \"/*\"; $ref"))
    }

    @Test fun `code in a string template counts`() {
        assertEquals("\"  \${$ref}  \"", code("\"a \${$ref} b\""))
    }

    @Test fun `a template keeps nested braces, strings and its end`() {
        val text = "\"a \${listOf(1).map { \"x\" + $ref }} b\" + $ref"
        assertEquals("\"  \${listOf(1).map { \" \" + $ref }}  \" + $ref", code(text))
    }

    @Test fun `the text of a raw string is blanked over lines`() {
        val line = "  $ref \"quoted\""
        val text = "val s = \"\"\"\n$line\n\"\"\"\n$ref"
        assertEquals("val s = \"\"\"\n${blank(line)}\n\"\"\"\n$ref", code(text))
    }

    @Test fun `code in a raw string template counts`() {
        assertEquals("\"\"\"  \${$ref}  \"\"\"", code("\"\"\"a \${$ref} b\"\"\""))
    }

    @Test fun `extra quotes before the end of a raw string are text`() {
        assertEquals("\"\"\"   \"\"\" + $ref", code("\"\"\"a\"\"\"\"\" + $ref"))
    }

    @Test fun `a char literal is blanked and does not open a string`() {
        assertEquals("val q = ' '; $ref", code("val q = '\"'; $ref"))
        assertEquals("val q = '  '; $ref", code("val q = '\\''; $ref"))
    }

    @Test fun `a quote in a comment does not open a literal`() {
        assertEquals("val x = 1 ${blank("// it's")}\n$ref", code("val x = 1 // it's\n$ref"))
        assertEquals("a ${blank("/* \"x */")}\n$ref", code("a /* \"x */\n$ref"))
    }

    @Test fun `a backtick name stays code`() {
        val text = "fun `a \"b\" // c`() = $ref"
        assertEquals(text, code(text))
    }

    private fun literals(text: String) = KotlinCode.literals(text).map { it.line to it.text }

    @Test fun `literals are the texts of strings and raw strings with their lines`() {
        val text = "val a = \"eins\"\nval b = \"\"\"zwei\n  drei\"\"\"\nval c = \"vier\" + \"\"\n"
        assertEquals(listOf(1 to "eins", 2 to "zwei\n  drei", 4 to "vier", 4 to ""), literals(text))
    }

    @Test fun `comments, char literals and backtick names are no literals`() {
        val text = "// \"a\"\n/* \"b\" */\nval q = '\"'\nfun `c \"d\"`() = 1\n/** \"e\" */\n"
        assertEquals(emptyList<Pair<Int, String>>(), literals(text))
    }

    @Test fun `escapes and simple templates stay as written`() {
        assertEquals(listOf(1 to "a\\\"b \$name"), literals("val s = \"a\\\"b \$name\""))
    }

    @Test fun `template code is left out and a string inside it is its own literal`() {
        val text = "val s = \"a \${f(\"x\")} b\"\nval r = \"\"\"c\n\${g(\"y\")}d\"\"\""
        assertEquals(listOf(1 to "a  b", 1 to "x", 2 to "c\nd", 3 to "y"), literals(text))
    }

    @Test fun `an unterminated string ends at the line break`() {
        assertEquals(listOf(1 to "offen", 2 to "zu"), literals("val s = \"offen\nval t = \"zu\""))
    }
}
