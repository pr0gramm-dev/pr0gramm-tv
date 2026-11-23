package dev.pr0gramm.tv.core.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * This value in the app language; reads the configuration, so a locale change resolves it again. The only place of
 * [UiText] that uses Compose: the pure values in `UiText.kt` stay free of it (review item 28).
 */
@Composable
fun UiText.asString(): String {
    LocalConfiguration.current
    val resources = LocalContext.current.resources
    return resolve(resources::getString)
}
