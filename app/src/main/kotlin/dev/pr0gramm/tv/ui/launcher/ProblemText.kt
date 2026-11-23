package dev.pr0gramm.tv.ui.launcher

import androidx.annotation.StringRes
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.data.manifest.ManifestProblem
import dev.pr0gramm.tv.data.manifest.ManifestProblem.Kind

/** A [ManifestProblem] in the app language (AC15): `<where>: <reason>`, or the reason alone for the whole file. */
internal object ProblemText {
    fun of(problem: ManifestProblem): UiText {
        val reason = UiText.Res(resource(problem.kind), problem.args)
        return if (problem.where.isEmpty()) reason else text(R.string.problem_at, UiText.Plain(problem.where), reason)
    }

    @Suppress("CyclomaticComplexMethod") // reason: one branch per Kind; the compiler keeps the when exhaustive
    @StringRes
    private fun resource(kind: Kind): Int = when (kind) {
        Kind.JSON_CHAR_EXPECTED -> R.string.problem_json_char_expected
        Kind.JSON_VALUE_EXPECTED -> R.string.problem_json_value_expected
        Kind.JSON_TEXT_AFTER_END -> R.string.problem_json_text_after_end
        Kind.JSON_TOO_DEEP -> R.string.problem_json_too_deep
        Kind.JSON_KEY_EXPECTED -> R.string.problem_json_key_expected
        Kind.JSON_DUPLICATE_KEY -> R.string.problem_json_duplicate_key
        Kind.JSON_UNCLOSED_STRING -> R.string.problem_json_unclosed_string
        Kind.JSON_CONTROL_CHAR -> R.string.problem_json_control_char
        Kind.JSON_ESCAPE_END -> R.string.problem_json_escape_end
        Kind.JSON_UNKNOWN_ESCAPE -> R.string.problem_json_unknown_escape
        Kind.JSON_BAD_UNICODE -> R.string.problem_json_bad_unicode
        Kind.ROOT_NOT_OBJECT -> R.string.problem_root_not_object
        Kind.TOO_LARGE -> R.string.problem_too_large
        Kind.MISSING -> R.string.problem_missing
        Kind.NOT_STRING -> R.string.problem_not_string
        Kind.BLANK -> R.string.problem_blank
        Kind.NOT_BOOLEAN -> R.string.problem_not_boolean
        Kind.NOT_WHOLE -> R.string.problem_not_whole
        Kind.TOO_BIG -> R.string.problem_too_big
        Kind.NOT_POSITIVE -> R.string.problem_not_positive
        Kind.NOT_STRING_LIST -> R.string.problem_not_string_list
        Kind.BAD_RESOURCE -> R.string.problem_bad_resource
        Kind.VERSION_RANGE -> R.string.problem_version_range
        Kind.BAD_DATE -> R.string.problem_bad_date
        Kind.NOT_LIST -> R.string.problem_not_list
        Kind.NOT_OBJECT -> R.string.problem_not_object
        Kind.BAD_ID -> R.string.problem_bad_id
        Kind.DUPLICATE -> R.string.problem_duplicate
        Kind.BAD_URL -> R.string.problem_bad_url
        Kind.BAD_COLOR -> R.string.problem_bad_color
        Kind.ZOOM_RANGE -> R.string.problem_zoom_range
        Kind.BAD_DOMAIN -> R.string.problem_bad_domain
        Kind.BAD_PACKAGE -> R.string.problem_bad_package
        Kind.OWN_PACKAGE -> R.string.problem_own_package
        Kind.BAD_SHA256 -> R.string.problem_bad_sha256
    }
}
