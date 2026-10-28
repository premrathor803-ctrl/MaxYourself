package com.elevate.launcher.center.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.View
import android.widget.LinearLayout
import com.elevate.launcher.center.content.Exercises
import com.elevate.launcher.center.content.Figure
import com.elevate.launcher.center.content.Pose
import com.elevate.launcher.center.content.PoseData
import com.elevate.launcher.center.content.Prop
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Perf
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws one exercise as a simple stick figure and, when the exercise moves, glides between the start and end position.
 * No pictures are stored: the figure is calculated from a handful of joint angles (see PoseData), so it costs almost no
 * memory. The animation stops when the screen is left, and it never runs when animations are off in the phone's
 * accessibility settings or in Elevate's performance setting. Tapping the picture pauses it (or switches between the
 * start and end position when it is not animated).
 */
class PoseView(ctx: Context, private val maxHeightDp: Int = 230) : View(ctx) {
    private var fig: Figure? = null
    private var phase = 0f
    private var anim: ValueAnimator? = null
    private var paused = false
    private var fixed = false

    private val cur = FloatArray(Pose.SIZE)
    private val pt = FloatArray(40)
    private val path = Path()
    private val limb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val prop = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val plumb = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    init {
        isClickable = true
        setOnClickListener { toggle() }
    }

    fun show(f: Figure) {
        fig = f
        phase = 0f
        paused = false
        fixed = false
        restart()
    }

    /** Shows one still frame (0 = start, 1 = end) without animating. Used by the picture check screen. */
    fun showAt(f: Figure, at: Float) {
        fig = f
        phase = at
        fixed = true
        isClickable = false
        stop()
        invalidate()
    }

    /** True when this figure has two poses and the animation is allowed to run. */
    private fun canAnimate(): Boolean {
        val f = fig ?: return false
        if (fixed || f.b == null || !Perf.animate) return false
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return false
        return true
    }

    private fun restart() {
        stop()
        if (isAttachedToWindow && !paused && canAnimate()) {
            val a = ValueAnimator.ofFloat(0f, 1f)
            a.duration = Perf.duration(1400L).coerceAtLeast(400L)
            a.repeatCount = ValueAnimator.INFINITE
            a.repeatMode = ValueAnimator.REVERSE
            a.addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            anim = a
            a.start()
        }
        invalidate()
    }

    private fun stop() {
        anim?.cancel()
        anim = null
    }

    private fun toggle() {
        val f = fig ?: return
        if (f.b == null || fixed) return
        if (canAnimate()) {
            paused = !paused
            restart()
        } else {
            phase = if (phase < 0.5f) 1f else 0f
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restart()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) restart() else stop()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = min((w / VIEW_W).toInt(), context.dp(maxHeightDp))
        setMeasuredDimension(w, h)
    }

    // ------------------------------------------------------------------ kinematics

    private fun sn(a: Float): Float = sin(Math.toRadians(a.toDouble())).toFloat()
    private fun cs(a: Float): Float = cos(Math.toRadians(a.toDouble())).toFloat()
    private fun put(i: Int, x: Float, y: Float) { pt[i * 2] = x; pt[i * 2 + 1] = y }
    private fun px(i: Int) = pt[i * 2]
    private fun py(i: Int) = pt[i * 2 + 1]

    /** Two-segment limb from point [root]: writes the middle joint to [k] and the end to [e]. */
    private fun limbTo(root: Int, a1: Float, l1: Float, a2: Float, l2: Float, k: Int, e: Int) {
        val kx = px(root) + l1 * sn(a1)
        val ky = py(root) + l1 * cs(a1)
        put(k, kx, ky)
        put(e, kx + l2 * sn(a2), ky + l2 * cs(a2))
    }

