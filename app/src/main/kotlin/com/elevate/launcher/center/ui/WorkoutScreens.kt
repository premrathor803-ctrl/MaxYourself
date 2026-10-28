package com.elevate.launcher.center.ui

import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.elevate.launcher.center.content.Exercise
import com.elevate.launcher.center.content.Exercises
import com.elevate.launcher.center.content.Plan
import com.elevate.launcher.center.content.PlanStep
import com.elevate.launcher.center.content.PoseData
import com.elevate.launcher.center.content.Plans
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

const val WORKOUT_SAFETY =
    "Check with a doctor first if you have a heart, joint, breathing or other health condition, are pregnant, or are recovering from an injury. Stop if you feel sharp pain, dizziness or chest discomfort."

// ------------------------------------------------------------------ shared entry points
fun startPlan(host: CenterActivity, plan: Plan) {
    val r = WorkoutRepo.loadResume()
    if (r == null) {
        host.push(PlayerScreen(host, plan, null))
        return
    }
    if (r.planKey != plan.key) {
        val old = Plans.get(r.planKey)?.name ?: "a workout"
        Dialogs.confirm(host, "Start a new workout?", "\"$old\" is still in progress. Starting this one discards it.", "Start new") {
            WorkoutRepo.clearResume()
            host.push(PlayerScreen(host, plan, null))
        }
        return
    }
    Dialogs.list(host, plan.name + " is in progress", listOf("Resume where I left off", "Start over")) { i ->
        if (i == 0) host.push(PlayerScreen(host, plan, r))
        else { WorkoutRepo.clearResume(); host.push(PlayerScreen(host, plan, null)) }
    }
}

fun resumeWorkout(host: CenterActivity) {
    val r = WorkoutRepo.loadResume() ?: return
    val plan = Plans.get(r.planKey)
    if (plan == null) {
        WorkoutRepo.clearResume()
        host.toast("That workout is no longer available")
        host.refresh()
        return
    }
    host.push(PlayerScreen(host, plan, r))
}

/** Two buttons in a row; the first is the main action. */
fun LinearLayout.buttonRow(primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>? = null) {
    val ctx = context
    val row = ctx.hbox()
    row.addView(UiKit.button(ctx, primary.first, true, primary.second), lp(0, WRAP, 1f))
    if (secondary != null) {
        row.addView(UiKit.button(ctx, secondary.first, false, secondary.second), lp(0, WRAP, 1f).apply { leftMargin = ctx.dp(8) })
    }
    addView(row, lp(MATCH, WRAP).apply { topMargin = ctx.dp(8) })
}

fun LinearLayout.resumeCard(host: CenterActivity) {
    val r = WorkoutRepo.loadResume() ?: return
    val plan = Plans.get(r.planKey)
    if (plan == null) { WorkoutRepo.clearResume(); return }
    val total = plan.steps.size
    val done = r.states.values.count { it == "done" || it == "skipped" }
    card {
        para("Workout in progress", sp = 12.5f, bottom = 2)
        cardTitle(plan.name)
        para("Started ${Days.timeOf(r.startedMs)} · $done of $total exercises finished. Resuming starts again from the exercise you were on.", sp = 13f)
        buttonRow("Resume" to { resumeWorkout(host) }, "Discard" to {
            Dialogs.confirm(host, "Discard this workout?", "Nothing from it will be saved.", "Discard") {
                WorkoutRepo.clearResume()
                host.refresh()
            }
        })
    }
}

fun LinearLayout.planCard(host: CenterActivity, plan: Plan, tag: String) {
    card {
        para(tag, sp = 12.5f, bottom = 2)
        cardTitle(plan.name)
        para("About ${plan.minutes} min · ${plan.levelName} · ${pluralize(plan.steps.size, "exercise")}", sp = 13f, bottom = 4)
        para(plan.blurb, sp = 13f, bottom = 0)
        buttonRow("Start" to { startPlan(host, plan) }, "Details" to { host.push(PlanDetailScreen(host, plan)) })
    }
}

