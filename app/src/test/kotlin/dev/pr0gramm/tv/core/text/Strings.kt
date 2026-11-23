package dev.pr0gramm.tv.core.text

import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.ui.launcher.settings.Help
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The texts of one `strings.xml` for JVM tests, without Robolectric (AC11). [dir] is a `values*` folder; unit tests
 * run in the module directory. The table finds the key of an id through the fields of `R.string` and reads each
 * value as aapt2 does (quotes, whitespace, `\` escapes).
 */
class StringTable(dir: String) {
    private val file = File(dir, "strings.xml")
    private val argPattern = Regex("""%(\d+)\$[sd]""")

    private val texts: Map<String, String> by lazy {
        check(file.isFile) { "$file missing" }
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        val elements = List(nodes.length) { nodes.item(it) as Element }
        elements.associate { it.getAttribute("name") to unescape(it.textContent) }
    }

    /** [text] as the app shows it in the language of this table; fails when its args do not match the placeholders. */
    operator fun invoke(text: UiText): String {
        checkArgs(text)
        return text.resolve(::raw)
    }

    /**
     * `String.format` ignores an extra arg, and a missing one fails only on the device. The table fails on both: the
     * args must be the placeholders `%1$…` to `%n$…` of the resource, each once.
     */
    private fun checkArgs(text: UiText) {
        if (text !is UiText.Res) return
        val used = argPattern.findAll(raw(text.id)).map { it.groupValues[1].toInt() }.toSet()
        check(used == (1..text.args.size).toSet()) { "resource ${text.id} uses args $used, got ${text.args.size}" }
        text.args.forEach { if (it is UiText) checkArgs(it) }
    }

    /** All of the text of [help] in the language of this table, in panel order, for tests and checks. */
    fun words(help: Help): String = buildList {
        help.intro?.let(::add)
        help.topics.forEach { addAll(listOf(it.name, it.text)) }
        help.options.forEach { addAll(listOf(it.name, it.text)) }
        help.note?.let(::add)
    }.joinToString(" ") { invoke(it) }

    /** The value of the resource [id] before `String.format`, as `Resources.getString(id)` returns it. */
    fun raw(id: Int): String {
        val key = KEYS[id] ?: error("$id is no R.string id")
        return texts[key] ?: error("$key missing in $file")
    }

    companion object {
        /**
         * The chars aapt2 joins and trims. aapt2 37.0.0 keeps every other space char as it is, for example U+00A0,
         * U+202F, U+2009 and U+3000 (review item 39); `Char.isWhitespace` would join them too.
         */
        private const val XML_SPACE = " \t\n\r"

        private val KEYS: Map<Int, String> by lazy {
            R.string::class.java.fields.associate { it.getInt(null) to it.name }
        }

        /**
         * The text aapt2 stores for the XML text [value]: outside `"…"` runs of [XML_SPACE] become one space and the
         * ends are trimmed; `"` itself is dropped; `\n`, `\t` and `\uXXXX` are escapes, `\` before any other char
         * keeps that char.
         */
        fun unescape(value: String): String {
            val out = StringBuilder()
            var quoted = false
            var space = false
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (!quoted && c in XML_SPACE) {
                    space = true
                    i++
                    continue
                }
                if (space && out.isNotEmpty()) out.append(' ')
                space = false
                when {
                    c == '"' -> quoted = !quoted
                    c == '\\' && i + 1 < value.length -> i = escape(value, i + 1, out)
                    else -> out.append(c)
                }
                i++
            }
            return out.toString()
        }

        /** Appends the escape whose char is at [at] in [value] to [out]; returns the index of its last char. */
        private fun escape(value: String, at: Int, out: StringBuilder): Int {
            when (val next = value[at]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'u' -> {
                    val hex = value.substring(at + 1, minOf(at + 5, value.length))
                    require(hex.length == 4 && hex.all { it in HEX }) { "bad \\u escape in \"$value\"" }
                    out.append(hex.toInt(16).toChar())
                    return at + 4
                }
                else -> out.append(next)
            }
            return at
        }

        private const val HEX = "0123456789abcdefABCDEF"
    }
}

/** The English and the German texts of the app. */
object Strings {
    val en = StringTable("src/main/res/values")
    val de = StringTable("src/main/res/values-de")
}
