package dev.pr0gramm.tv.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.Text
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.cutShape
import dev.pr0gramm.tv.core.remotekeys.OK_KEYS
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.asString
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.domain.catalogsync.Absence
import dev.pr0gramm.tv.domain.catalogsync.CatalogState
import dev.pr0gramm.tv.ui.launcher.settings.General

/**
 * The start page without a catalog (AC4): the header with the gear ([onSettings]), and below it the [NoCatalogNotice]
 * of [state] with its button: OK reports the [NoCatalogButton] to [onButton]. The focus starts on that button, else
 * on the gear.
 */
@Composable
internal fun NoCatalogScreen(
    state: CatalogState.Absent,
    onButton: (NoCatalogButton) -> Unit,
    onSettings: () -> Unit,
) {
    val stage = LocalStage.current
    val notice = NoCatalogNotice.of(state)
    val gear = remember { FocusRequester() }
    val button = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(Night.Bg)) {
        Header(onSettings, gear = Modifier.focusRequester(gear))
        if (notice != null) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(notice.text.asString(), fontSize = stage.sp(NOTICE_SIZE), color = Night.Text2)
                NoticeButton(notice.button, onButton, Modifier.focusRequester(button))
            }
        }
    }
    LaunchedEffect(notice) { (if (notice != null) button else gear).requestFocus() }
}

/** The button under the notice: [RETRY] asks the server again, [ENTER_SERVER] asks for a server address. */
internal enum class NoCatalogButton(val label: UiText) {
    RETRY(text(R.string.nocatalog_retry)),
    ENTER_SERVER(text(R.string.nocatalog_enter_server)),
}

/** What the start page says without a catalog: [text], and the [button] under it. */
internal data class NoCatalogNotice(val text: UiText, val button: NoCatalogButton) {
    companion object {
        private val NO_SERVER = text(R.string.nocatalog_no_server)

        /** Null while the first refresh runs: the page stays neutral, without an error text (AC4). */
        fun of(state: CatalogState.Absent): NoCatalogNotice? = when (state) {
            CatalogState.Loading -> null
            is CatalogState.Missing -> when (state.reason) {
                Absence.NO_SERVER -> NoCatalogNotice(NO_SERVER, NoCatalogButton.ENTER_SERVER)
                Absence.UNREACHABLE -> NoCatalogNotice(General.failure(Failure.UNREACHABLE), NoCatalogButton.RETRY)
                Absence.REJECTED -> NoCatalogNotice(General.failure(Failure.BAD_INDEX), NoCatalogButton.RETRY)
            }
        }
    }
}

/** OK reports [button] to [onClick]; like the gear, the button only changes colour with the focus. */
@Composable
private fun NoticeButton(button: NoCatalogButton, onClick: (NoCatalogButton) -> Unit, modifier: Modifier) {
    val stage = LocalStage.current
    var focused by remember { mutableStateOf(false) }
    Text(
        button.label.asString(),
        fontSize = stage.sp(BUTTON_SIZE),
        color = if (focused) Night.OnFocus else Night.Text,
        modifier = modifier
            .padding(top = stage.dp(BUTTON_GAP))
            .clip(stage.cutShape(BUTTON_CUT))
            .background(if (focused) Night.Focus else Night.Surface2)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val ok = event.key in OK_KEYS
                if (ok && event.type == KeyEventType.KeyUp) onClick(button)
                ok
            }
            .focusable()
            .padding(stage.dp(BUTTON_H), stage.dp(BUTTON_V)),
    )
}

private const val NOTICE_SIZE = 36
private const val BUTTON_SIZE = 28
private const val BUTTON_GAP = 40
private const val BUTTON_CUT = 10
private const val BUTTON_H = 32
private const val BUTTON_V = 12
