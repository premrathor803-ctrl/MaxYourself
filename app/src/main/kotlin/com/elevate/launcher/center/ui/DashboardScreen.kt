package com.elevate.launcher.center.ui

import android.view.View
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import java.util.Calendar

/** The Center's home: today's habits, today's workout, and shortcuts into every section. */
class DashboardScreen(host: CenterActivity) : Screen(host) {
    override val title = "Center"
    override val actionLabel: String? = "Settings"
    override fun onAction() { host.openSection("settings") }
    override fun onResumeScreen() { host.refresh() }

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 5 -> "Hello"
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    override fun build(): View {
        val col = host.vbox()
        val today = Days.today()
        col.addView(UiKit.text(host, greeting(), 26f, Palette.text, true), lp(MATCH, WRAP))
        col.para(Days.prettyLong(today), sp = 13.5f, bottom = 6)

        col.resumeCard(host)

        // ---- habits
        col.heading("Today's habits", 8)
        val all = HabitRepo.all()
        if (all.isEmpty()) {
            col.para("Habits are small actions you tick off. Nothing is added until you tap Add.", sp = 13.5f)
            col.starterList(4) { host.refresh() }
            col.tapCard("More habit options", "Create your own or browse every starter") { host.openSection("habits") }
        } else {
            val due = HabitRepo.dueToday()
            if (due.isEmpty()) {
                col.para("Nothing is scheduled for today.", sp = 13.5f)
            } else {
                val done = due.count { stateOfDay(it, today) == HState.COMPLETED }
                col.para("$done of ${due.size} done", sp = 13.5f)
                for (h in due.take(8)) col.habitRow(h, today, { host.refresh() }) { host.push(HabitDetailScreen(host, h.id)) }
                if (due.size > 8) col.para("+${due.size - 8} more in Habits", sp = 12.5f)
            }
            col.tapCard("All habits", "History, streaks and editing") { host.openSection("habits") }
        }

        // ---- workout
        col.heading("Today's workout")
        col.todayWorkoutCard(host)

        // ---- this week
        val ws = Days.weekStart(today)
        val workouts = WorkoutRepo.completedCount(ws, Days.add(ws, 6))
        val checkIns = all.sumOf { HabitRepo.completedCount(it, ws, today) }
        col.heading("This week")
        col.card {
            weekStrip()
            para("${pluralize(workouts, "workout")} completed · ${pluralize(checkIns, "habit check-in")} so far", sp = 13f, bottom = 0)
        }

        // ---- explore
        col.heading("Explore")
        col.tileRow(
            Triple("Habits", "Daily routines", { host.openSection("habits") }),
            Triple("Workouts", "Plans and history", { host.openSection("workouts") })
        )
        col.tileRow(
            Triple("Grooming", "Skincare and hair", { host.openSection("grooming") }),
            Triple("Posture", "Mobility and sleep", { host.openSection("posture") })
        )
        col.tileRow(
            Triple("Goals", "Targets you set", { host.openSection("goals") }),
            Triple("Progress", "Charts from your logs", { host.openSection("progress") })
        )
        col.tileRow(
            Triple("Learn", "Short offline guides", { host.openSection("learn") }),
            Triple("Reminders", "Optional nudges", { host.openSection("reminders") })
        )
        col.noteCard("General fitness and wellness information. It is not medical advice, and nothing here rates your looks or promises to change your height. Everything stays on this phone.")
        return col
    }
}
