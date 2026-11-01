package com.elevate.launcher.center.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.elevate.launcher.core.ColorMath
import com.elevate.launcher.core.Motion
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import java.util.Locale

/** Simple vertical bar chart drawn on a Canvas. Only draws what it is given; callers show an empty state instead of an empty chart. */
class BarChart(ctx: Context) : View(ctx) {
    private var values: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    private var maxV = 1f
    private var unit = ""
    private var highlight = -1
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val basePaint = Paint()
    private var reveal = 1f
    private var anim: ValueAnimator? = null

    fun set(values: List<Float>, labels: List<String>, max: Float? = null, unit: String = "", highlight: Int = -1) {
        val changed = this.values != values
        this.values = values
        this.labels = labels
        this.unit = unit
        this.highlight = highlight
        val m = max ?: (values.maxOrNull() ?: 1f)
        maxV = if (m <= 0f) 1f else m
        contentDescription = values.indices.joinToString(", ") { i ->
            (labels.getOrNull(i) ?: "") + " " + fmtNum(values[i].toDouble()) + unit
        }
        // bars grow up from the baseline when the data is new; with animation off they are simply there
        anim?.cancel()
        anim = null
        reveal = 1f
        if (changed && values.any { it > 0f }) {
            reveal = 0f
            anim = Motion.value(0f, 1f, 650L) { reveal = it; invalidate() }
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(200), widthMeasureSpec), context.dp(160))
    }

    override fun onDraw(c: Canvas) {
        val n = values.size
        if (n == 0) return
        val w = width.toFloat()
        val topPad = context.dpf(20f)
        val botPad = context.dpf(22f)
        val chartH = height - topPad - botPad
        val slot = w / n
        val bw = Math.min(slot * 0.62f, context.dpf(34f))
        val narrow = slot < context.dpf(30f)
        textPaint.textSize = context.dpf(if (narrow) 9f else 11f)
        basePaint.color = Palette.divider
        c.drawRect(0f, topPad + chartH, w, topPad + chartH + 2f, basePaint)
        for (i in 0 until n) {
            val cx = slot * i + slot / 2f
            val v = values[i]
            val h = (if (v <= 0f) 0f else Math.max(context.dpf(3f), v / maxV * chartH)) * reveal
            if (i == highlight) {
                // the bar that matters is a gradient (violet at the top, accent at the base); the rest are quiet
                barPaint.color = 0xFF000000.toInt()
                barPaint.shader = LinearGradient(0f, topPad + chartH - h, 0f, topPad + chartH, Palette.accent2, Palette.accent, Shader.TileMode.CLAMP)
            } else {
                barPaint.shader = null
                barPaint.color = ColorMath.withAlpha(Palette.accent, 0.62f)
            }
            if (h > 0f) {
                val r = RectF(cx - bw / 2f, topPad + chartH - h, cx + bw / 2f, topPad + chartH)
                c.drawRoundRect(r, context.dpf(5f), context.dpf(5f), barPaint)
                textPaint.color = Palette.text
                val shown = if (narrow) Math.round(v).toString() else fmtNum(v.toDouble())
                c.drawText(shown, cx, topPad + chartH - h - context.dpf(4f), textPaint)
            }
            val lab = labels.getOrNull(i) ?: ""
            if (lab.isNotEmpty()) {
                textPaint.color = Palette.dim
                c.drawText(lab, cx, height - context.dpf(6f), textPaint)
            }
        }
    }
}

/** Line chart over real dates. [xs] are day offsets, [ys] the values. */
class LineChart(ctx: Context) : View(ctx) {
    private var xs: List<Float> = emptyList()
    private var ys: List<Float> = emptyList()
    private var firstLabel = ""
    private var lastLabel = ""
    private var unit = ""
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dpf(2.5f); strokeJoin = Paint.Join.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val area = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = Paint().apply { strokeWidth = 1f }