fun LinearLayout.todayWorkoutCard(host: CenterActivity) {
    val today = Days.today()
    if (WorkoutRepo.completedToday()) {
        card {
            cardTitle("Workout done today")
            para("You finished a workout today. Another gentle session is optional; rest counts too.", sp = 13f)
            buttonRow("See history" to { host.push(HistoryScreen(host)) }, "Choose another" to { host.push(PlanListScreen(host)) })
        }
        return
    }
    val plan = Plans.suggestedFor(today)
    if (plan != null) {
        planCard(host, plan, "Suggested for today")
    } else {
        val rest = Plans.restDayPlan()
        card {
            para("Rest day", sp = 12.5f, bottom = 2)
            cardTitle("No workout planned today")
            para("Your training days are ${trainingDaysText()}. Rest is part of training. A gentle recovery session is there if you want it (about ${rest.minutes} min).", sp = 13f, bottom = 0)
            buttonRow("Gentle recovery" to { startPlan(host, rest) }, "Choose a workout" to { host.push(PlanListScreen(host)) })
        }
    }
}

fun trainingDaysText(): String {
    val mask = Center.trainingDays
    val list = (0..6).filter { (mask shr it) and 1 == 1 }.map { Days.dowNames[it] }
    return if (list.isEmpty()) "not set" else list.joinToString(", ")
}

/** Seven dots for this week: filled = workout completed. */
fun LinearLayout.weekStrip() {
    val ctx = context
    val today = Days.today()
    val ws = Days.weekStart(today)
    val done = WorkoutRepo.completedDays(ws, Days.add(ws, 6))
    val row = ctx.hbox()
    for (i in 0..6) {
        val d = Days.add(ws, i)
        val col = ctx.vbox()
        col.gravity = Gravity.CENTER_HORIZONTAL
        val dot = StateDot(ctx)
        dot.kind = when {
            d in done -> 1
            d == today -> 0
            d > today && Plans.isTrainingDay(d) -> 0
            else -> 4
        }
        val t = UiKit.text(ctx, Days.dayLetter(d), 12f, if (Plans.isTrainingDay(d)) Palette.text else Palette.dim, Plans.isTrainingDay(d))
        t.gravity = Gravity.CENTER
        col.addView(dot, lp(WRAP, WRAP))
        col.addView(t, lp(WRAP, WRAP))
        row.addView(col, lp(0, WRAP, 1f))
    }
    addView(row, lp(MATCH, WRAP).apply { bottomMargin = ctx.dp(6) })
}

fun LinearLayout.trainingPrefsCard() {
    card {
        cardTitle("Your training week")
        para("Pick the days you want to work out. Suggestions only appear on these days, and you can start any workout on any day.", sp = 13f)
        addView(context.dayChips(Center.trainingDays) { Center.trainingDays = it }, lp(MATCH, WRAP).apply { bottomMargin = context.dp(10) })
        para("Level", sp = 12.5f, bottom = 4)
        addView(context.chips(listOf("beginner" to "Beginner", "intermediate" to "Intermediate"), Center.level) { Center.level = it }, lp(MATCH, WRAP))
        para("Level only changes which workouts are suggested. Every plan stays available.", sp = 12f, bottom = 0)
    }
}

// =============================================================================================
class WorkoutsScreen(host: CenterActivity) : Screen(host) {
    override val title = "Workouts"

    override fun build(): View {
        val col = host.vbox()
        col.resumeCard(host)
        val ws = Days.weekStart(Days.today())
        val n = WorkoutRepo.completedCount(ws, Days.add(ws, 6))
        col.heading("This week", 4)
        col.card {
            weekStrip()
            para("${pluralize(n, "workout")} completed this week. Filled circle: workout completed. Ring: planned or today. Bold letters are your training days.", sp = 12.5f, bottom = 0)
        }
        col.todayWorkoutCard(host)
        col.heading("Browse")
        col.tapCard("Choose a workout", "${Plans.all.size} plans, from gentle to harder") { host.push(PlanListScreen(host)) }
        col.tapCard("Exercise library", "${Exercises.all.size} exercises with written instructions") { host.push(ExerciseLibraryScreen(host, null)) }
        col.tapCard("Workout history", "Everything you have completed or ended early") { host.push(HistoryScreen(host)) }
        col.gap(6)
        col.trainingPrefsCard()
        col.noteCard(WORKOUT_SAFETY)
        col.noteCard("Exercises are explained in writing. There are no videos or pictures in this version, so go slowly and read each step.")
        return col
    }
}

// =============================================================================================
class PlanListScreen(host: CenterActivity) : Screen(host) {
    override val title = "Choose a workout"

