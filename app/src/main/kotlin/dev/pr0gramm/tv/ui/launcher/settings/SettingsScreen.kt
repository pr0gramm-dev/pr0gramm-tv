package dev.pr0gramm.tv.ui.launcher.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.text.asString
import dev.pr0gramm.tv.core.text.text

/**
 * One site's settings (AC4). Up and Down choose a row; OK steps it once per press (toggles, or one zoom step
 * up); Left and Right step the zoom down and up. Changes apply at once. Back is handled by the activity.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val rows = state.rows
    val scope = text(R.string.settings_site_scope, state.title)
    NightPage(
        title = state.title,
        hint = stringResource(R.string.settings_site_hint),
        size = rows.size,
        modifier = modifier,
        help = { RowHelp(rows[it].label, rows[it].help, scope) },
        onOk = {
            when (val setting = rows[it].setting) {
                Setting.RESET -> viewModel.reset()
                else -> viewModel.step(setting)
            }
        },
        onStep = { index, direction ->
            if (rows[index].setting == Setting.ZOOM) viewModel.step(Setting.ZOOM, direction)
        },
    ) { selected ->
        rows.forEachIndexed { index, row ->
            NightItem(row.label.asString(), row.value.asString(), index == selected, row.style, row.on)
        }
    }
}
