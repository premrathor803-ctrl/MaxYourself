package com.elevate.launcher.core

import android.content.Context
import android.content.res.Configuration
import com.elevate.launcher.R

/**
 * Runtime colour set. [Scheme] is an immutable snapshot (the customization preview draws with one without touching the
 * live colours); [update] copies the scheme for the current configuration and settings into the fields the screens read.
 */
object Palette {
    /** Everything a screen needs to paint itself. [accentText] is the accent adjusted so it is readable as small text. */
    class Scheme(
        val dark: Boolean,
        val bg: Int,
        val surface: Int,
        val surface2: Int,
        val text: Int,
        val dim: Int,
        val accent: Int,
        val accent2: Int,
        val accentText: Int,
        val onAccent: Int,
        val divider: Int,
        val border: Int
    )

    var dark = true
    var bg = 0xFF08090D.toInt()
    var surface = 0xFF12141B.toInt()
    var surface2 = 0xFF191D27.toInt()
    var text = 0xFFF4F5F8.toInt()
    var dim = 0xFF9298A8.toInt()
    var accent = 0xFF648CFF.toInt()
    var accent2 = 0xFF9272FF.toInt()
    var accentText = 0xFF648CFF.toInt()
    var onAccent = 0xFF08090D.toInt()
    var divider = 0x1FFFFFFF
    var border = 0x14FFFFFF
    val danger = 0xFFE5484D.toInt()
    val ok = 0xFF3FB950.toInt()

    /** Name to colour. "electric" is the new default and pairs with the violet from the visual spec. */
    val accents = linkedMapOf(
        "electric" to 0xFF648CFF.toInt(),
        "violet" to 0xFF9272FF.toInt(),
        "teal" to 0xFF19B5A5.toInt(),
        "orange" to 0xFFF2882E.toInt(),
        "rose" to 0xFFE5527A.toInt(),
        "blue" to 0xFF3B82F6.toInt(),
        "green" to 0xFF3FB950.toInt(),
        "gold" to 0xFFE3B341.toInt()
    )

    val accentNames = linkedMapOf(
        "electric" to "Electric", "violet" to "Violet", "teal" to "Teal", "orange" to "Orange",
        "rose" to "Rose", "blue" to "Blue", "green" to "Green", "gold" to "Gold"
    )

    fun accentStyle(name: String): Int = when (name) {
        "violet" -> R.style.Accent_Violet
        "orange" -> R.style.Accent_Orange
        "rose" -> R.style.Accent_Rose
        "blue" -> R.style.Accent_Blue
        "green" -> R.style.Accent_Green
        "gold" -> R.style.Accent_Gold
        "teal" -> R.style.Accent_Teal
        else -> R.style.Accent_Electric
    }

    /**
     * The second colour of an accent gradient. Chosen by hand so each pair sits close on the colour wheel and dark text stays
     * readable on both ends (checked by a unit test). Electric and Violet are the two colours from the visual spec.
     */
    private val partners = mapOf(
        "electric" to 0xFF9272FF.toInt(), "violet" to 0xFF648CFF.toInt(), "teal" to 0xFF4FA8E8.toInt(),
        "orange" to 0xFFE5527A.toInt(), "rose" to 0xFF9272FF.toInt(), "blue" to 0xFF19B5A5.toInt(),
        "green" to 0xFF19B5A5.toInt(), "gold" to 0xFFF2882E.toInt()
    )

    fun partnerOf(name: String, accent: Int): Int {
        partners[name]?.let { return it }
        val hsl = ColorMath.toHsl(accent)
        hsl[0] = (hsl[0] + 38f) % 360f
        return ColorMath.fromHsl(hsl)
    }

    private const val NEAR_BLACK = 0xFF08090D.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** Text or icon colour that sits on top of an accent fill: whichever of near-black and white has the better contrast. */
    fun onAccentFor(fill: Int): Int =
        if (ColorMath.contrast(fill, NEAR_BLACK) >= ColorMath.contrast(fill, WHITE)) NEAR_BLACK else WHITE

    /** Like [onAccentFor] for a gradient: the colour that reads best on the worse of its two ends. */
    fun onGradientFor(a: Int, b: Int): Int {
        val dark = Math.min(ColorMath.contrast(a, NEAR_BLACK), ColorMath.contrast(b, NEAR_BLACK))
        val light = Math.min(ColorMath.contrast(a, WHITE), ColorMath.contrast(b, WHITE))
        return if (dark >= light) NEAR_BLACK else WHITE
    }

