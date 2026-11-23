package dev.pr0gramm.tv.data.manifest

import dev.pr0gramm.tv.data.manifest.ManifestProblem.Kind

/**
 * A strict JSON tree (RFC 8259), read by the one small reader of the manifest. App and build-logic share it, so the
 * same file gets the same verdict on the TV and in the build. Duplicate keys are an error; numbers stay as written.
 */
internal sealed interface Json {
    /** Fields in the order of the file. */
    data class Obj(val fields: Map<String, Json>) : Json

    data class Arr(val items: List<Json>) : Json

    data class Str(val value: String) : Json

    /** The literal as written, e.g. `1.50` or `1e3`; the caller decides what a valid number is. */
    data class Num(val raw: String) : Json

    data class Bool(val value: Boolean) : Json

    data object Null : Json

    companion object {
        /** Deeper nesting is an error, so a hostile file cannot overflow the stack. */
        const val MAX_DEPTH = 64

        /** Throws [JsonException]. A leading byte order mark is skipped. */
        fun parse(text: String): Json = JsonReader(text.removePrefix("\uFEFF")).document()
    }
}

/**
 * [at]: index of the offending character in the text (without a leading byte order mark). [kind] is a `JSON_*`
 * kind of [ManifestProblem]; [detail] are its args before the position.
 */
internal class JsonException(
    val at: Int,
    val kind: Kind,
    private val detail: List<String> = emptyList(),
) : IllegalArgumentException("$kind at character $at") {
    /** The problem for the whole file: [kind] with [detail] and the position [at]. */
    val problem: ManifestProblem get() = ManifestProblem("", kind, detail + at.toString())
}

/** Low-level steps over [text]: where the reader stands, spaces, single characters, failures. */
private abstract class JsonCursor(protected val text: String) {
    protected var pos = 0

    protected val current: Char? get() = text.getOrNull(pos)

    /** Skips space, then consumes [c] if it is next. */
    protected fun next(c: Char): Boolean {
        skipSpace()
        return (current == c).also { if (it) pos++ }
    }

    /** Skips space, then consumes [c] or fails. */
    protected fun expect(c: Char) {
        if (!next(c)) fail(Kind.JSON_CHAR_EXPECTED, c.toString())
    }

    protected fun skipSpace() {
        while (current?.let { it in SPACE } == true) pos++
    }

    protected fun word(word: String, value: Json): Json {
        if (!text.startsWith(word, pos)) fail(Kind.JSON_VALUE_EXPECTED)
        pos += word.length
        return value
    }

    protected fun fail(kind: Kind, vararg detail: String, at: Int = pos): Nothing =
        throw JsonException(at, kind, detail.toList())

    private companion object {
        const val SPACE = " \t\r\n"
    }
}

private class JsonReader(text: String) : JsonCursor(text) {
    fun document(): Json {
        val value = value(depth = 0)
        skipSpace()
        if (pos < text.length) fail(Kind.JSON_TEXT_AFTER_END)
        return value
    }

    private fun value(depth: Int): Json {
        if (depth > Json.MAX_DEPTH) fail(Kind.JSON_TOO_DEEP, Json.MAX_DEPTH.toString())
        skipSpace()
        return when (current) {
            '{' -> obj(depth)
            '[' -> arr(depth)
            '"' -> Json.Str(string())
            't' -> word("true", Json.Bool(true))
            'f' -> word("false", Json.Bool(false))
            'n' -> word("null", Json.Null)
            else -> number()
        }
    }

    private fun obj(depth: Int): Json.Obj {
        pos++
        val fields = LinkedHashMap<String, Json>()
        if (!next('}')) {
            do {
                skipSpace()
                val at = pos
                if (current != '"') fail(Kind.JSON_KEY_EXPECTED)
                val key = string()
                expect(':')
                if (fields.put(key, value(depth + 1)) != null) fail(Kind.JSON_DUPLICATE_KEY, key, at = at)
            } while (next(','))
            expect('}')
        }
        return Json.Obj(fields)
    }

    private fun arr(depth: Int): Json.Arr {
        pos++
        val items = mutableListOf<Json>()
        if (!next(']')) {
            do {
                items += value(depth + 1)
            } while (next(','))
            expect(']')
        }
        return Json.Arr(items)
    }

    private fun string(): String {
        pos++
        val out = StringBuilder()
        while (true) {
            val c = current ?: fail(Kind.JSON_UNCLOSED_STRING)
            pos++
            when {
                c == '"' -> return out.toString()
                c == '\\' -> out.append(escape())
                c < ' ' -> fail(Kind.JSON_CONTROL_CHAR, at = pos - 1)
                else -> out.append(c)
            }
        }
    }

    private fun escape(): Char {
        val c = current ?: fail(Kind.JSON_ESCAPE_END)
        pos++
        ESCAPES[c]?.let { return it }
        if (c != 'u') fail(Kind.JSON_UNKNOWN_ESCAPE, c.toString(), at = pos - 1)
        val hex = text.substring(pos, minOf(pos + HEX_DIGITS, text.length))
        if (hex.length < HEX_DIGITS || hex.any { it !in HEX_CHARS }) fail(Kind.JSON_BAD_UNICODE)
        pos += HEX_DIGITS
        return hex.toInt(HEX).toChar()
    }

    private fun number(): Json.Num {
        val match = NUMBER.matchAt(text, pos) ?: fail(Kind.JSON_VALUE_EXPECTED)
        pos = match.range.last + 1
        return Json.Num(match.value)
    }

    private companion object {
        const val HEX = 16
        const val HEX_DIGITS = 4
        const val HEX_CHARS = "0123456789abcdefABCDEF"

        // A number must not run on into letters, digits or signs: `01`, `1.`, `1e` and `0x1` are errors.
        val NUMBER = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?(?![0-9A-Za-z.+-])")
        val ESCAPES = mapOf(
            '"' to '"',
            '\\' to '\\',
            '/' to '/',
            'b' to '\b',
            'f' to '\u000c',
            'n' to '\n',
            'r' to '\r',
            't' to '\t',
        )
    }
}
