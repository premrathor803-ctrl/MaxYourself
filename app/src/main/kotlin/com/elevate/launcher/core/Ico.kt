package com.elevate.launcher.core

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.widget.ImageView

/**
 * The icon set. Every icon is drawn in code on the same 24 unit grid with the same round-capped 1.8 unit line, so they
 * look like one family and add no image files to the app. Icons are decoration: the views that show them are hidden from
 * screen readers, and the label next to them carries the meaning.
 */
class Ico(private val kind: String, private var tint: Int) : Drawable() {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.8f
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val sc = Math.min(b.width(), b.height()) / 24f
        c.save()
        c.translate(b.left + (b.width() - 24f * sc) / 2f, b.top + (b.height() - 24f * sc) / 2f)
        c.scale(sc, sc)
        line.color = tint
        fill.color = tint
        shape(c)
        c.restore()
    }

    private fun l(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(x1, y1, x2, y2, line)
    private fun ring(c: Canvas, x: Float, y: Float, r: Float) = c.drawCircle(x, y, r, line)
    private fun dot(c: Canvas, x: Float, y: Float, r: Float) = c.drawCircle(x, y, r, fill)
    private fun box(c: Canvas, a: Float, b: Float, x: Float, y: Float, r: Float) = c.drawRoundRect(RectF(a, b, x, y), r, r, line)

    private fun poly(c: Canvas, close: Boolean, vararg p: Float) {
        val path = Path()
        path.moveTo(p[0], p[1])
        var i = 2
        while (i + 1 < p.size) { path.lineTo(p[i], p[i + 1]); i += 2 }
        if (close) path.close()
        c.drawPath(path, line)
    }

    private fun shape(c: Canvas) {
        when (kind) {
            "habits" -> { ring(c, 12f, 12f, 9f); poly(c, false, 7.8f, 12.4f, 10.6f, 15.2f, 16.4f, 9.2f) }
            "check" -> poly(c, false, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
            "workouts" -> {
                l(c, 8f, 12f, 16f, 12f)
                l(c, 7f, 7.5f, 7f, 16.5f); l(c, 17f, 7.5f, 17f, 16.5f)
                l(c, 4.2f, 9.5f, 4.2f, 14.5f); l(c, 19.8f, 9.5f, 19.8f, 14.5f)
            }
            "grooming" -> poly(c, true, 12f, 3f, 13.9f, 10.1f, 21f, 12f, 13.9f, 13.9f, 12f, 21f, 10.1f, 13.9f, 3f, 12f, 10.1f, 10.1f)
            "posture" -> {
                ring(c, 12f, 5.4f, 2.3f)
                l(c, 12f, 8.6f, 12f, 15f)
                l(c, 7.5f, 10.4f, 16.5f, 10.4f)
                l(c, 12f, 15f, 9f, 20.5f); l(c, 12f, 15f, 15f, 20.5f)
            }
            "goals" -> { ring(c, 12f, 12f, 9f); ring(c, 12f, 12f, 5.2f); dot(c, 12f, 12f, 1.6f) }
            "progress" -> {
                line.strokeWidth = 2.6f
                l(c, 6f, 19f, 6f, 13f); l(c, 12f, 19f, 12f, 8f); l(c, 18f, 19f, 18f, 4.5f)
                line.strokeWidth = 1.8f
            }
            "learn" -> {
                val p = Path()
                p.moveTo(3.5f, 5.5f); p.cubicTo(7f, 4.4f, 10f, 5f, 12f, 7f)
                p.cubicTo(14f, 5f, 17f, 4.4f, 20.5f, 5.5f)
                p.lineTo(20.5f, 18.5f); p.cubicTo(17f, 17.5f, 14f, 18f, 12f, 20f)
                p.cubicTo(10f, 18f, 7f, 17.5f, 3.5f, 18.5f); p.close()
                c.drawPath(p, line)
                l(c, 12f, 7f, 12f, 20f)
            }
            "reminders" -> {
                val p = Path()
                p.moveTo(6f, 16.5f); p.lineTo(6f, 11f)
                p.arcTo(RectF(6f, 4.5f, 18f, 16.5f), 180f, 180f)
                p.lineTo(18f, 16.5f); p.lineTo(19.6f, 18.2f); p.lineTo(4.4f, 18.2f); p.close()
                c.drawPath(p, line)
                l(c, 10.2f, 21f, 13.8f, 21f)
            }
            "photos" -> {
                box(c, 3.5f, 5f, 20.5f, 19f, 3f)
                ring(c, 9f, 10.2f, 1.6f)
                poly(c, false, 4.6f, 17f, 9.4f, 12.8f, 13f, 15.6f, 16f, 13.2f, 19.4f, 16.4f)
            }
            "growth" -> {
                l(c, 7.5f, 4.5f, 7.5f, 19.5f)
                poly(c, false, 5f, 7f, 7.5f, 4.5f, 10f, 7f)
                poly(c, false, 5f, 17f, 7.5f, 19.5f, 10f, 17f)
                l(c, 13f, 6f, 19f, 6f); l(c, 13f, 10f, 17f, 10f); l(c, 13f, 14f, 19f, 14f); l(c, 13f, 18f, 17f, 18f)
            }
            "gaming" -> {
                box(c, 3f, 7.5f, 21f, 17f, 4.5f)
                l(c, 8f, 10.2f, 8f, 14.4f); l(c, 5.9f, 12.3f, 10.1f, 12.3f)
                dot(c, 15.6f, 11.2f, 1.1f); dot(c, 17.8f, 13.6f, 1.1f)
            }
            "settings" -> {
                l(c, 4f, 7f, 7f, 7f); l(c, 11f, 7f, 20f, 7f); ring(c, 9f, 7f, 2f)
                l(c, 4f, 12f, 12f, 12f); l(c, 16f, 12f, 20f, 12f); ring(c, 14f, 12f, 2f)
                l(c, 4f, 17f, 6f, 17f); l(c, 10f, 17f, 20f, 17f); ring(c, 8f, 17f, 2f)
            }
            "sleep" -> {
                val a = Path().apply { addCircle(12f, 12f, 8.2f, Path.Direction.CW) }
                val b = Path().apply { addCircle(16.8f, 8.4f, 7f, Path.Direction.CW) }
                a.op(b, Path.Op.DIFFERENCE)
                c.drawPath(a, line)
            }
            "trophy" -> {
                val p = Path()
                p.moveTo(8f, 4f); p.lineTo(16f, 4f); p.lineTo(16f, 10f)
                p.arcTo(RectF(8f, 6f, 16f, 14f), 0f, 180f); p.close()
                c.drawPath(p, line)
                poly(c, false, 8f, 6.5f, 4.8f, 6.5f, 4.8f, 8f, 5.8f, 10f, 8.2f, 10.6f)
                poly(c, false, 16f, 6.5f, 19.2f, 6.5f, 19.2f, 8f, 18.2f, 10f, 15.8f, 10.6f)
                l(c, 12f, 14f, 12f, 18f); l(c, 8.5f, 20f, 15.5f, 20f)
            }
            "bolt" -> poly(c, true, 13.2f, 3f, 5.6f, 13.4f, 11f, 13.4f, 10f, 21f, 18.4f, 10.2f, 13f, 10.2f)
            "star" -> {
                val p = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) 9f else 4.1f
                    val a = Math.PI * (-90.0 + i * 36.0) / 180.0
                    val x = 12f + (r * Math.cos(a)).toFloat()
                    val y = 12.4f + (r * Math.sin(a)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                c.drawPath(p, line)
            }
            "lock" -> {
                box(c, 5.5f, 10.5f, 18.5f, 20f, 2.5f)
                val p = Path()
                p.moveTo(8.5f, 10.5f); p.lineTo(8.5f, 7.6f)
                p.arcTo(RectF(8.5f, 4f, 15.5f, 11.2f), 180f, 180f); p.lineTo(15.5f, 10.5f)
                c.drawPath(p, line)
            }
            "today" -> {
                box(c, 4f, 5.5f, 20f, 20f, 3f)
                l(c, 4f, 10f, 20f, 10f); l(c, 8.5f, 3.5f, 8.5f, 7.5f); l(c, 15.5f, 3.5f, 15.5f, 7.5f)
            }
            "play" -> poly(c, true, 8f, 5.5f, 18.5f, 12f, 8f, 18.5f)
            "chevron" -> poly(c, false, 9f, 6f, 15f, 12f, 9f, 18f)
            "plus" -> { l(c, 12f, 5f, 12f, 19f); l(c, 5f, 12f, 19f, 12f) }
            "info" -> { ring(c, 12f, 12f, 9f); l(c, 12f, 11f, 12f, 16.5f); dot(c, 12f, 7.9f, 1.1f) }
            "user" -> { ring(c, 12f, 8f, 3.6f); val p = Path(); p.moveTo(4.5f, 20f); p.arcTo(RectF(4.5f, 12.5f, 19.5f, 27.5f), 180f, 180f); c.drawPath(p, line) }
            "palette" -> { ring(c, 12f, 12f, 9f); dot(c, 8.4f, 10f, 1.2f); dot(c, 12f, 7.6f, 1.2f); dot(c, 15.8f, 10f, 1.2f); dot(c, 8.8f, 14.4f, 1.2f) }
            "motion" -> { poly(c, false, 3.5f, 14f, 8f, 14f, 10.5f, 7f, 13.5f, 17f, 16f, 10f, 20.5f, 10f) }
            else -> ring(c, 12f, 12f, 9f)
        }
    }

    override fun setAlpha(alpha: Int) { line.alpha = alpha; fill.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { line.colorFilter = cf; fill.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        /** An icon view of [dp] by [dp], hidden from screen readers. */
        fun view(ctx: Context, kind: String, dp: Int, tint: Int): ImageView = ImageView(ctx).apply {
            setImageDrawable(Ico(kind, tint))
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = android.widget.LinearLayout.LayoutParams(ctx.dp(dp), ctx.dp(dp))
        }

        /** An icon on a softly tinted rounded square, used on dashboard tiles and list headers. */
        fun badge(ctx: Context, kind: String, box: Int = 40, tint: Int = Palette.accent): android.widget.FrameLayout =
            android.widget.FrameLayout(ctx).apply {
                background = UiKit.rounded(ColorMath.withAlpha(tint, 0.16f), ctx.dpf(box * 0.32f))
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                val iv = ImageView(ctx).apply {
                    setImageDrawable(Ico(kind, if (ColorMath.contrast(tint, Palette.surface) >= 3.0) tint else Palette.accentText))
                }
                val pad = ctx.dp(box / 4)
                addView(iv, android.widget.FrameLayout.LayoutParams(ctx.dp(box) - pad * 2, ctx.dp(box) - pad * 2).apply { setMargins(pad, pad, pad, pad) })
                layoutParams = android.widget.LinearLayout.LayoutParams(ctx.dp(box), ctx.dp(box))
            }
    }
}
