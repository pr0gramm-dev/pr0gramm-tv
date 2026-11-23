package dev.pr0gramm.tv.core.designsystem

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.darkColorScheme
import dev.pr0gramm.tv.R

/** The colours of the design system "Nacht" (system.html). Orange is the focus and nothing else. */
object Night {
    val Bg = Color(0xFF0E0E10)
    val Surface1 = Color(0xFF17171A)
    val Surface2 = Color(0xFF212125)
    val Surface3 = Color(0xFF2D2D32)
    val Text = Color(0xFFF5F5F7)
    val Text2 = Color(0xFFB4B4BC)
    val Text3 = Color(0xFF85858E)
    val Focus = Color(0xFFFF6B1A)
    val OnFocus = Color(0xFF120800)
    val Star = Color(0xFFFFB020)
    val Danger = Color(0xFFFF5A5F)
}

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_bold, FontWeight.Bold),
    Font(R.font.geist_extrabold, FontWeight.ExtraBold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    Font(R.font.geist_mono_extrabold, FontWeight.ExtraBold),
)

/** The shape "Schnitt": square, top right and bottom left cut off diagonally. [cut] is in stage pixels. */
fun Stage.cutShape(cut: Int): Shape = CutCornerShape(topEnd = dp(cut), bottomStart = dp(cut))

/** The slanted title tag: a parallelogram with a slant of [slant] stage pixels. */
class TagShape(private val slant: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { slant.toPx() }
        return Outline.Generic(
            Path().apply {
                moveTo(s, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - s, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}

/**
 * The layout is drawn for a 1920 x 1080 stage (like the mockup) and scaled to the real width, so it looks the
 * same at any TV density. [scale] is stage pixels to dp.
 */
@Immutable
class Stage(val scale: Float) {
    fun dp(stagePx: Int): Dp = (stagePx * scale).dp

    fun sp(stagePx: Int): TextUnit = (stagePx * scale).sp
}

val LocalStage = compositionLocalOf { Stage(1f) }

private const val STAGE_WIDTH = 1920f

/** The theme and the stage of the whole app. */
@Composable
fun PrTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Night.Focus,
        onPrimary = Night.OnFocus,
        background = Night.Bg,
        onBackground = Night.Text,
        surface = Night.Surface1,
        onSurface = Night.Text,
        onSurfaceVariant = Night.Text2,
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalStage provides Stage(maxWidth.value / STAGE_WIDTH)) {
            MaterialTheme(colorScheme = colors) {
                // Normal tracking like the mock; tv-material's body style spaces letters out by 0.5 sp.
                ProvideTextStyle(TextStyle(fontFamily = Geist, color = Night.Text, letterSpacing = 0.sp), content)
            }
        }
    }
}
