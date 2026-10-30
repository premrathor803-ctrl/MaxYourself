package com.elevate.launcher.core

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Small composite widgets built in code. */
object Widgets {
    /** A label with [-] value [+] controls. */
    fun stepper(
        ctx: Context, label: String, initial: Int, min: Int, max: Int, step: Int = 1,
        fmt: (Int) -> String = { it.toString() }, onChange: (Int) -> Unit
    ): LinearLayout {
        var v = initial
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(4), 0, ctx.dp(4))
        }
        val value: TextView = UiKit.text(ctx, fmt(v), 15f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            minWidth = ctx.dp(70)
        }
        fun apply() { value.text = fmt(v); onChange(v) }
        row.addView(UiKit.text(ctx, label, 15f, Palette.text), lp(0, WRAP, 1f))
        row.addView(UiKit.button(ctx, "−", false, repeatable = true) { if (v - step >= min) { v -= step; apply() } }, lp(ctx.dp(46), ctx.dp(44)))
        row.addView(value, lp(WRAP, WRAP))
        row.addView(UiKit.button(ctx, "+", false, repeatable = true) { if (v + step <= max) { v += step; apply() } }, lp(ctx.dp(46), ctx.dp(44)))
        return row
    }
}
