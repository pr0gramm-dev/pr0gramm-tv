package dev.pr0gramm.tv.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.pr0gramm.tv.R
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.data.catalogsync.CachedCatalog

/** The catalog whose pictures the tiles show; the start page provides it. */
val LocalCatalog = staticCompositionLocalOf<CachedCatalog> { error("no catalog provided") }

/**
 * What a tile shows inside its frame (`.art` in `final.html`): the picture or a generated face; for apps the state
 * badge and the loading bar (AC9). Title and sub line (with an entry's site) are below the frame, see [TitleTag].
 */
@Composable
internal fun TileFace(tile: Tile, onDrawn: () -> Unit) {
    val catalog = LocalCatalog.current
    when (tile) {
        is Tile.Site -> {
            val model = tile.site.banner?.let(catalog::image)
            Banner(model, tile.site.name, tile.site.color?.let(::Color) ?: Night.Surface1, onDrawn)
        }
        is Tile.App -> {
            val model = tile.entry.banner?.let(catalog::image)
            Banner(model, tile.entry.name, Night.Surface2, onDrawn)
            if (tile.state.dimmed) Box(Modifier.fillMaxSize().background(Night.Bg.copy(alpha = DIM)))
            AppBadge(tile.state)
        }
        is Tile.Saved -> {
            val model: Any? = tile.entry.image ?: tile.site.banner?.let(catalog::image)
            Banner(model, tile.entry.title, tile.site.color?.let(::Color) ?: Night.Surface1, onDrawn)
        }
    }
}

private val AppState.dimmed: Boolean get() = this !is AppState.Installed && this !is AppState.Update

/** The picture, or a generated face (name on a colour field) when there is none (AC1). */
@Composable
private fun Banner(model: Any?, name: String, color: Color, onDrawn: () -> Unit) {
    if (model == null) {
        val stage = LocalStage.current
        Box(Modifier.fillMaxSize().background(color), Alignment.Center) {
            Text(
                name,
                fontSize = stage.sp(NAME_SIZE),
                fontWeight = FontWeight.ExtraBold,
                color = Night.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(stage.dp(NAME_PAD)),
            )
        }
        LaunchedEffect(Unit) { onDrawn() }
    } else {
        AsyncImage(
            model = model,
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { onDrawn() },
            onError = { onDrawn() },
        )
    }
}

/** "Install", "Update" or "Error" at the top left, and the loading bar at the bottom, like final.html. */
@Composable
private fun AppBadge(state: AppState) {
    val stage = LocalStage.current
    Box(Modifier.fillMaxSize().padding(stage.dp(BADGE_INSET))) {
        when (state) {
            AppState.Missing ->
                Badge(stringResource(R.string.tile_badge_install), Night.Bg.copy(alpha = BADGE_ALPHA), Night.Text)
            AppState.Failed -> Badge(stringResource(R.string.tile_badge_error), ERROR, Night.OnFocus)
            AppState.Update -> Badge(stringResource(R.string.tile_badge_update), Night.Text, Night.Bg)
            is AppState.Running -> LoadingBar(state.percent, Modifier.align(Alignment.BottomStart))
            AppState.Waiting, AppState.Installed -> Unit
        }
    }
}

@Composable
private fun Badge(text: String, fill: Color, ink: Color) {
    val stage = LocalStage.current
    Text(
        text,
        fontSize = stage.sp(BADGE_SIZE),
        fontWeight = FontWeight.Bold,
        color = ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.background(fill).padding(stage.dp(BADGE_H), stage.dp(BADGE_V)),
    )
}

@Composable
private fun LoadingBar(percent: Int, modifier: Modifier) {
    val stage = LocalStage.current
    Box(modifier.fillMaxWidth().height(stage.dp(BAR_HEIGHT)).background(Night.Bg.copy(alpha = BAR_ALPHA))) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, PERCENT_MAX) / PERCENT_MAX.toFloat())
                .fillMaxHeight()
                .background(Night.Text),
        )
    }
}

private val ERROR = Color(0xFFFF5A5F)
private const val PERCENT_MAX = 100
private const val DIM = 0.35f
private const val BADGE_ALPHA = 0.88f
private const val BAR_ALPHA = 0.7f
private const val NAME_SIZE = 44
private const val NAME_PAD = 18
private const val BADGE_INSET = 5
private const val BADGE_SIZE = 18
private const val BADGE_H = 12
private const val BADGE_V = 4
private const val BAR_HEIGHT = 8
