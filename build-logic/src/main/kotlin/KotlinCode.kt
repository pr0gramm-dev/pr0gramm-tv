/**
 * The code of a Kotlin source file, for checks that must not read comments or literal text (AC2).
 */
object KotlinCode {
    /**
     * [text] with comments (line, nested block, KDoc) and the text of string, raw-string and char literals replaced
     * by spaces. Literal quotes, code inside `${}`, backtick names and every line break stay, so each character keeps
     * its offset and line. A simple template `$name` is blanked: it cannot hold a qualified name.
     */
    fun code(text: String): String = Lexer(text).apply { run() }.code

    /** The text of one string or raw-string literal of a file, and the line (from 1) of its opening quote. */
    data class Literal(val line: Int, val text: String)

    /**
     * Every string and raw-string literal of [text], in the order of their opening quotes. A literal's text is
     * written as in the source: escapes and a simple template `$name` stay. Code in `${}` is left out; a literal
     * inside that code is a literal of its own. Comments, char literals and backtick names hold no literal.
     */
    fun literals(text: String): List<Literal> = Lexer(text).apply { run() }.literals
}

/** One pass over the text; each method starts at its token and leaves [i] after it. */
private class Lexer(private val text: String) {
    private val out = StringBuilder(text.length)
    private var i = 0

    /** The literals found so far by their start; [open] holds the text of each literal that is not closed yet. */
    private val found = mutableListOf<Pair<Int, StringBuilder>>()
    private val open = ArrayDeque<StringBuilder>()

    val code: String get() = out.toString()
    val literals: List<KotlinCode.Literal>
        get() = found.map { (start, literal) -> KotlinCode.Literal(text.lineAt(start), literal.toString()) }

    fun run() = code(inTemplate = false)

    /** Copies code to the end, or in a template up to (not including) the `}` that closes its `${`. */
    private fun code(inTemplate: Boolean) {
        var braces = 0
        while (i < text.length) {
            when {
                at("//") -> lineComment()
                at("/*") -> blockComment()
                at("\"\"\"") -> rawString()
                at("\"") -> string()
                at("'") -> char()
                at("`") -> backtickName()
                at("{") -> {
                    braces++
                    keep(1)
                }
                at("}") && inTemplate && braces == 0 -> return
                at("}") -> {
                    braces--
                    keep(1)
                }
                else -> keep(1)
            }
        }
    }

    private fun lineComment() {
        while (i < text.length && !at("\n")) blank(1)
    }

    private fun blockComment() {
        var depth = 0
        do {
            when {
                at("/*") -> {
                    depth++
                    blank(2)
                }
                at("*/") -> {
                    depth--
                    blank(2)
                }
                else -> blank(1)
            }
        } while (depth > 0 && i < text.length)
    }

    /** A line string ends at its quote; a line break ends an unterminated one, so the next line stays code. */
    private fun string() {
        keep(1)
        startLiteral()
        while (i < text.length && !at("\n")) {
            when {
                at("\"") -> return endLiteral(1)
                at("\${") -> template()
                at("\\") -> literalText(2)
                else -> literalText(1)
            }
        }
        open.removeLast()
    }

    /** Of a run of three or more quotes, the last three end the raw string and the others are text. */
    private fun rawString() {
        keep(3)
        startLiteral()
        while (i < text.length) {
            when {
                at("\"\"\"") -> {
                    var quotes = 0
                    while (at("\"", quotes)) quotes++
                    literalText(quotes - 3)
                    return endLiteral(3)
                }
                at("\${") -> template()
                else -> literalText(1)
            }
        }
        open.removeLast()
    }

    private fun template() {
        keep(2)
        code(inTemplate = true)
        if (i < text.length) keep(1)
    }

    private fun char() {
        keep(1)
        while (i < text.length && !at("\n")) {
            when {
                at("'") -> return keep(1)
                at("\\") -> blank(2)
                else -> blank(1)
            }
        }
    }

    private fun backtickName() {
        keep(1)
        while (i < text.length && !at("\n")) {
            if (at("`")) return keep(1)
            keep(1)
        }
    }

    private fun startLiteral() {
        val literal = StringBuilder()
        found += i to literal
        open.addLast(literal)
    }

    /** Blanks [count] characters of the innermost open literal and adds them to its text. */
    private fun literalText(count: Int) {
        open.last().append(text, i, minOf(i + count, text.length))
        blank(count)
    }

    /** Keeps the [quotes] that close the innermost open literal. */
    private fun endLiteral(quotes: Int) {
        open.removeLast()
        keep(quotes)
    }

    private fun at(token: String, offset: Int = 0) = text.startsWith(token, i + offset)

    private fun keep(count: Int) {
        val end = minOf(i + count, text.length)
        out.append(text, i, end)
        i = end
    }

    private fun blank(count: Int) {
        val end = minOf(i + count, text.length)
        for (c in text.substring(i, end)) out.append(if (c == '\n' || c == '\r') c else ' ')
        i = end
    }
}

private fun String.lineAt(index: Int) = 1 + (0 until index).count { this[it] == '\n' }