    private fun compute() {
        val v = cur
        val hx = v[Pose.HX]
        val hy = v[Pose.HY]
        put(H, hx, hy)
        if (v[Pose.FRONT] > 0.5f) {
            put(S, hx, hy - LT)
            put(HC, px(S) + (NECK + HEADR) * sn(v[Pose.HD]), py(S) + (NECK + HEADR) * cs(v[Pose.HD]))
            put(SL, px(S) - SHW, py(S)); put(SR, px(S) + SHW, py(S))
            put(HL, hx - HPW, hy); put(HR, hx + HPW, hy)
            limbTo(SL, v[Pose.UA], LU, v[Pose.FA], LF, E, W)
            limbTo(SR, v[Pose.UB], LU, v[Pose.FB], LF, EB, WB)
            limbTo(HL, v[Pose.TH], LH, v[Pose.SH], LS, K, A)
            limbTo(HR, v[Pose.TB], LH, v[Pose.SB], LS, KB, AB)
            val rot = v[Pose.ROT]
            if (rot != 0f) {
                val c = cs(rot)
                val s = sn(rot)
                for (i in 0..HR) {
                    val dx = px(i) - hx
                    val dy = py(i) - hy
                    put(i, hx + dx * c - dy * s, hy + dx * s + dy * c)
                }
            }
            return
        }
        val t = v[Pose.T]
        val sx = hx + LT * sn(t)
        val sy = hy + LT * cs(t)
        put(S, sx, sy)
        val b = v[Pose.BEND]
        put(C, (hx + sx) / 2 + cs(t) * b * 2, (hy + sy) / 2 - sn(t) * b * 2)
        put(HC, sx + (NECK + HEADR) * sn(v[Pose.HD]), sy + (NECK + HEADR) * cs(v[Pose.HD]))
        limbTo(S, v[Pose.UA], LU, v[Pose.FA], LF, E, W)
        limbTo(S, v[Pose.UB], LU, v[Pose.FB], LF, EB, WB)
        limbTo(H, v[Pose.TH], LH, v[Pose.SH], LS, K, A)
        limbTo(H, v[Pose.TB], LH, v[Pose.SB], LS, KB, AB)
        put(T, px(A) + LFT * sn(v[Pose.FO]), py(A) + LFT * cs(v[Pose.FO]))
        put(TB, px(AB) + LFT * sn(v[Pose.FOB]), py(AB) + LFT * cs(v[Pose.FOB]))
    }

    // ------------------------------------------------------------------ drawing

    private var ox = 0f
    private var u = 1f

    private fun X(x: Float) = ox + x * u
    private fun Y(y: Float) = (y - VIEW_TOP) * u

    private fun line(c: Canvas, i: Int, j: Int, p: Paint) {
        c.drawLine(X(px(i)), Y(py(i)), X(px(j)), Y(py(j)), p)
    }

    private fun lineXY(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, p: Paint) {
        c.drawLine(X(x1), Y(y1), X(x2), Y(y2), p)
    }

    override fun onDraw(c: Canvas) {
        val f = fig ?: return
        val a = f.a.v
        val bp = f.b?.v
        for (i in 0 until Pose.SIZE) cur[i] = if (bp == null) a[i] else a[i] + (bp[i] - a[i]) * phase
        cur[Pose.FRONT] = a[Pose.FRONT]
        cur[Pose.ROT] = a[Pose.ROT]
        compute()

        u = min(width / VIEW_W, height / VIEW_H)
        ox = (width - VIEW_W * u) / 2f
        val front = a[Pose.FRONT] > 0.5f
        val top = front && a[Pose.ROT] != 0f

        val near = Palette.text
        val far = Palette.dim
        val faint = (Palette.dim and 0x00FFFFFF) or (0x99 shl 24)
        limb.strokeWidth = 0.045f * u
        prop.strokeWidth = 0.03f * u
        prop.color = faint

        if (!top) {
            prop.strokeWidth = 0.014f * u
            lineXY(c, 0.05f, GROUND, 1.35f, GROUND, prop)
            prop.strokeWidth = 0.03f * u
        }
        for (p in f.props) drawProp(c, p)

        if (front) {
            limb.color = near
            line(c, HL, HR, limb); line(c, SL, SR, limb); line(c, S, H, limb)
            line(c, SR, EB, limb); line(c, EB, WB, limb)
            line(c, SL, E, limb); line(c, E, W, limb)
            line(c, HR, KB, limb); line(c, KB, AB, limb)
            line(c, HL, K, limb); line(c, K, A, limb)
        } else {
            limb.color = far
            line(c, S, EB, limb); line(c, EB, WB, limb)
            line(c, H, KB, limb); line(c, KB, AB, limb); line(c, AB, TB, limb)
            limb.color = near
            if (abs(cur[Pose.BEND]) > 0.0001f) {
                path.reset()
                path.moveTo(X(px(H)), Y(py(H)))
                path.quadTo(X(px(C)), Y(py(C)), X(px(S)), Y(py(S)))
                c.drawPath(path, limb)
            } else {
                line(c, H, S, limb)
            }
            line(c, H, K, limb); line(c, K, A, limb); line(c, A, T, limb)
            line(c, S, E, limb); line(c, E, W, limb)
        }
        limb.color = near
        limb.strokeWidth = 0.032f * u
        c.drawCircle(X(px(HC)), Y(py(HC)), HEADR * u, limb)
    }

