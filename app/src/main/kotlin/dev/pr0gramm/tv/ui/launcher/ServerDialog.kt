package dev.pr0gramm.tv.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.Geist
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.cutShape
import dev.pr0gramm.tv.core.remotekeys.OK_KEYS
import dev.pr0gramm.tv.core.text.UiText
import dev.pr0gramm.tv.core.text.asString
import dev.pr0gramm.tv.core.text.text
import dev.pr0gramm.tv.core.updatefailure.Failure
import dev.pr0gramm.tv.domain.catalogsync.Connect
import dev.pr0gramm.tv.ui.launcher.settings.General
import kotlinx.coroutines.launch

/** The texts of the server dialog. Pure. */
internal object ServerDialog {
    /** Why the address was not saved (AC3): no address, not reachable, or the parser's first problem. */
    fun reason(refused: Connect.Refused): UiText = when (refused) {
        Connect.BadAddress -> text(R.string.server_bad_address)
        Connect.Unreachable -> General.failure(Failure.UNREACHABLE)
        is Connect.Rejected ->
            text(R.string.server_rejected, General.failure(Failure.BAD_INDEX), ProblemText.of(refused.problem))
    }
}

/**
 * The dialog for the server address (AC2, AC3): a text field for the TV keyboard, prefilled with [shown], and the
 * button "Connect". The IME action or OK on the button calls [connect]; [Connect.Saved] goes to [onSaved] with
 * `changed`, a [Connect.Refused] stays in the dialog as [ServerDialog.reason]. Back calls [onClose], except while
 * the check runs. The dialog is its own window, so the focus cannot leave it.
 */
@Composable
internal fun ServerDialog(
    shown: String,
    connect: suspend (String) -> Connect,
    onSaved: (changed: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val stage = LocalStage.current
    val scope = rememberCoroutineScope()
    val field = remember { FocusRequester() }
    var text by remember { mutableStateOf(TextFieldValue(shown, TextRange(shown.length))) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<UiText?>(null) }
    val submit = {
        if (!busy) {
            scope.launch {
                busy = true
                error = null
                when (val outcome = connect(text.text)) {
                    is Connect.Saved -> onSaved(outcome.changed)
                    is Connect.Refused -> error = ServerDialog.reason(outcome)
                }
                busy = false
            }
        }
    }
    // The dialog window gets the IME insets itself (decorFitsSystemWindows = false), and imePadding centres the dialog
    // in the space above the TV keyboard: the button and the error line stay visible while the keyboard is open.
    val properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(stage.dp(DIALOG_WIDTH))
                    .clip(stage.cutShape(DIALOG_CUT))
                    .background(Night.Surface1)
                    .padding(stage.dp(DIALOG_PAD)),
            ) {
                Text(stringResource(R.string.server_title), fontSize = stage.sp(TITLE_SIZE), color = Night.Text)
                Text(
                    stringResource(R.string.server_hint),
                    fontSize = stage.sp(LINE_SIZE),
                    color = Night.Text2,
                    modifier = Modifier.padding(top = stage.dp(GAP)),
                )
                AddressField(text, { text = it }, readOnly = busy, onGo = { submit() }, Modifier.focusRequester(field))
                val line = if (busy) stringResource(R.string.server_busy) else error?.asString()
                if (line != null) {
                    Text(
                        line,
                        fontSize = stage.sp(LINE_SIZE),
                        color = if (busy) Night.Text2 else Night.Danger,
                        modifier = Modifier.padding(top = stage.dp(GAP)),
                    )
                }
                ConnectButton(onClick = { submit() })
            }
        }
        // Inside the dialog's own window: the field's focusRequester is attached only once this content composes.
        LaunchedEffect(Unit) { field.requestFocus() }
    }
}

/** The single-line field for the address; the IME shows its "Go" key, which calls [onGo]. */
@Composable
private fun AddressField(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    readOnly: Boolean,
    onGo: () -> Unit,
    modifier: Modifier,
) {
    val stage = LocalStage.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value,
        onValue,
        modifier = modifier
            .padding(top = stage.dp(GAP))
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        readOnly = readOnly,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Geist, fontSize = stage.sp(FIELD_SIZE), color = Night.Text),
        cursorBrush = SolidColor(Night.Focus),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(stage.cutShape(FIELD_CUT))
                    .background(if (focused) Night.Surface3 else Night.Surface2)
                    .padding(stage.dp(FIELD_PAD)),
            ) { inner() }
        },
    )
}

/** The button "Connect": OK calls [onClick]; like the gear, it only changes colour with the focus. */
@Composable
private fun ConnectButton(onClick: () -> Unit) {
    val stage = LocalStage.current
    var focused by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.server_connect),
        fontSize = stage.sp(BUTTON_SIZE),
        color = if (focused) Night.OnFocus else Night.Text,
        modifier = Modifier
            .padding(top = stage.dp(BUTTON_GAP))
            .clip(stage.cutShape(BUTTON_CUT))
            .background(if (focused) Night.Focus else Night.Surface2)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val ok = event.key in OK_KEYS
                if (ok && event.type == KeyEventType.KeyUp) onClick()
                ok
            }
            .focusable()
            .padding(stage.dp(BUTTON_H), stage.dp(BUTTON_V)),
    )
}

private const val DIALOG_WIDTH = 1000
private const val DIALOG_CUT = 16
private const val DIALOG_PAD = 48
private const val TITLE_SIZE = 40
private const val LINE_SIZE = 26
private const val GAP = 20
private const val FIELD_SIZE = 32
private const val FIELD_CUT = 10
private const val FIELD_PAD = 20
private const val BUTTON_SIZE = 28
private const val BUTTON_GAP = 32
private const val BUTTON_CUT = 10
private const val BUTTON_H = 32
private const val BUTTON_V = 12
