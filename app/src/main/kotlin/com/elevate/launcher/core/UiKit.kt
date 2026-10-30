package com.elevate.launcher.core

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

/** Ignores a second tap that arrives right after the first, so a double tap cannot open a screen or save a record twice. */
object Tap {
    private var last = 0L

    fun ok(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - last < 450L) return false
        last = now
        return true
    }
}

object UiKit {
    fun rounded(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
            if (strokePx > 0) setStroke(strokePx, strokeColor)
        }

    /** Rounded background with a ripple that is clipped to the same rounded shape. */
    fun pressable(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): RippleDrawable {
        val content = rounded(color, radiusPx, strokeColor, strokePx)
        val mask = rounded(Color.WHITE, radiusPx)
        val ripple = if (Palette.dark) 0x33FFFFFF else 0x22000000
        return RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
    }

    fun text(ctx: Context, s: CharSequence, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(ctx).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(color)
            typeface = Fonts.face(bold)
        }

    /** A filled pill button. Quick repeat taps are ignored unless [repeatable] is set (steppers and previous/next buttons). */
    fun button(ctx: Context, label: String, filled: Boolean = true, repeatable: Boolean = false, onClick: () -> Unit): TextView =
        text(ctx, label, 14f, if (filled) Palette.onAccent else Palette.text, true).apply {
            gravity = Gravity.CENTER
            val bgc = if (filled) Palette.accent else Palette.surface2
            background = pressable(bgc, ctx.dpf(22f))
            setPadding(ctx.dp(18), ctx.dp(10), ctx.dp(18), ctx.dp(10))
            minHeight = ctx.dp(44)
            setOnClickListener { if (repeatable || Tap.ok()) onClick() }
        }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Palette.surface, ctx.dpf(18f))
        setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
    }

    fun spacer(ctx: Context, h: Int): View = View(ctx).apply { layoutParams = lp(MATCH, ctx.dp(h)) }
}