    private fun drawProp(c: Canvas, p: Prop) {
        when (p.kind) {
            0, 4 -> lineXY(c, p.a, 0.05f, p.a, GROUND, prop)
            1 -> {
                val x = p.a
                val seat = p.b
                lineXY(c, x - 0.12f, seat, x + 0.12f, seat, prop)
                lineXY(c, x - 0.12f, seat - 0.30f, x - 0.12f, GROUND, prop)
                lineXY(c, x + 0.10f, seat, x + 0.10f, GROUND, prop)
            }
            2 -> {
                lineXY(c, p.a, p.c, p.b, p.c, prop)
                lineXY(c, p.a + 0.03f, p.c, p.a + 0.03f, GROUND, prop)
                lineXY(c, p.b - 0.03f, p.c, p.b - 0.03f, GROUND, prop)
            }
            3 -> {
                plumb.color = Palette.accent
                plumb.strokeWidth = 0.012f * u
                plumb.pathEffect = DashPathEffect(floatArrayOf(0.03f * u, 0.02f * u), 0f)
                lineXY(c, p.a, 0.0f, p.a, GROUND, plumb)
            }
        }
    }

    companion object {
        private const val LT = 0.26f
        private const val LU = 0.16f
        private const val LF = 0.15f
        private const val LH = 0.20f
        private const val LS = 0.20f
        private const val LFT = 0.07f
        private const val NECK = 0.045f
        private const val HEADR = 0.055f
        private const val SHW = 0.085f
        private const val HPW = 0.045f
        private const val GROUND = 0.865f
        private const val VIEW_W = 1.4f
        private const val VIEW_H = 1.0f
        private const val VIEW_TOP = -0.06f

        // point indices
        private const val H = 0
        private const val S = 1
        private const val HC = 2
        private const val E = 3
        private const val W = 4
        private const val EB = 5
        private const val WB = 6
        private const val K = 7
        private const val A = 8
        private const val T = 9
        private const val KB = 10
        private const val AB = 11
        private const val TB = 12
        private const val C = 13
        private const val SL = 14
        private const val SR = 15
        private const val HL = 16
        private const val HR = 17
    }
}

/** Adds the picture for exercise [key] to this column, if there is one. Returns the view so the caller can tweak it. */
fun LinearLayout.poseView(key: String, name: String, maxHeightDp: Int = 230, bottomDp: Int = 8): PoseView? {
    val f = PoseData.get(key) ?: return null
    val v = PoseView(context, maxHeightDp)
    v.contentDescription = "Simple stick-figure picture of $name" + (if (f.b != null) ", showing the start and the end of the movement" else "")
    v.show(f)
    addView(v, lp(MATCH, WRAP).apply { bottomMargin = context.dp(bottomDp) })
    return v
}

/** The one-line explanation shown under a picture. */
fun poseHint(key: String): String {
    val f = PoseData.get(key) ?: return ""
    return if (f.b == null) "A simple picture of the position to hold."
    else if (Perf.animate) "A simple picture of the movement. Tap it to pause or play."
    else "A simple picture of the movement. Tap it to switch between the start and the end."
}

/**
 * Developer-only screen (reached through a deep link, not from any menu): shows every exercise picture as still start/end
 * frames, twelve at a time, so the drawings can be checked on a real device build.
 */
class PoseGalleryScreen(host: CenterActivity, private val page: Int) : Screen(host) {
    override val title = "Picture check ${page + 1}"

    override fun build(): View {
        val col = host.vbox()
        val keys = Exercises.all.map { it.key }.drop(page * 12).take(12)
        var i = 0
        while (i < keys.size) {
            val row = host.hbox()
            for (j in 0 until 2) {
                val k = keys.getOrNull(i + j)
                val cell = host.vbox()
                if (k != null) {
                    cell.para(k, sp = 10f, bottom = 0)
                    val f = PoseData.get(k)
                    if (f != null) {
                        val r = host.hbox()
                        val a = PoseView(host, 70)
                        a.showAt(f, 0f)
                        r.addView(a, lp(0, WRAP, 1f))
                        if (f.b != null) {
                            val b = PoseView(host, 70)
                            b.showAt(f, 1f)
                            r.addView(b, lp(0, WRAP, 1f))
                        } else {
                            r.addView(View(host), lp(0, WRAP, 1f))
                        }
                        cell.addView(r, lp(MATCH, WRAP))
                    }
                }
                row.addView(cell, lp(0, WRAP, 1f))
            }
            col.addView(row, lp(MATCH, WRAP))
            i += 2
        }
        return col
    }
}