    /** The accent made lighter or darker until it reads at 4.5:1 or better on [onTop], for use as small text. */
    fun readableAccent(accent: Int, onTop: Int): Int {
        if (ColorMath.contrast(accent, onTop) >= 4.5) return accent
        val darkBg = ColorMath.luminance(onTop) < 0.4
        val hsl = ColorMath.toHsl(accent)
        var c = accent
        var n = 0
        while (ColorMath.contrast(c, onTop) < 4.5 && n < 40) {
            hsl[2] = if (darkBg) Math.min(1f, hsl[2] + 0.02f) else Math.max(0f, hsl[2] - 0.02f)
            c = ColorMath.fromHsl(hsl)
            n++
        }
        return c
    }

    /** Builds the colours for a theme. [night] is whether dark colours are used. Pure: it reads no settings. */
    fun scheme(night: Boolean, amoled: Boolean, accentName: String): Scheme {
        val accent = accents[accentName] ?: accents.getValue("electric")
        val name = if (accents.containsKey(accentName)) accentName else "electric"
        val bg: Int
        val surface: Int
        val surface2: Int
        val text: Int
        val dim: Int
        val divider: Int
        val border: Int
        if (night) {
            bg = if (amoled) 0xFF000000.toInt() else 0xFF08090D.toInt()
            surface = if (amoled) 0xFF0C0D11.toInt() else 0xFF12141B.toInt()
            surface2 = if (amoled) 0xFF15171E.toInt() else 0xFF191D27.toInt()
            text = 0xFFF4F5F8.toInt()
            dim = 0xFF9298A8.toInt()
            divider = 0x1FFFFFFF
            border = 0x14FFFFFF
        } else {
            bg = 0xFFF4F5F9.toInt()
            surface = 0xFFFFFFFF.toInt()
            surface2 = 0xFFECEEF4.toInt()
            text = 0xFF12141B.toInt()
            dim = 0xFF5A6072.toInt()
            divider = 0x1A000000
            border = 0x14000000
        }
        val partner = partnerOf(name, accent)
        // surface2 is the darkest light surface and the lightest dark one, so text readable on it is readable on all three.
        return Scheme(
            night, bg, surface, surface2, text, dim, accent, partner,
            readableAccent(accent, surface2), onGradientFor(accent, partner), divider, border
        )
    }

    /** The colours currently in use, as a [Scheme]. */
    fun live(): Scheme = Scheme(dark, bg, surface, surface2, text, dim, accent, accent2, accentText, onAccent, divider, border)

    fun isNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    fun update(ctx: Context) {
        val s = scheme(isNight(ctx), Prefs.amoled, Prefs.accent)
        dark = s.dark
        bg = s.bg
        surface = s.surface
        surface2 = s.surface2
        text = s.text
        dim = s.dim
        accent = s.accent
        accent2 = s.accent2
        accentText = s.accentText
        onAccent = s.onAccent
        divider = s.divider
        border = s.border
    }
}

/** Colour arithmetic that needs no Android classes, so it can be unit tested on the JVM. */
object ColorMath {
    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    /** [a] moved [t] (0..1) of the way towards [b]. Alpha is mixed too. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = Math.round(((a shr shift) and 0xFF) * (1f - k) + ((b shr shift) and 0xFF) * k)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** [c] with its alpha set to [a] (0..1). */
    fun withAlpha(c: Int, a: Float): Int = (Math.round(a.coerceIn(0f, 1f) * 255f) shl 24) or (c and 0x00FFFFFF)

    /** Hue 0..360, saturation and lightness 0..1. */
    fun toHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = Math.max(r, Math.max(g, b))
        val mn = Math.min(r, Math.min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d > 0f) {
            s = d / (1f - Math.abs(2f * l - 1f))
            h = when (mx) {
                r -> ((g - b) / d) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } * 60f
            if (h < 0f) h += 360f
        }
        return floatArrayOf(h, s, l)
    }

    fun fromHsl(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]
        val c = (1f - Math.abs(2f * l - 1f)) * s
        val x = c * (1f - Math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = Math.round((v + m).coerceIn(0f, 1f) * 255f)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    /** WCAG relative luminance of an opaque ARGB colour. */
    fun luminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** WCAG contrast ratio, 1 to 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = Math.max(la, lb)
        val lo = Math.min(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }
}