    override fun build(): View {
        val col = host.vbox()
        col.para("No equipment needed. A wall, a sturdy chair and a mat or towel are enough.", sp = 13.5f)
        for (cat in Plans.categories) {
            col.heading(cat, 10)
            for (p in Plans.all.filter { it.category == cat }) {
                col.tapCard(p.name, "About ${p.minutes} min · ${p.levelName} · ${pluralize(p.steps.size, "exercise")}") {
                    host.push(PlanDetailScreen(host, p))
                }
            }
        }
        col.noteCard(WORKOUT_SAFETY)
        return col
    }
}

// =============================================================================================
class PlanDetailScreen(host: CenterActivity, private val plan: Plan) : Screen(host) {
    override val title = plan.name

    override fun build(): View {
        val col = host.vbox()
        col.para(plan.blurb, dim = false, sp = 14.5f)
        col.card {
            statRow(listOf("~${plan.minutes} min" to "Time", plan.levelName to "Level", plan.steps.size.toString() to "Exercises"))
            para(plan.equipment, sp = 12.5f, bottom = 0)
        }
        col.actionButton("Start workout") { startPlan(host, plan) }
        section(col, "Warm-up", plan.warm)
        section(col, "Main", plan.main)
        section(col, "Cool-down", plan.cool)
        col.gap(6)
        col.noteCard(WORKOUT_SAFETY)
        col.actionButton("Start workout") { startPlan(host, plan) }
        return col
    }

    private fun section(col: LinearLayout, name: String, steps: List<PlanStep>) {
        if (steps.isEmpty()) return
        col.heading(name, 10)
        for (s in steps) col.tapCard(s.ex.name, s.describe()) { host.push(ExerciseDetailScreen(host, s.ex)) }
    }
}

// =============================================================================================
class ExerciseLibraryScreen(host: CenterActivity, startCat: String?) : Screen(host) {
    override val title = "Exercise library"
    private var cat: String? = startCat
    private var query = ""

    override fun build(): View {
        val col = host.vbox()
        val search = host.field("Search exercises")
        search.setText(query)
        col.addView(search, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })

        val chipsRow = host.hbox()
        val labels = ArrayList<Pair<String?, TextView>>()
        fun paint() {
            for ((k, v) in labels) {
                val on = k == cat
                v.background = UiKit.rounded(if (on) Palette.accent else Palette.surface2, host.dpf(16f))
                v.setTextColor(if (on) Palette.onAccent else Palette.text)
            }
        }
        val listBox = host.vbox()
        fun fill() {
            listBox.removeAllViews()
            var items = if (cat == null) Exercises.all else Exercises.byCategory(cat!!)
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                items = items.filter { it.name.lowercase().contains(q) || it.purpose.lowercase().contains(q) }
            }
            if (items.isEmpty()) {
                listBox.emptyState("No matches", "Try another word or choose All.")
            }
            for (e in items) listBox.tapCard(e.name, "${e.category} · ${e.levelName} · " + (if (e.timed) "timed" else "reps")) {
                host.push(ExerciseDetailScreen(host, e))
            }
        }
        val options = listOf<String?>(null) + Exercises.categories
        for (c in options) {
            val tv = UiKit.text(host, c ?: "All", 13f, Palette.text, true)
            tv.setPadding(host.dp(14), host.dp(8), host.dp(14), host.dp(8))
            tv.setOnClickListener { cat = c; paint(); fill() }
            labels.add(c to tv)
            chipsRow.addView(tv, lp(WRAP, WRAP).apply { rightMargin = host.dp(6) })
        }
        val hs = HorizontalScrollView(host)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chipsRow)
        col.addView(hs, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
        col.addView(listBox, lp(MATCH, WRAP))
        search.doAfterTextChanged { query = it?.toString() ?: ""; fill() }
        paint()
        fill()
        return col
    }
}

// =============================================================================================
class ExerciseDetailScreen(host: CenterActivity, private val ex: Exercise) : Screen(host) {
    override val title = ex.name

