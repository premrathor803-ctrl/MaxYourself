package com.elevate.launcher.ui.center

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.elevate.launcher.center.content.Plans
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.center.ui.stateOfDay
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.ui.home.HomeStyle

/** The page to the left of the first home page: a quick look at today plus shortcuts into the Center. */
class CenterPanelView(ctx: Context, private val open: (String?) -> Unit) : ScrollView(ctx) {
    private val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    init {
        isFillViewport = true
        setPadding(ctx.dp(18), ctx.dp(24), ctx.dp(18), ctx.dp(12))
        clipToPadding = false
        addView(col, LayoutParams(MATCH, WRAP))
        refresh()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refresh()
    }

    private fun cardBg() = UiKit.rounded(if (Prefs.wallpaper) 0x66000000 else Palette.surface, context.dpf(18f))

    fun refresh() {
        col.removeAllViews()
        val ctx = context
        col.addView(UiKit.text(ctx, "Center", 30f, HomeStyle.text, true))
        col.addView(UiKit.text(ctx, Days.prettyLong(Days.today()), 14f, HomeStyle.dim), lp(MATCH, WRAP).apply { bottomMargin = ctx.dp(14) })

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding(ctx.dp(16), ctx.dp(14), ctx.dp(16), ctx.dp(14))
            setOnClickListener { open(null) }
        }
        try {
            val today = Days.today()
            val habits = HabitRepo.all()
            val due = HabitRepo.dueToday()
            val habitLine = when {
                habits.isEmpty() -> "No habits yet. Set some up in the Center."
                due.isEmpty() -> "No habits scheduled today."
                else -> due.count { stateOfDay(it, today) == HState.COMPLETED }.toString() + " of " + due.size + " habits done today"
            }
            val workoutLine = when {
                WorkoutRepo.completedToday() -> "Workout done today"
                WorkoutRepo.loadResume() != null -> "Workout in progress. Tap to resume."
                else -> Plans.suggestedFor(today)?.let { "Today: " + it.name + " · about " + it.minutes + " min" } ?: "Rest day today"
            }
            card.addView(UiKit.text(ctx, "Today", 13f, HomeStyle.dim, true))
            card.addView(UiKit.text(ctx, habitLine, 16f, HomeStyle.text, true), lp(MATCH, WRAP).apply { topMargin = ctx.dp(4) })
            card.addView(UiKit.text(ctx, workoutLine, 14f, HomeStyle.dim), lp(MATCH, WRAP).apply { topMargin = ctx.dp(4) })
        } catch (e: Exception) {
            card.addView(UiKit.text(ctx, "Open the Center to see today", 15f, HomeStyle.text, true))
        }
        col.addView(card, lp(MATCH, WRAP).apply { bottomMargin = ctx.dp(12) })

        val tiles = listOf(
            "Habits" to "habits", "Workouts" to "workouts",
            "Grooming" to "grooming", "Posture" to "posture",
            "Goals" to "goals", "Progress" to "progress"
        )
        var i = 0
        while (i < tiles.size) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0..1) {
                val (label, section) = tiles[i + k]
                val t = TextView(ctx).apply {
                    text = label
                    textSize = 15f
                    setTextColor(HomeStyle.text)
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER_VERTICAL
                    background = cardBg()
                    setPadding(ctx.dp(16), 0, ctx.dp(12), 0)
                    minHeight = ctx.dp(56)
                    setOnClickListener { open(section) }
                }
                row.addView(t, lp(0, WRAP, 1f).apply { if (k == 0) rightMargin = ctx.dp(5) else leftMargin = ctx.dp(5) })
            }
            col.addView(row, lp(MATCH, WRAP).apply { bottomMargin = ctx.dp(10) })
            i += 2
        }
        col.addView(UiKit.button(ctx, "Open Center") { open(null) }, lp(MATCH, WRAP).apply { topMargin = ctx.dp(4) })
    }
}
