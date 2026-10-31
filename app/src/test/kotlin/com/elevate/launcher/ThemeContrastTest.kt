package com.elevate.launcher

import com.elevate.launcher.core.ColorMath
import com.elevate.launcher.core.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every theme, with and without pure black, in every accent colour, must stay readable. */
class ThemeContrastTest {
    private data class Combo(val night: Boolean, val amoled: Boolean, val accent: String) {
        val s: Palette.Scheme get() = Palette.scheme(night, amoled, accent)
        override fun toString() = (if (night) (if (amoled) "black" else "dark") else "light") + "/" + accent
    }

    private val combos: List<Combo> = Palette.accents.keys.flatMap { a ->
        listOf(Combo(true, false, a), Combo(true, true, a), Combo(false, false, a))
    }

    @Test fun thereAreEightAccentsAndTwentyFourCombinations() {
        assertEquals(8, Palette.accents.size)
        assertEquals(24, combos.size)
        assertEquals(Palette.accents.keys, Palette.accentNames.keys)
    }

    @Test fun mainTextIsReadableOnEverySurface() {
        for (c in combos) {
            val s = c.s
            for (bg in listOf(s.bg, s.surface, s.surface2)) {
                assertTrue("$c text ${ColorMath.contrast(s.text, bg)}", ColorMath.contrast(s.text, bg) >= 7.0)
            }
        }
    }

    @Test fun secondaryTextIsReadableOnEverySurface() {
        for (c in combos) {
            val s = c.s
            for (bg in listOf(s.bg, s.surface, s.surface2)) {
                assertTrue("$c dim ${ColorMath.contrast(s.dim, bg)}", ColorMath.contrast(s.dim, bg) >= 4.5)
            }
        }
    }

    @Test fun accentTextIsReadableOnEverySurface() {
        for (c in combos) {
            val s = c.s
            for (bg in listOf(s.bg, s.surface, s.surface2)) {
                assertTrue("$c accentText ${ColorMath.contrast(s.accentText, bg)}", ColorMath.contrast(s.accentText, bg) >= 4.5)
            }
        }
    }

    @Test fun textOnAccentButtonsIsReadableOnBothGradientEnds() {
        for (c in combos) {
            val s = c.s
            assertTrue("$c start ${ColorMath.contrast(s.onAccent, s.accent)}", ColorMath.contrast(s.onAccent, s.accent) >= 4.5)
            assertTrue("$c end ${ColorMath.contrast(s.onAccent, s.accent2)}", ColorMath.contrast(s.onAccent, s.accent2) >= 4.5)
        }
    }

    @Test fun surfacesStepUpInBrightnessOnDarkThemes() {
        for (c in combos.filter { it.night }) {
            val s = c.s
            assertTrue("$c", ColorMath.luminance(s.bg) <= ColorMath.luminance(s.surface))
            assertTrue("$c", ColorMath.luminance(s.surface) <= ColorMath.luminance(s.surface2))
        }
    }

    @Test fun designColoursAreExactlyTheOnesInTheSpec() {
        val s = Palette.scheme(true, false, "electric")
        assertEquals(0xFF08090D.toInt(), s.bg)
        assertEquals(0xFF12141B.toInt(), s.surface)
        assertEquals(0xFF191D27.toInt(), s.surface2)
        assertEquals(0xFF648CFF.toInt(), s.accent)
        assertEquals(0xFF9272FF.toInt(), s.accent2)
        assertEquals(0xFFF4F5F8.toInt(), s.text)
        assertEquals(0xFF9298A8.toInt(), s.dim)
    }

    @Test fun pureBlackOnlyChangesDarkThemes() {
        assertEquals(0xFF000000.toInt(), Palette.scheme(true, true, "electric").bg)
        assertEquals(Palette.scheme(false, false, "teal").bg, Palette.scheme(false, true, "teal").bg)
    }

    @Test fun unknownAccentFallsBackToElectric() {
        assertEquals(Palette.scheme(true, false, "electric").accent, Palette.scheme(true, false, "nope").accent)
    }

    @Test fun gradientPartnerDiffersFromTheAccent() {
        for ((name, color) in Palette.accents) assertNotEquals(name, color, Palette.partnerOf(name, color))
    }

    // ---- ColorMath
    @Test fun contrastExtremes() {
        assertEquals(21.0, ColorMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
        assertEquals(1.0, ColorMath.contrast(0xFF648CFF.toInt(), 0xFF648CFF.toInt()), 0.0001)
    }

    @Test fun mixEndpointsAndMiddle() {
        val a = 0xFF000000.toInt()
        val b = 0xFFFFFFFF.toInt()
        assertEquals(a, ColorMath.mix(a, b, 0f))
        assertEquals(b, ColorMath.mix(a, b, 1f))
        assertEquals(0xFF808080.toInt(), ColorMath.mix(a, b, 0.5f))
        assertEquals(b, ColorMath.mix(a, b, 3f))
    }

    @Test fun withAlphaKeepsTheColour() {
        assertEquals(0x80648CFF.toInt(), ColorMath.withAlpha(0xFF648CFF.toInt(), 0.5f))
        assertEquals(0x00648CFF, ColorMath.withAlpha(0xFF648CFF.toInt(), 0f))
    }

    @Test fun hslRoundTripIsCloseForEveryAccent() {
        for ((name, c) in Palette.accents) {
            val back = ColorMath.fromHsl(ColorMath.toHsl(c))
            for (shift in listOf(16, 8, 0)) {
                val d = Math.abs(((c shr shift) and 0xFF) - ((back shr shift) and 0xFF))
                assertTrue("$name channel $shift off by $d", d <= 1)
            }
        }
    }

    @Test fun greyHasNoSaturation() {
        val hsl = ColorMath.toHsl(0xFF808080.toInt())
        assertEquals(0f, hsl[1], 0.0001f)
    }
}
