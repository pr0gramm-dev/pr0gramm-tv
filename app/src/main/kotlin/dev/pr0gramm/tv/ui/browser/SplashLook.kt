package dev.pr0gramm.tv.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.pr0gramm.tv.core.designsystem.LocalStage
import dev.pr0gramm.tv.core.designsystem.Night
import dev.pr0gramm.tv.core.designsystem.cutShape
import dev.pr0gramm.tv.data.catalogsync.CachedCatalog
import dev.pr0gramm.tv.data.manifest.Site
import java.io.File

/**
 * What the splash shows while a page loads: [picture] (an entry's image, else the site's banner, null without
 * either), an entry's own [title] (null for a site), and the site's [name] and [color] for a site without a banner.
 */
data class SplashLook(val picture: Any?, val title: String?, val name: String, val color: Int?) {
    companion object {
        /** The banner comes from [catalog] as the tiles do; a banner not downloaded yet leaves the name. */
        fun of(site: Site, catalog: CachedCatalog, title: String?, image: File?): SplashLook =
            SplashLook(image ?: site.banner?.let(catalog::image), title, site.name, site.color)
    }
}

/**
 * Variant B of `oeffnen.html`: the picture fills the screen soft and darkened, sharp and large in the middle,
 * an entry's title below it, then the orange loading line. The background copy is decoded tiny ([BACKDROP_W] px)
 * and scaled up: that makes it soft without a blur (AC13) and costs nearly nothing.
 */
@Composable
internal fun Splash(look: SplashLook, progress: Int) {
    val stage = LocalStage.current
    val width = stage.dp(if (look.title != null) ENTRY_WIDTH else SITE_WIDTH)
    Box(Modifier.fillMaxSize().background(Night.Bg)) {
        if (look.picture != null) {
            val context = LocalPlatformContext.current
            val small = remember(look.picture) {
                ImageRequest.Builder(context).data(look.picture).size(BACKDROP_W, BACKDROP_W * HEIGHT / WIDTH).build()
            }
            AsyncImage(
                model = small,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Night.Bg.copy(alpha = SHADE)))
        }
        Column(
            Modifier.align(Alignment.Center).width(width),
            verticalArrangement = Arrangement.spacedBy(stage.dp(GAP)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Picture(look, Modifier.size(width, width * HEIGHT / WIDTH).clip(stage.cutShape(CUT)))
            if (look.title != null) {
                Text(
                    look.title,
                    fontSize = stage.sp(TITLE_SIZE),
                    fontWeight = FontWeight.ExtraBold,
                    color = Night.Text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.fillMaxWidth().height(stage.dp(LINE)).background(Night.Surface3)) {
                val done = progress.coerceIn(0, FULL) / FULL.toFloat()
                Box(Modifier.fillMaxWidth(done).fillMaxHeight().background(Night.Focus))
            }
        }
    }
}

/** The sharp picture, or the site's name on its colour when there is none. */
@Composable
private fun Picture(look: SplashLook, modifier: Modifier) {
    if (look.picture != null) {
        AsyncImage(look.picture, look.title ?: look.name, modifier, contentScale = ContentScale.Crop)
    } else {
        val stage = LocalStage.current
        Box(modifier.background(look.color?.let(::Color) ?: Night.Surface1), Alignment.Center) {
            Text(
                look.name,
                fontSize = stage.sp(NAME_SIZE),
                fontWeight = FontWeight.Bold,
                color = Night.Text,
                modifier = Modifier.padding(stage.dp(GAP)),
            )
        }
    }
}

private const val SITE_WIDTH = 800
private const val ENTRY_WIDTH = 960
private const val WIDTH = 16
private const val HEIGHT = 9
private const val CUT = 20
private const val GAP = 36
private const val LINE = 6
private const val TITLE_SIZE = 56
private const val NAME_SIZE = 64
private const val BACKDROP_W = 64
private const val SHADE = 0.55f
private const val FULL = 100
