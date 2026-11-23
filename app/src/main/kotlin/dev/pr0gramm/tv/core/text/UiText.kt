package dev.pr0gramm.tv.core.text

import androidx.annotation.StringRes
import dev.pr0gramm.tv.R
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * A text for the user that is not resolved yet. Pure code returns a [UiText]; a composable resolves it with
 * [asString], a JVM test with [resolve] against a `strings.xml` (localisation spec, AC9b, AC11).
 */
sealed interface UiText {
    /** A string resource; [args] are String, Int, Long or UiText (resolved first). */
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText {
        init {
            require(args.all { it is String || it is Int || it is Long || it is UiText }) {
                "args must be String, Int, Long or UiText: $args"
            }
        }
    }

    /** Text that is not the app's: catalog names, page titles, an address, the version. */
    data class Plain(val text: String) : UiText

    /** A date or time, formatted with the pattern resource [pattern] and `format_locale` (AC10). */
    data class Date(@StringRes val pattern: Int, val value: TemporalAccessor) : UiText
}

fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/**
 * The text of this value; [raw] gives the value of a string resource before `String.format`, as
 * `Resources.getString(id)` does. It reads `format_locale`: every [UiText.Res] goes through `String.format` with
 * that locale, never with `Locale.getDefault()`, so `%%` is always one `%`. `AppContainer` reads the same value for
 * the `Accept-Language` header.
 */
fun UiText.resolve(raw: (Int) -> String): String {
    val locale = Locale.forLanguageTag(raw(R.string.format_locale))
    return when (this) {
        is UiText.Plain -> text
        is UiText.Date -> DateTimeFormatter.ofPattern(raw(pattern), locale).format(value)
        is UiText.Res -> {
            val values = args.map { if (it is UiText) it.resolve(raw) else it }
            FORMAT(locale, raw(id), values.toTypedArray())
        }
    }
}

/** `String.format(locale, template, args)` with the args as one array: the reference takes the vararg as an array. */
private val FORMAT: (Locale, String, Array<out Any?>) -> String = java.lang.String::format