    override fun build(): View {
        val col = host.vbox()
        col.para("${ex.category} · ${ex.levelName} · " + (if (ex.timed) "timed hold" else "reps") + (if (ex.perSide) " · each side" else ""), sp = 13f)
        col.para(ex.purpose, dim = false, sp = 14.5f)
        if (PoseData.get(ex.key) != null) col.card {
            poseView(ex.key, ex.name)
            para(poseHint(ex.key), sp = 12f, bottom = 0)
        }
        col.heading("Starting position", 8)
        col.para(ex.startPos, dim = false)
        col.heading("How to do it", 8)
        ex.steps.forEachIndexed { i, s -> col.para("${i + 1}. $s", dim = false, bottom = 4) }
        col.heading("Common mistakes", 12)
        for (m in ex.mistakes) col.para("• $m", dim = false, bottom = 4)
        col.heading("Make it easier or harder", 12)
        col.card {
            para("Easier: ${ex.easier}", dim = false, bottom = 4)
            para("Harder: ${ex.harder}", dim = false, bottom = 0)
        }
        col.heading("Safety", 8)
        col.noteCard(ex.safety)
        val inPlans = Plans.all.filter { p -> p.steps.any { it.key == ex.key } }
        if (inPlans.isNotEmpty()) {
            col.heading("Used in", 12)
            for (p in inPlans) col.tapCard(p.name, "About ${p.minutes} min") { host.push(PlanDetailScreen(host, p)) }
        }
        col.noteCard("Written instructions only. This is general fitness information, not a personal programme or medical advice.")
        return col
    }
}

// =============================================================================================
class HistoryScreen(host: CenterActivity) : Screen(host) {
    override val title = "Workout history"

    override fun build(): View {
        val col = host.vbox()
        val list = WorkoutRepo.sessions()
        if (list.isEmpty()) {
            col.emptyState("No workouts yet", "Finish a workout, or end one early, and it shows up here.", "Choose a workout") { host.push(PlanListScreen(host)) }
            return col
        }
        val completed = list.filter { it.status == "completed" }
        col.card {
            statRow(listOf(
                completed.size.toString() to "Completed",
                completed.sumOf { it.minutes }.toString() to "Minutes (completed)",
                (list.size - completed.size).toString() to "Ended early"
            ))
            para("Calculated from the sessions you saved. Minutes are the time between starting and finishing, so they include rests.", sp = 12f, bottom = 0)
        }
        for (s in list) {
            col.tapCard(
                Days.pretty(s.day) + " · " + s.planName,
                "${s.minutes} min · ${s.statusLabel} · ${s.done} done" + (if (s.skipped > 0) ", ${s.skipped} skipped" else "")
            ) { host.push(SessionDetailScreen(host, s.id)) }
        }
        return col
    }
}

// =============================================================================================
class SessionDetailScreen(host: CenterActivity, private val id: Long) : Screen(host) {
    override val title = "Workout"

    override fun build(): View {
        val col = host.vbox()
        val s = WorkoutRepo.session(id)
        if (s == null) {
            col.emptyState("Not found", "This workout was deleted.", "Back") { host.pop() }
            return col
        }
        col.heading(s.planName, 4)
        col.para("${Days.prettyLong(s.day)} · ${Days.timeOf(s.started)} to ${Days.timeOf(s.ended)} · ${s.minutes} min · ${s.statusLabel}", sp = 13f)
        col.heading("Exercises", 8)
        col.card {
            for (e in WorkoutRepo.exercisesOf(s.id)) {
                val label = when (e.state) {
                    "done" -> "Done"
                    "skipped" -> "Skipped"
                    else -> "Not reached"
                }
                val row = context.hbox()
                row.addView(UiKit.text(context, e.name, 14f, Palette.text), lp(0, WRAP, 1f))
                row.addView(UiKit.text(context, label, 13f, if (e.state == "done") Palette.accent else Palette.dim, true))
                addView(row, lp(MATCH, WRAP).apply { bottomMargin = context.dp(6) })
            }
        }
        col.heading("How did it feel?", 8)
        var diff = s.difficulty
        col.addView(
            host.chips((1..5).map { it.toString() to it.toString() }, if (diff in 1..5) diff.toString() else "") { diff = it.toInt() },
            lp(MATCH, WRAP)
        )
        col.para("1 = too easy, 3 = just right, 5 = too hard", sp = 12f)
        col.fieldLabel("Notes")
        val notes = host.field("Anything to remember next time", s.notes, multiline = true)
        col.addView(notes, lp(MATCH, WRAP))
        col.gap(10)
        col.actionButton("Save notes") {
            WorkoutRepo.setFeedback(s.id, diff, notes.text.toString().trim())
            host.toast("Saved")
        }
        col.actionButton("Delete this workout", false) {
            Dialogs.confirm(host, "Delete this workout?", "It will no longer count toward your history or progress.", "Delete") {
                WorkoutRepo.delete(s.id)
                host.pop()
            }
        }
        return col
    }
}
