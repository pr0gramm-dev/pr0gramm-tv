package dev.pr0gramm.tv.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** AC12: the palette of system.html, and its contrast promises. */
class NightTest {
    @Test
    fun paletteIsTheOneOfTheDesignSystem() {
        assertEquals(Color(0xFF0E0E10), Night.Bg)
        assertEquals(Color(0xFF17171A), Night.Surface1)
        assertEquals(Color(0xFF212125), Night.Surface2)
        assertEquals(Color(0xFF2D2D32), Night.Surface3)
        assertEquals(Color(0xFFF5F5F7), Night.Text)
        assertEquals(Color(0xFFB4B4BC), Night.Text2)
        assertEquals(Color(0xFF85858E), Night.Text3)
        assertEquals(Color(0xFFFF6B1A), Night.Focus)
        assertEquals(Color(0xFF120800), Night.OnFocus)
    }

    @Test
    fun focusColourIsReadableOnTheBackgroundAndDarkTextOnFocus() {
        assertTrue(contrast(Night.Focus, Night.Bg) >= 6.0)
        assertTrue(contrast(Night.OnFocus, Night.Focus) >= 6.5)
        assertTrue(contrast(Night.Text2, Night.Bg) >= 9.0)
        assertTrue(contrast(Night.Text3, Night.Bg) >= 5.0)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val x = v.toDouble()
            return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
