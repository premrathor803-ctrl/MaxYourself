package com.elevate.launcher.core

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/** The rules for how much motion is allowed. Pure, so it can be unit tested without a phone. */
object MotionMath {
    const val OFF = 0
    const val REDUCED = 1
    const val FULL = 2

    /**
     * [pref] is the Elevate animation setting (full, reduced, off), [perf] the performance profile
     * (balanced, lightweight, minimal) and [systemScale] the phone's animator duration scale (0 when
     * the person has turned animations off in Android's own settings).
     */
    fun level(pref: String, perf: String, systemScale: Float): Int {
        if (systemScale <= 0f) return OFF
        if (pref == "off" || perf == "minimal") return OFF
        if (pref == "reduced" || perf == "lightweight") return REDUCED
        return FULL
    }

    /** Duration after the level is applied: full as given, reduced at 60 percent, off at zero. */
    fun duration(base: Long, level: Int): Long = when (level) {
        FULL -> base
        REDUCED -> (base * 0.6).toLong()
        else -> 0L
    }

    /** How many items of a screen get an entrance animation. Fewer when motion is reduced, none when it is off. */
    fun entranceCount(level: Int): Int = when (level) {
        FULL -> 8
        REDUCED -> 3
        else -> 0
    }
}

/**
 * Animation helpers used by every screen. All of them do nothing when motion is off, and none of them is needed to use
 * the app: every animation ends on the same state that is drawn without it.
 */
object Motion {
    private var app: Context? = null
    private var cachedScale = 1f
    private var cachedAt = 0L

    fun init(ctx: Context) { app = ctx.applicationContext }

    private fun systemScale(): Float {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - cachedAt < 1000L && cachedAt != 0L) return cachedScale
        val a = app ?: return 1f
        cachedScale = try {
            Settings.Global.getFloat(a.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (t: Throwable) {
            1f
        }
        cachedAt = now
        return cachedScale
    }

    /** Forces the next [level] call to read the phone's setting again (the emulator test changes it while running). */
    fun refresh() { cachedAt = 0L }

    /** False when the phone's own animation setting is off. Elevate's setting cannot turn animation back on past it. */
    val systemAllows: Boolean get() = systemScale() > 0f

    val level: Int get() = MotionMath.level(Prefs.motion, Prefs.perf, systemScale())
    val on: Boolean get() = level > 0

    fun dur(base: Long): Long = MotionMath.duration(base, level)

    /** A press makes the control sink slightly, then come back. The touch is not consumed, so clicks still work. */
    fun press(v: View) {
        v.setOnTouchListener { view, e ->
            if (level > 0) {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(dur(90L)).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(dur(140L)).setInterpolator(DecelerateInterpolator()).start()
                }
            }
            false
        }
    }

    /**
     * Cards slide up a little and fade in one after another. Only the first few children are animated so a long
     * screen does not take long to settle, and the rest are simply there.
     */
    fun enter(group: ViewGroup) {
        val n = MotionMath.entranceCount(level)
        if (n == 0) return
        val rise = group.context.dpf(14f)
        val count = Math.min(group.childCount, n)
        for (i in 0 until count) {
            val v = group.getChildAt(i)
            v.alpha = 0f
            v.translationY = rise
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(dur(i * 45L))
                .setDuration(dur(260L))
                .setInterpolator(DecelerateInterpolator(1.6f))
                .start()
        }
    }

    fun fadeIn(v: View, base: Long = 200L) {
        if (!on) { v.alpha = 1f; return }
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(dur(base)).setInterpolator(DecelerateInterpolator()).start()
    }

    /** A short "it worked" pulse: grows a little past its size and settles. */
    fun pop(v: View) {
        if (!on) return
        v.animate().cancel()
        v.scaleX = 0.86f
        v.scaleY = 0.86f
        v.animate().scaleX(1f).scaleY(1f).setDuration(dur(320L)).setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    /**
     * Shows or hides [content] by growing or shrinking its height. [widthPx] is the width it will have, needed to know the
     * height it grows to. With motion off it simply appears or disappears.
     */
    fun expand(content: View, widthPx: Int, show: Boolean) {
        val lp = content.layoutParams
        if (!on || widthPx <= 0) {
            content.visibility = if (show) View.VISIBLE else View.GONE
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            content.layoutParams = lp
            return
        }
        if (show) {
            content.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val target = content.measuredHeight
            lp.height = 0
            content.layoutParams = lp
            content.visibility = View.VISIBLE
            content.alpha = 0f
            val a = ValueAnimator.ofInt(0, target)
            a.duration = dur(260L)
            a.interpolator = DecelerateInterpolator()
            a.addUpdateListener {
                lp.height = it.animatedValue as Int
                content.layoutParams = lp
                content.alpha = (it.animatedValue as Int).toFloat() / Math.max(1, target)
            }
            a.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    content.layoutParams = lp
                    content.alpha = 1f
                }
            })
            a.start()
        } else {
            val from = content.height
            val a = ValueAnimator.ofInt(from, 0)
            a.duration = dur(220L)
            a.interpolator = DecelerateInterpolator()
            a.addUpdateListener {
                lp.height = it.animatedValue as Int
                content.layoutParams = lp
                content.alpha = (it.animatedValue as Int).toFloat() / Math.max(1, from)
            }
            a.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    content.visibility = View.GONE
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    content.layoutParams = lp
                    content.alpha = 1f
                }
            })
            a.start()
        }
    }

    /** Shows [v] for a moment (it must already be in a layout), then fades it out and removes it. */
    fun flash(v: View, holdMs: Long = 1300L) {
        val remove = Runnable { (v.parent as? ViewGroup)?.removeView(v) }
        if (!on) { v.postDelayed(remove, holdMs + 500L); return }
        v.alpha = 0f
        v.translationY = v.context.dpf(6f)
        v.animate().alpha(1f).translationY(0f).setDuration(dur(220L)).withEndAction {
            v.animate().alpha(0f).translationY(-v.context.dpf(8f)).setStartDelay(holdMs).setDuration(dur(400L)).withEndAction(remove).start()
        }.start()
    }

    /** Animates a number 0..1 from [from] to [to] and calls [step] each frame. Ends exactly on [to]. */
    fun value(from: Float, to: Float, base: Long, step: (Float) -> Unit): ValueAnimator? {
        if (!on || from == to) { step(to); return null }
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = dur(base)
        a.interpolator = DecelerateInterpolator(1.4f)
        a.addUpdateListener { step(it.animatedValue as Float) }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) { step(to) }
            override fun onAnimationCancel(animation: android.animation.Animator) { step(to) }
        })
        a.start()
        return a
    }
}