    fun set(xs: List<Float>, ys: List<Float>, firstLabel: String, lastLabel: String, unit: String) {
        this.xs = xs
        this.ys = ys
        this.firstLabel = firstLabel
        this.lastLabel = lastLabel
        this.unit = unit
        contentDescription = "Line chart with ${ys.size} readings from $firstLabel to $lastLabel"
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(200), widthMeasureSpec), context.dp(170))
    }

    override fun onDraw(c: Canvas) {
        if (ys.isEmpty()) return
        val right = width - context.dpf(10f)
        val top = context.dpf(14f)
        val bottom = height - context.dpf(26f)
        val minY0 = ys.minOrNull()!!
        val maxY0 = ys.maxOrNull()!!
        val pad = Math.max(0.5f, (maxY0 - minY0) * 0.2f)
        val minY = minY0 - pad
        val maxY = maxY0 + pad
        txt.textSize = context.dpf(11f)
        val axisW = Math.max(txt.measureText(fmtAxis(maxY)), txt.measureText(fmtAxis(minY)))
        val left = axisW + context.dpf(10f)
        val minX = xs.first()
        val spanX = Math.max(1f, xs.last() - minX)
        fun px(x: Float) = if (xs.size == 1) (left + right) / 2f else left + (x - minX) / spanX * (right - left)
        fun py(y: Float) = bottom - (y - minY) / (maxY - minY) * (bottom - top)

        grid.color = Palette.divider
        c.drawLine(left, top, right, top, grid)
        c.drawLine(left, bottom, right, bottom, grid)
        txt.textSize = context.dpf(11f)
        txt.color = Palette.dim
        txt.textAlign = Paint.Align.RIGHT
        c.drawText(fmtAxis(maxY), left - context.dpf(5f), top + context.dpf(4f), txt)
        c.drawText(fmtAxis(minY), left - context.dpf(5f), bottom, txt)
        txt.textAlign = Paint.Align.LEFT
        c.drawText(firstLabel, left, height - context.dpf(6f), txt)
        txt.textAlign = Paint.Align.RIGHT
        c.drawText(lastLabel, right, height - context.dpf(6f), txt)

        dot.color = Palette.accent
        if (ys.size > 1) {
            val p = Path()
            for (i in ys.indices) {
                val x = px(xs[i]); val y = py(ys[i])
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            // a soft fill under the line, then the line itself in the accent gradient (paint alpha is reset because a shader is multiplied by it)
            val fillPath = Path(p)
            fillPath.lineTo(px(xs.last()), bottom)
            fillPath.lineTo(px(xs.first()), bottom)
            fillPath.close()
            area.color = 0xFF000000.toInt()
            area.shader = LinearGradient(0f, top, 0f, bottom, ColorMath.withAlpha(Palette.accent, 0.24f), ColorMath.withAlpha(Palette.accent, 0f), Shader.TileMode.CLAMP)
            c.drawPath(fillPath, area)
            line.color = 0xFF000000.toInt()
            line.shader = LinearGradient(left, 0f, right, 0f, Palette.accent, Palette.accent2, Shader.TileMode.CLAMP)
            c.drawPath(p, line)
            line.shader = null
        }
        for (i in ys.indices) c.drawCircle(px(xs[i]), py(ys[i]), context.dpf(4f), dot)
        // the latest reading gets a halo
        dot.color = ColorMath.withAlpha(Palette.accent, 0.25f)
        c.drawCircle(px(xs.last()), py(ys.last()), context.dpf(9f), dot)
    }

    private fun fmtAxis(v: Float) = String.format(Locale.US, "%.1f", v) + unit
}

/** A rounded progress bar. [progress] is 0..1. */
class ProgressBar2(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(100), widthMeasureSpec), context.dp(10))
    }

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        paint.shader = null
        paint.color = Palette.surface2
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, paint)
        if (progress > 0f) {
            paint.color = 0xFF000000.toInt()
            paint.shader = LinearGradient(0f, 0f, Math.max(width.toFloat(), 1f), 0f, Palette.accent, Palette.accent2, Shader.TileMode.CLAMP)
            c.drawRoundRect(0f, 0f, Math.max(height.toFloat(), width * progress), height.toFloat(), r, r, paint)
            paint.shader = null
        }
    }
}

/**
 * A small status marker drawn on a Canvas (no font glyphs needed).
 * kind: 0 pending ring, 1 completed, 2 skipped, 3 not completed, 4 not scheduled.
 */
class StateDot(ctx: Context) : View(ctx) {
    var kind = 0
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = context.dp(30)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = Math.min(width, height) / 2f - context.dpf(2f)
        val sw = context.dpf(2f)
        when (kind) {
            1 -> {
                p.style = Paint.Style.FILL; p.color = Palette.accent
                c.drawCircle(cx, cy, r, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = context.dpf(2.4f); p.color = Palette.onAccent
                p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
                val path = Path()
                path.moveTo(cx - r * 0.42f, cy + r * 0.02f)
                path.lineTo(cx - r * 0.1f, cy + r * 0.34f)
                path.lineTo(cx + r * 0.46f, cy - r * 0.3f)
                c.drawPath(path, p)
            }
            2 -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = Palette.dim
                c.drawCircle(cx, cy, r, p)
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - r * 0.4f, cy, cx + r * 0.4f, cy, p)
            }
            3 -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = Palette.danger
                c.drawCircle(cx, cy, r, p)
                p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - r * 0.34f, cy - r * 0.34f, cx + r * 0.34f, cy + r * 0.34f, p)
                c.drawLine(cx - r * 0.34f, cy + r * 0.34f, cx + r * 0.34f, cy - r * 0.34f, p)
            }
            4 -> {
                p.style = Paint.Style.FILL; p.color = Palette.divider
                c.drawCircle(cx, cy, r * 0.28f, p)
            }
            else -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.color = Palette.accent
                c.drawCircle(cx, cy, r, p)
            }
        }
    }
}
