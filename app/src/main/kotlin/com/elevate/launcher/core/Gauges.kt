package com.elevate.launcher.core

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.TextView
import java.util.Locale

/**
 * Concentric progress rings. Each ring is one real measure (for example habits done today) from 0 to 1. A ring that has
 * nothing to show draws only its track, never a made-up value. Sweeps animate to their value when motion is on.
 */
class RingsView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    class Ring(val progress: Float, val from: Int, val to: Int)

    private var rings: List<Ring> = emptyList()
    private var shown = FloatArray(0)
    private var anim: ValueAnimator? = null
    private var track = Palette.divider
    private var centerValue = ""
    private var centerCaption = ""
    private var valueColor = Palette.text
    private var captionColor = Palette.dim

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val shaders = ArrayList<Shader?>()
    private val m = Matrix()

    /** Colours for the track, centre figure and caption. Defaults follow the live palette. */
    fun colors(track: Int, value: Int, caption: Int) {
        this.track = track; valueColor = value; captionColor = caption
        shaders.clear()
        invalidate()
    }

    /** [value] is drawn large in the middle, [caption] small under it. */
    fun center(value: String, caption: String) {
        centerValue = value
        centerCaption = caption
        invalidate()
    }

    /** [from] says where the sweeps start, for example the values shown before a refresh. Default: what is drawn now. */
    fun set(list: List<Ring>, animate: Boolean = true, from: FloatArray? = null, durationMs: Long? = null) {
        anim?.cancel()
        val target = FloatArray(list.size) { list[it].progress.coerceIn(0f, 1f) }
        val start = if (from != null && from.size == list.size) from.copyOf()
        else if (shown.size == list.size) shown.copyOf() else FloatArray(list.size)
        rings = list
        shaders.clear()
        if (!animate || (durationMs == null && !Motion.on) || durationMs == 0L) {
            shown = target
            invalidate()
            return
        }
        shown = start
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs ?: Motion.dur(900L)
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener {
                val t = it.animatedValue as Float
                for (i in target.indices) shown[i] = start[i] + (target[i] - start[i]) * t
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { shown = target.copyOf(); invalidate() }
                override fun onAnimationCancel(animation: android.animation.Animator) { shown = target.copyOf(); invalidate() }
            })
            start()
        }
    }

    /** What is drawn right now for ring [i], 0..1. The emulator test reads this to prove the sweep really animates. */
    fun displayed(i: Int): Float = if (i in shown.indices) shown[i] else 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = resolveSize(context.dp(150), widthMeasureSpec)
        val h = resolveSize(context.dp(150), heightMeasureSpec)
        val s = Math.min(w, h)
        setMeasuredDimension(s, s)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val stroke = Math.max(context.dpf(7f), w * 0.075f)
        val gap = stroke * 0.55f
        trackPaint.color = track
        trackPaint.strokeWidth = stroke
        val cx = w / 2f
        val cy = height / 2f
        for (i in rings.indices) {
            val r = w / 2f - stroke / 2f - i * (stroke + gap)
            if (r <= stroke) break
            rect.set(cx - r, cy - r, cx + r, cy + r)
            c.drawCircle(cx, cy, r, trackPaint)
            val p = shown.getOrElse(i) { 0f }
            if (p > 0.004f) {
                arcPaint.strokeWidth = stroke
                while (shaders.size <= i) shaders.add(null)
                var sh = shaders[i]
                if (sh == null) {
                    sh = SweepGradient(cx, cy, intArrayOf(rings[i].from, rings[i].to, rings[i].from), floatArrayOf(0f, 0.75f, 1f))
                    shaders[i] = sh
                }
                m.reset()
                m.postRotate(-90f, cx, cy)
                sh.setLocalMatrix(m)
                arcPaint.shader = sh
                c.drawArc(rect, -90f, 360f * p, false, arcPaint)
                arcPaint.shader = null
            }
        }
        if (centerValue.isNotEmpty()) {
            val innerR = w / 2f - rings.size * (stroke + gap)
            val size = Math.min(context.dpf(34f), Math.max(context.dpf(16f), innerR * 0.9f))
            valuePaint.typeface = Fonts.display()
            valuePaint.color = valueColor
            valuePaint.textSize = size
            c.drawText(centerValue, cx, cy + size * 0.18f, valuePaint)
            if (centerCaption.isNotEmpty()) {
                capPaint.typeface = Fonts.face(false)
                capPaint.color = captionColor
                capPaint.textSize = context.dpf(11f)
                c.drawText(centerCaption, cx, cy + size * 0.18f + context.dpf(15f), capPaint)
            }
        }
    }
}

/** A number that counts up to its value when motion is on, and simply shows it when it is off. */
class CountText(ctx: Context) : TextView(ctx) {
    private var shownValue = 0.0
    private var animator: ValueAnimator? = null
    private var decimals = 0
    private var prefix = ""
    private var suffix = ""

