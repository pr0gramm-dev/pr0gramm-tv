package dev.pr0gramm.tv.ui.launcher.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.GeistMono
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.asString
import androidx.compose.foundation.layout.Row as LayoutRow

/**
 * The general settings (AC10): "Check for updates", "Server", "Pointer look", "Language", "Remote keys" and
 * "About the app".
 * [view] picks the list or the page behind a row: the key table ([ControlsScreen]) or the details of the app.
 * Up and Down choose a row, OK reports [General.press] to [onPress]. Back is handled by the activity.
 */
@Composable
fun GeneralScreen(
    info: GeneralInfo,
    view: GeneralView,
    onPress: (GeneralPress) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Owned here, not by NightPage: the row stays selected while the controls page is shown (Back returns to it).
    val selection = rememberSaveable { mutableIntStateOf(0) }
    if (view == GeneralView.CONTROLS) return ControlsScreen(modifier)
    val about = view == GeneralView.ABOUT
    val rows = General.rows(info)
    // The about page has no rows: no OK action and no help; the selection stays for the way back.
    NightPage(
        title = stringResource(if (about) R.string.general_about_title else R.string.general_title),
        hint = stringResource(if (about) R.string.general_about_hint else R.string.general_list_hint),
        size = if (about) 0 else rows.size,
        modifier = modifier,
        help = { RowHelp(rows[it].label, rows[it].help, SettingsHelp.GENERAL_SCOPE) },
        onOk = { onPress(General.press(rows[it].item, info)) },
        selection = selection,
    ) { selected ->
        if (about) {
            AboutLines(General.about(info))
        } else {
            rows.forEachIndexed { index, row ->
                NightItem(row.label.asString(), row.value.asString(), index == selected)
            }
        }
    }
}

@Composable
private fun AboutLines(lines: List<Pair<UiText, UiText>>) {
    val stage = LocalStage.current
    Column(Modifier.padding(start = stage.dp(ABOUT_INDENT), top = stage.dp(ABOUT_TOP))) {
        for ((label, value) in lines) {
            LayoutRow(Modifier.padding(vertical = stage.dp(ABOUT_GAP))) {
                Text(
                    label.asString(),
                    fontSize = stage.sp(ABOUT_SIZE),
                    color = Night.Text,
                    modifier = Modifier.width(stage.dp(ABOUT_LABEL)),
                )
                Text(value.asString(), fontSize = stage.sp(ABOUT_SIZE), color = Night.Text2, fontFamily = GeistMono)
            }
        }
    }
}

private const val ABOUT_INDENT = 24
private const val ABOUT_TOP = 8
private const val ABOUT_GAP = 7
private const val ABOUT_SIZE = 24
private const val ABOUT_LABEL = 260
