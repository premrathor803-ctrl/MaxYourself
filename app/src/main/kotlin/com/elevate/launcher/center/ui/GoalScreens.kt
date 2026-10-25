package com.elevate.launcher.center.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.Goal
import com.elevate.launcher.center.data.GoalRepo
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Widgets
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

private fun goalKindName(k: String): String = when (k) {
    GoalRepo.KIND_HABIT -> "Habit goal"
    GoalRepo.KIND_WORKOUTS -> "Workout goal"
    else -> "Custom goal"
}

private fun goalProgressText(g: Goal): String {
    val (cur, target) = GoalRepo.progress(g)
    val unit = when (g.kind) {
        GoalRepo.KIND_HABIT -> "completed days"
        GoalRepo.KIND_WORKOUTS -> "workouts"
        else -> g.unit
    }
    return fmtNum(cur) + " of " + fmtNum(target) + (if (unit.isNotEmpty()) " $unit" else "")
}

private fun goalTimeText(g: Goal): String {
    if (g.done) return "Marked done"
    val left = Days.diff(Days.today(), g.endDay)
    return when {
        left > 1 -> "$left days left"
        left == 1 -> "1 day left"
        left == 0 -> "Ends today"
        else -> "Ended " + Days.short(g.endDay)
    }
}

// =============================================================================================
class GoalsScreen(host: CenterActivity) : Screen(host) {
    override val title = "Goals"
    override val actionLabel: String? = "New"
    override fun onAction() { host.push(GoalFormScreen(host, null)) }

    override fun build(): View {
        val col = host.vbox()
        col.para("Set a target for a period of time. Progress comes only from what you log: ticked habits, completed workouts or entries you add yourself.", sp = 13.5f)
        val goals = GoalRepo.all()
        if (goals.isEmpty()) {
            col.emptyState("No goals yet", "A good goal is small and specific, like \"complete 12 workouts in 30 days\".", "Create a goal") { host.push(GoalFormScreen(host, null)) }
            return col
        }
        for (g in goals) {
            val (cur, target) = GoalRepo.progress(g)
            col.card(10) {
                background = com.elevate.launcher.core.UiKit.pressable(com.elevate.launcher.core.Palette.surface, host.dp(18).toFloat())
                setOnClickListener { host.push(GoalDetailScreen(host, g.id)) }
                cardTitle(g.title)
                para(goalKindName(g.kind) + " · " + goalTimeText(g), sp = 12.5f, bottom = 6)
                val bar = ProgressBar2(host)
                bar.progress = if (target <= 0) 0f else (cur / target).toFloat()
                addView(bar, lp(MATCH, WRAP).apply { bottomMargin = host.dp(6) })
                para(goalProgressText(g), dim = false, sp = 13.5f, bottom = 0)
            }
        }
        return col
    }
}

// =============================================================================================
class GoalFormScreen(host: CenterActivity, private val existing: Goal?) : Screen(host) {
    override val title = if (existing == null) "New goal" else "Edit goal"
    private var kind = existing?.kind ?: GoalRepo.KIND_WORKOUTS
    private var habitId = existing?.habitId ?: 0L
    private var days = if (existing == null) 30 else Math.max(1, Days.diff(existing.startDay, existing.endDay))

    override fun build(): View {
        val col = host.vbox()
        col.fieldLabel("Title")
        val titleField = host.field("e.g. Train three times a week", existing?.title ?: "")
        col.addView(titleField, lp(MATCH, WRAP))

        col.fieldLabel("What counts toward it?")
        val habitBox = host.vbox()
        val customBox = host.vbox()
        fun showKind() {
            habitBox.visibility = if (kind == GoalRepo.KIND_HABIT) View.VISIBLE else View.GONE
            customBox.visibility = if (kind == GoalRepo.KIND_MANUAL) View.VISIBLE else View.GONE
        }
        col.addView(host.chips(listOf(
            GoalRepo.KIND_WORKOUTS to "Workouts", GoalRepo.KIND_HABIT to "A habit", GoalRepo.KIND_MANUAL to "My own number"
        ), kind) { kind = it; showKind() }, lp(MATCH, WRAP))

        val habits = HabitRepo.all()
        if (habits.isEmpty()) {
            habitBox.para("You have no habits yet. Create one in Habits first, then link it here.", sp = 13f)
        } else {
            val chosen = habits.firstOrNull { it.id == habitId } ?: habits.first().also { habitId = it.id }
            var row: LinearLayout? = null
            row = habitBox.pickRow("Habit", chosen.name) {
                Dialogs.list(host, "Choose a habit", habits.map { it.name }) { i ->
                    habitId = habits[i].id
                    (row?.getChildAt(1) as? TextView)?.text = habits[i].name + "  ›"
                }
            }
        }
        col.addView(habitBox, lp(MATCH, WRAP).apply { topMargin = host.dp(8) })

        col.fieldLabel("Target")
        val target = host.field("e.g. 12", if (existing != null) fmtNum(existing.target) else "", number = true)
        col.addView(target, lp(MATCH, WRAP))
        col.para("Workouts and habits: how many completed days or sessions. My own number: the total you want to reach.", sp = 12f)

        val unit = host.field("Unit, e.g. km, pages, glasses", existing?.unit ?: "")
        customBox.addView(unit, lp(MATCH, WRAP))
        col.addView(customBox, lp(MATCH, WRAP))
        showKind()

        col.gap(6)
        col.addView(Widgets.stepper(host, "Days to reach it", days, 7, 365, 1, { "$it days" }) { days = it }, lp(MATCH, WRAP))
        col.fieldLabel("Review note (optional)")
        val review = host.field("What will you check, or change, if you fall behind?", existing?.review ?: "", multiline = true)
        col.addView(review, lp(MATCH, WRAP))

        col.gap(12)
        col.actionButton("Save goal") {
            val t = titleField.text.toString().trim()
            val tv = target.text.toString().replace(',', '.').toDoubleOrNull()
            if (t.isEmpty()) { host.toast("Give the goal a title"); return@actionButton }
            if (tv == null || tv <= 0.0) { host.toast("Enter a target above zero"); return@actionButton }
            if (kind == GoalRepo.KIND_HABIT && HabitRepo.get(habitId) == null) { host.toast("Choose a habit first"); return@actionButton }
            val u = if (kind == GoalRepo.KIND_MANUAL) unit.text.toString().trim() else ""
            val e = existing
            if (e == null) {
                val start = Days.today()
                GoalRepo.insert(Goal(0, t, "", kind, if (kind == GoalRepo.KIND_HABIT) habitId else 0L, tv, u, start, Days.add(start, days), review.text.toString().trim(), false))
            } else {
                e.title = t; e.kind = kind; e.habitId = if (kind == GoalRepo.KIND_HABIT) habitId else 0L
                e.target = tv; e.unit = u; e.endDay = Days.add(e.startDay, days); e.review = review.text.toString().trim()
                GoalRepo.update(e)
            }
            host.pop()
        }
        return col
    }
}

