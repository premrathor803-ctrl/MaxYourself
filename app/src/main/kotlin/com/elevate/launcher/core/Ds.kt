package com.elevate.launcher.core

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

/**
 * The design system in one place: radii, card styles, gradients. Every function takes the colours it should paint with,
 * so the customization preview can show a choice before it is applied without touching the live colours.
 */
object Ds {
    const val R_CARD = 20f
    const val R_SMALL = 14f
    const val R_PILL = 24f

    val cardStyles = linkedMapOf("soft" to "Soft", "flat" to "Flat", "outline" to "Outline", "accent" to "Accent")
    val layouts = linkedMapOf("balanced" to "Balanced", "focus" to "Focus", "explorer" to "Explorer")
    val navStyles = linkedMapOf("pill" to "Pill", "dots" to "Dots", "bar" to "Bar")
    val motions = linkedMapOf("full" to "Full", "reduced" to "Reduced", "off" to "Off")

    fun gradient(c1: Int, c2: Int, radiusPx: Float, horizontal: Boolean = true): GradientDrawable =
        GradientDrawable(
            if (horizontal) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(c1, c2)
        ).apply { cornerRadius = radiusPx }

    /** The background of a card in the given style. */
    fun cardBg(ctx: Context, style: String, s: Palette.Scheme, radiusDp: Float = R_CARD): GradientDrawable {
        val r = ctx.dpf(radiusDp)
        val hair = Math.max(1, ctx.dp(1))
        return when (style) {
            "flat" -> UiKit.rounded(s.surface, r)
            "outline" -> UiKit.rounded(ColorMath.withAlpha(s.surface, 0.35f), r, ColorMath.withAlpha(s.text, 0.22f), Math.max(1, ctx.dp(1)))
            "accent" -> gradient(
                ColorMath.mix(s.surface, s.accent, if (s.dark) 0.16f else 0.10f),
                ColorMath.mix(s.surface, s.accent2, if (s.dark) 0.06f else 0.03f), r, false
            ).apply { setStroke(hair, ColorMath.withAlpha(s.accent, if (s.dark) 0.38f else 0.30f)) }
            else -> gradient(
                ColorMath.mix(s.surface, s.text, if (s.dark) 0.035f else 0f), s.surface, r, false
            ).apply { setStroke(hair, s.border) }
        }
    }

    /** Light themes lift soft and accent cards a little. Dark themes use the border instead, where a shadow cannot be seen. */
    fun cardElevation(ctx: Context, style: String, s: Palette.Scheme): Float =
        if (!s.dark && (style == "soft" || style == "accent")) ctx.dpf(1.5f) else 0f

    /** A card background with a press ripple. */
    fun pressableCard(ctx: Context, style: String, s: Palette.Scheme, radiusDp: Float = R_CARD): RippleDrawable {
        val mask = UiKit.rounded(android.graphics.Color.WHITE, ctx.dpf(radiusDp))
        val ripple = if (s.dark) 0x26FFFFFF else 0x16000000
        return RippleDrawable(ColorStateList.valueOf(ripple), cardBg(ctx, style, s, radiusDp), mask)
    }

    /** Accent gradient fill with a ripple, for primary buttons. */
    fun primaryBg(ctx: Context, s: Palette.Scheme, radiusDp: Float = R_PILL): Drawable {
        val r = ctx.dpf(radiusDp)
        val mask = UiKit.rounded(android.graphics.Color.WHITE, r)
        return RippleDrawable(ColorStateList.valueOf(ColorMath.withAlpha(s.onAccent, 0.22f)), gradient(s.accent, s.accent2, r), mask)
    }

    /** Quiet button fill: a raised surface with a hairline border. */
    fun secondaryBg(ctx: Context, s: Palette.Scheme, radiusDp: Float = R_PILL): Drawable {
        val r = ctx.dpf(radiusDp)
        val mask = UiKit.rounded(android.graphics.Color.WHITE, r)
        val ripple = if (s.dark) 0x26FFFFFF else 0x16000000
        return RippleDrawable(ColorStateList.valueOf(ripple), UiKit.rounded(s.surface2, r, s.border, Math.max(1, ctx.dp(1))), mask)
    }
}
