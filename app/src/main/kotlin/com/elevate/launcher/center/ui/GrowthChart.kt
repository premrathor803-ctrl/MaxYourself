package com.elevate.launcher.center.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import java.util.Locale

/**
 * A line chart of real readings against their real dates. Points sit where their dates are, so uneven gaps look uneven.
 * Touch or drag to select a reading; the arrow keys do the same, and the caller can add buttons for it.
 * It never draws a point that was not given, never extends the line beyond the last reading, and draws no forecast.
 */
class GrowthChart(ctx: Context) : View(ctx) {
    class Pt(val day: String, val value: Double)

    private var pts: List<Pt> = emptyList()
    private var unit = ""
    var selected = -1
        private set
    var onSelect: ((Int) -> Unit)? = null

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dpf(2.5f); strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply { strokeWidth = 1f }
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    init {
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    /** [points] must be in date order. [unit] is shown on the axis, for example " cm". */
    fun set(points: List<Pt>, unit: String, selected: Int) {
        this.pts = points
        this.unit = unit
        this.selected = if (selected in points.indices) selected else points.lastIndex
        describe()
        invalidate()
    }

    fun select(i: Int) {
        if (pts.isEmpty()) return
        val n = i.coerceIn(0, pts.lastIndex)
        if (n == selected) return
        selected = n
        describe()
        invalidate()
        onSelect?.invoke(n)
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun describe() {
        if (pts.isEmpty()) { contentDescription = "Height chart with no readings"; return }
        val s = pts[selected]
        contentDescription = "Height chart with ${pts.size} reading" + (if (pts.size == 1) "" else "s") +
            " from ${Days.short(pts.first().day)} to ${Days.short(pts.last().day)}, " +
            "from ${fmt(pts.first().value)}$unit to ${fmt(pts.last().value)}$unit. " +
            "Selected: ${fmt(s.value)}$unit on ${Days.short(s.day)}. Use the previous and next buttons to move between readings."
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(200), widthMeasureSpec), context.dp(210))
    }

    private var left = 0f
    private var right = 0f
    private var top = 0f
    private var bottom = 0f
    private var minX = 0
    private var spanX = 1
    private var minY = 0.0
    private var maxY = 1.0

    private fun px(i: Int): Float {
        if (pts.size == 1) return (left + right) / 2f
        val x = Days.diff(pts.first().day, pts[i].day)
        return left + (x - minX).toFloat() / spanX * (right - left)
    }

    private fun py(v: Double): Float = (bottom - (v - minY) / (maxY - minY) * (bottom - top)).toFloat()

    private fun layout() {
        txt.textSize = context.dpf(11f)
        val lo = pts.minOf { it.value }
        val hi = pts.maxOf { it.value }
        val pad = Math.max(0.5, (hi - lo) * 0.25)
        minY = lo - pad
        maxY = hi + pad
        val axisW = Math.max(txt.measureText(fmt(minY)), txt.measureText(fmt(maxY)))
        left = axisW + context.dpf(12f)
        right = width - context.dpf(14f)
        top = context.dpf(26f)
        bottom = height - context.dpf(28f)
        minX = 0
        spanX = Math.max(1, Days.diff(pts.first().day, pts.last().day))
    }

    override fun onDraw(c: Canvas) {
        if (pts.isEmpty() || width == 0) return
        layout()
        grid.color = Palette.divider
        txt.color = Palette.dim
        txt.textAlign = Paint.Align.RIGHT
        for (k in 0..2) {
            val v = minY + (maxY - minY) * k / 2.0
            val y = py(v)
            c.drawLine(left, y, right, y, grid)
            c.drawText(fmt(v), left - context.dpf(6f), y + context.dpf(4f), txt)
        }
        txt.textAlign = Paint.Align.LEFT
        c.drawText(Days.short(pts.first().day), left, height - context.dpf(8f), txt)
        if (pts.size > 1) {
            txt.textAlign = Paint.Align.RIGHT
            c.drawText(Days.short(pts.last().day), right, height - context.dpf(8f), txt)
        }

        if (pts.size > 1) {
            val path = Path()
            for (i in pts.indices) {
                val x = px(i)
                val y = py(pts[i].value)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            line.color = Palette.accent
            c.drawPath(path, line)
        }
        for (i in pts.indices) {
            dot.color = Palette.accent
            c.drawCircle(px(i), py(pts[i].value), context.dpf(4f), dot)
        }

        if (selected in pts.indices) {
            val x = px(selected)
            val y = py(pts[selected].value)
            grid.color = Palette.accent
            c.drawLine(x, top - context.dpf(6f), x, bottom, grid)
            dot.color = Palette.surface
            c.drawCircle(x, y, context.dpf(8f), dot)
            dot.color = Palette.accent
            c.drawCircle(x, y, context.dpf(6f), dot)
            txt.color = Palette.text
            txt.textAlign = Paint.Align.CENTER
            txt.isFakeBoldText = true
            val label = fmt(pts[selected].value) + unit
            val half = txt.measureText(label) / 2f
            val lx = Math.max(left + half, Math.min(x, right - half + context.dpf(8f)))
            c.drawText(label, lx, top - context.dpf(10f), txt)
            txt.isFakeBoldText = false
        }
    }

    private fun nearestTo(x: Float): Int {
        var best = 0
        var bd = Float.MAX_VALUE
        for (i in pts.indices) {
            val d = Math.abs(px(i) - x)
            if (d < bd) { bd = d; best = i }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (pts.isEmpty()) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; dragging = false
                layout()
                select(nearestTo(e.x))
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && Math.abs(e.x - downX) > slop && Math.abs(e.x - downX) > Math.abs(e.y - downY)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) select(nearestTo(e.x))
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { select(selected - 1); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { select(selected + 1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }
}
