package com.elevate.launcher.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.elevate.launcher.core.ColorMath

/**
 * The home screen page indicator. It follows the pager while the finger is still moving (the position is fractional),
 * so the current page stretches into a pill and hands over to the next one instead of jumping. It draws a few rounded
 * rectangles per frame and allocates nothing while drawing, so it is safe on slow phones.
 *
 * Styles: "pill" (current page stretches), "dots" (equal dots, the current one a little larger), "bar" (thin segments).
 */
class PageIndicator(ctx: Context) : View(ctx) {
    private var count = 1
    private var style = "pill"
    private var on = 0xFFFFFFFF.toInt()
    private var off = 0x55FFFFFF
    private var pos = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun configure(count: Int, style: String, on: Int, off: Int, current: Int) {
        this.count = Math.max(1, count)
        this.style = style
        this.on = on
        this.off = off
        pos = current.coerceIn(0, this.count - 1).toFloat()
        contentDescription = "Home page ${current + 1} of ${this.count}"
        invalidate()
    }

    /** [p] is the pager position plus its offset, for example 1.4 when the second page is 40 percent scrolled away. */
    fun setPosition(p: Float) {
        val clamped = p.coerceIn(0f, (count - 1).toFloat())
        if (clamped == pos) return
        pos = clamped
        invalidate()
    }

    private fun near(i: Int): Float = Math.max(0f, 1f - Math.abs(pos - i))

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val h = (if (style == "bar") 3f else 6f) * d
        val gap = (if (style == "dots") 9f else 6f) * d
        var total = gap * (count - 1)
        for (i in 0 until count) total += widthOf(i, d)
        var x = (width - total) / 2f
        val cy = height / 2f
        for (i in 0 until count) {
            val n = near(i)
            val w = widthOf(i, d)
            paint.color = ColorMath.mix(off, on, n)
            if (style == "dots") {
                val r = h / 2f * (1f + 0.3f * n)
                rect.set(x + w / 2f - r, cy - r, x + w / 2f + r, cy + r)
                val corner = if (i == 0) r * 0.7f else r
                c.drawRoundRect(rect, corner, corner, paint)
            } else {
                rect.set(x, cy - h / 2f, x + w, cy + h / 2f)
                val corner = if (i == 0 && style == "pill") h * 0.35f else h / 2f
                c.drawRoundRect(rect, corner, corner, paint)
            }
            x += w + gap
        }
    }

    private fun widthOf(i: Int, d: Float): Float = when (style) {
        "dots" -> 6f * d
        "bar" -> 22f * d
        else -> (6f + 12f * near(i)) * d
    }
}
