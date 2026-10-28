package com.elevate.launcher.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.elevate.launcher.core.dpf
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A plain analog clock face for the home screen. It has no seconds hand, so it only needs to redraw once a minute,
 * and it stops updating whenever it is not on screen.
 */
class AnalogClockView(ctx: Context, private val ink: Int, private val accent: Int, private val shadow: Boolean) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 20_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(tick)
        post(tick)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        removeCallbacks(tick)
        if (visibility == VISIBLE) post(tick)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - context.dpf(3f)
        if (r <= 0f) return
        if (shadow) paint.setShadowLayer(context.dpf(3f), 0f, context.dpf(1f), 0x88000000.toInt()) else paint.clearShadowLayer()

        paint.style = Paint.Style.STROKE
        paint.color = ink
        paint.strokeWidth = context.dpf(1.6f)
        c.drawCircle(cx, cy, r, paint)

        paint.strokeWidth = context.dpf(1.4f)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val inner = if (i % 3 == 0) r * 0.80f else r * 0.88f
            c.drawLine(cx + (sin(a) * inner).toFloat(), cy - (cos(a) * inner).toFloat(),
                cx + (sin(a) * r * 0.95f).toFloat(), cy - (cos(a) * r * 0.95f).toFloat(), paint)
        }

        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60.0
        val m = now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60.0

        paint.strokeWidth = context.dpf(4f)
        val ha = Math.toRadians(h * 30.0)
        c.drawLine(cx, cy, cx + (sin(ha) * r * 0.5f).toFloat(), cy - (cos(ha) * r * 0.5f).toFloat(), paint)
        paint.strokeWidth = context.dpf(2.6f)
        val ma = Math.toRadians(m * 6.0)
        c.drawLine(cx, cy, cx + (sin(ma) * r * 0.76f).toFloat(), cy - (cos(ma) * r * 0.76f).toFloat(), paint)

        paint.style = Paint.Style.FILL
        paint.color = accent
        c.drawCircle(cx, cy, context.dpf(4f), paint)
    }
}