    init {
        typeface = Fonts.display()
        letterSpacing = -0.02f
        fontFeatureSettings = "tnum"
        includeFontPadding = false
        gravity = Gravity.START
    }

    private fun fmt(v: Double): String = prefix + String.format(Locale.US, "%." + decimals + "f", v) + suffix

    /** Shows [target]. If [animate] and motion is on, counts from what was shown before. */
    fun setValue(target: Double, decimals: Int = 0, prefix: String = "", suffix: String = "", animate: Boolean = true, startAt: Double? = null) {
        animator?.cancel()
        this.decimals = decimals
        this.prefix = prefix
        this.suffix = suffix
        val from = startAt ?: shownValue
        if (!animate || !Motion.on || from == target) {
            shownValue = target
            text = fmt(target)
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.dur(700L)
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener {
                val t = it.animatedValue as Float
                shownValue = from + (target - from) * t
                text = fmt(shownValue)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { shownValue = target; text = fmt(target) }
                override fun onAnimationCancel(animation: android.animation.Animator) { shownValue = target; text = fmt(target) }
            })
            start()
        }
    }

    /** The number currently on screen, which is between the old and new value while it counts. */
    fun displayed(): Double = shownValue

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}

/** A slim progress bar with an accent gradient fill. */
class XpBar(ctx: Context) : View(ctx) {
    private var target = 0f
    private var shown = 0f
    private var anim: ValueAnimator? = null
    private var from = Palette.accent
    private var to = Palette.accent2
    private var trackColor = Palette.divider
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun colors(from: Int, to: Int, track: Int) { this.from = from; this.to = to; trackColor = track; invalidate() }

    fun set(progress: Float, animate: Boolean = true, startAt: Float? = null) {
        anim?.cancel()
        val goal = progress.coerceIn(0f, 1f)
        val start = startAt ?: shown
        target = goal
        if (!animate || !Motion.on || start == goal) { shown = goal; invalidate(); return }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.dur(800L)
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { shown = start + (goal - start) * (it.animatedValue as Float); invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) { shown = goal; invalidate() }
                override fun onAnimationCancel(animation: android.animation.Animator) { shown = goal; invalidate() }
            })
            start()
        }
    }

    fun displayed(): Float = shown

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(120), widthMeasureSpec), context.dp(8))
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        p.shader = null
        p.color = trackColor
        rect.set(0f, 0f, w, h)
        c.drawRoundRect(rect, h / 2f, h / 2f, p)
        val fillW = Math.max(h, w * shown)
        if (shown > 0.003f) {
            p.shader = LinearGradient(0f, 0f, Math.max(w, 1f), 0f, from, to, Shader.TileMode.CLAMP)
            rect.set(0f, 0f, fillW, h)
            c.drawRoundRect(rect, h / 2f, h / 2f, p)
            p.shader = null
        }
    }
}

/** A tiny line of real readings. With fewer than two readings it shows one dot, never an invented trend. */
class Sparkline(ctx: Context) : View(ctx) {
    private var values: List<Double> = emptyList()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dpf(2.2f); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private var from = Palette.accent
    private var to = Palette.accent2

    fun set(v: List<Double>, from: Int = Palette.accent, to: Int = Palette.accent2) {
        values = v
        this.from = from
        this.to = to
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(96), widthMeasureSpec), context.dp(36))
    }

    override fun onDraw(c: Canvas) {
        if (values.isEmpty()) return
        val pad = context.dpf(4f)
        val w = width - pad * 2
        val h = height - pad * 2
        val lo = values.min()
        val hi = values.max()
        val span = Math.max(0.5, hi - lo)
        fun x(i: Int) = if (values.size == 1) width / 2f else pad + w * i / (values.size - 1)
        fun y(v: Double) = (pad + h - ((v - lo) / span * h * 0.8 + h * 0.1)).toFloat()
        if (values.size > 1) {
            val path = Path()
            for (i in values.indices) if (i == 0) path.moveTo(x(i), y(values[i])) else path.lineTo(x(i), y(values[i]))
            line.shader = LinearGradient(0f, 0f, width.toFloat(), 0f, from, to, Shader.TileMode.CLAMP)
            c.drawPath(path, line)
            line.shader = null
        }
        dot.color = to
        c.drawCircle(x(values.lastIndex), y(values.last()), context.dpf(3.6f), dot)
    }
}

/** A calm pulsing block shown while records load. Static when motion is off. */
class Skeleton(ctx: Context, private val h: Int = 72) : View(ctx) {
    private var pulse: ValueAnimator? = null

    init {
        background = UiKit.rounded(ColorMath.withAlpha(Palette.text, if (Palette.dark) 0.06f else 0.07f), ctx.dpf(Ds.R_CARD))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(100), widthMeasureSpec), context.dp(h))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Motion.on) {
            pulse = ValueAnimator.ofFloat(0.55f, 1f).apply {
                duration = 900L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { alpha = it.animatedValue as Float }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        pulse = null
        alpha = 1f
        super.onDetachedFromWindow()
    }
}