// =============================================================================================
class GoalDetailScreen(host: CenterActivity, private val id: Long) : Screen(host) {
    override val title: String get() = GoalRepo.get(id)?.title ?: "Goal"
    override val actionLabel: String? = "Edit"
    override fun onAction() { GoalRepo.get(id)?.let { host.push(GoalFormScreen(host, it)) } }

    override fun build(): View {
        val col = host.vbox()
        val g = GoalRepo.get(id)
        if (g == null) {
            col.emptyState("Goal not found", "It may have been deleted.", "Back") { host.pop() }
            return col
        }
        val (cur, target) = GoalRepo.progress(g)
        col.card {
            para(goalKindName(g.kind) + " · " + goalTimeText(g), sp = 12.5f, bottom = 6)
            val bar = ProgressBar2(host)
            bar.progress = if (target <= 0) 0f else (cur / target).toFloat()
            addView(bar, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
            para(goalProgressText(g), dim = false, sp = 16f, bottom = 4)
            para(
                when (g.kind) {
                    GoalRepo.KIND_HABIT -> "Counted from completed days of \"" + (HabitRepo.get(g.habitId)?.name ?: "a deleted habit") + "\" between " + Days.short(g.startDay) + " and " + Days.short(g.endDay) + "."
                    GoalRepo.KIND_WORKOUTS -> "Counted from workouts you completed between " + Days.short(g.startDay) + " and " + Days.short(g.endDay) + "."
                    else -> "Counted from the entries you add below."
                },
                sp = 12.5f, bottom = 0
            )
        }
        if (!g.done && cur >= target) col.noteCard("You have reached the target. Mark the goal done when you are ready.")

        if (g.kind == GoalRepo.KIND_MANUAL) {
            col.actionButton("Add progress") { addEntry(g) }
            val entries = GoalRepo.entries(g.id)
            if (entries.isNotEmpty()) {
                col.heading("Entries", 8)
                for (e in entries) {
                    col.tapCard("+" + fmtNum(e.value) + (if (g.unit.isNotEmpty()) " " + g.unit else ""), Days.pretty(e.day) + (if (e.note.isNotEmpty()) " · " + e.note else ""), "") {
                        Dialogs.confirm(host, "Delete this entry?", fmtNum(e.value), "Delete") {
                            GoalRepo.deleteEntry(e.id)
                            host.refresh()
                        }
                    }
                }
                col.para("Tap an entry to delete it.", sp = 12f)
            }
        }

        col.heading("Review note", 10)
        col.card {
            para(if (g.review.isEmpty()) "No note yet. Write what you will check or change if you fall behind." else g.review, dim = g.review.isEmpty(), sp = 14f, bottom = 8)
            addView(com.elevate.launcher.core.UiKit.button(host, "Edit note", false) {
                Dialogs.prompt(host, "Review note", g.review, "What will you check or change?") { v ->
                    g.review = v.trim()
                    GoalRepo.update(g)
                    host.refresh()
                }
            })
        }

        col.gap(6)
        col.actionButton(if (g.done) "Reopen goal" else "Mark goal done", !g.done) {
            g.done = !g.done
            GoalRepo.update(g)
            host.refresh()
        }
        col.actionButton("Delete goal", false) {
            Dialogs.confirm(host, "Delete \"${g.title}\"?", "The goal and its entries are removed. Your habits and workouts are not affected.", "Delete") {
                GoalRepo.delete(g.id)
                host.pop()
            }
        }
        return col
    }

    private fun addEntry(g: Goal) {
        val box = host.vbox()
        val value = host.field("Amount" + (if (g.unit.isNotEmpty()) " (${g.unit})" else ""), "", number = true)
        val note = host.field("Note (optional)")
        box.fieldLabel("Amount")
        box.addView(value, lp(MATCH, WRAP))
        box.fieldLabel("Note")
        box.addView(note, lp(MATCH, WRAP))
        host.formDialog("Add progress", box) {
            val v = value.text.toString().replace(',', '.').toDoubleOrNull()
            if (v == null || v <= 0.0) {
                host.toast("Enter an amount above zero")
                false
            } else {
                GoalRepo.addEntry(g.id, Days.today(), v, note.text.toString().trim())
                host.refresh()
                true
            }
        }
    }
}
