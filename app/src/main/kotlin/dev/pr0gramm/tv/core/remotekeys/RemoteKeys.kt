package dev.pr0gramm.tv.core.remotekeys

import androidx.compose.ui.input.key.Key

/** The keys that count as the OK button of the remote (and of a keyboard). */
val OK_KEYS: Set<Key> = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** The gear key of the TCL remote (`KEYCODE_NOTIFICATION`): opens and closes the menu of what is selected. */
val MENU_KEY: Key = Key.Notification

/** The seconds ◀/▶ seek in a fullscreen video. */
const val SEEK_S = 10
