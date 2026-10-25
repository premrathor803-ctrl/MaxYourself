package com.elevate.launcher.center.ui

import android.view.View
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.MeasureRepo
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.SleepRepo
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.lp

/**
 * Charts built only from what was recorded. Each block says whether its numbers are "Recorded" (taken as entered)
 * or "Calculated" (worked out from entries). Blocks with no data show an empty state instead of a blank chart.
 */
class ProgressScreen(host: CenterActivity) : Screen(host) {
    override val title = "Progress"

    override fun build(): View {
        val col = host.vbox()
        col.noteCard("Everything on this page is drawn from what you recorded on this phone. Nothing is estimated or made up, and nothing scores your looks.")
        val today = Days.today()

        // ------------------------------------------------------------ workouts
        col.heading("Workouts per week", 6)
        val completed = WorkoutRepo.sessions(1000).filter { it.status == "completed" }
        if (completed.isEmpty()) {
            col.emptyState("No completed workouts yet", "Finish a workout and your weekly chart appears here.", "Choose a workout") { host.openSection("workouts") }
        } else {
            val thisWeek = Days.weekStart(today)
            val weeks = (7 downTo 0).map { Days.add(thisWeek, -7 * it) }
            val counts = weeks.map { w -> WorkoutRepo.completedCount(w, Days.add(w, 6)).toFloat() }
            col.card {
                val chart = BarChart(host)
                chart.set(counts, weeks.map { Days.short(it) }, null, "", counts.size - 1)
                addView(chart, lp(MATCH, WRAP))
                para("Recorded: completed workouts counted in the week they were finished (weeks start on Monday). The last bar is this week so far.", sp = 12f, bottom = 0)
            }
            val cats = WorkoutRepo.categoryCounts(Days.add(today, -29), today)
            if (cats.isNotEmpty()) {
                col.card {
                    cardTitle("Last 30 days by type")
                    for ((c, n) in cats) {
                        val row = host.hbox()
                        row.addView(UiKit.text(host, c, 14f, Palette.text), lp(0, WRAP, 1f))
                        row.addView(UiKit.text(host, n.toString(), 14f, Palette.accent, true))
                        addView(row, lp(MATCH, WRAP).apply { bottomMargin = host.dp(4) })
                    }
                    para("Recorded: number of completed workouts of each type.", sp = 12f, bottom = 0)
                }
            }
        }

        // ------------------------------------------------------------ habits
        col.heading("Habits", 12)
        val habits = HabitRepo.all()
        if (habits.isEmpty()) {
            col.emptyState("No habits yet", "Add a habit and tick it off to see how consistent you are.", "Open habits") { host.openSection("habits") }
        } else {
            val from = Days.add(today, -13)
            val days = Days.range(from, today)
            val done = IntArray(days.size)
            val sched = IntArray(days.size)
            for (h in habits) {
                val lg = HabitRepo.logs(h.id, from, today)
                val wd = if (h.method == 1) WorkoutRepo.completedDays(from, today) else emptySet()
                for ((i, d) in days.withIndex()) {
                    when (HabitRepo.stateOf(h, d, lg, today, wd)) {
                        HState.COMPLETED -> { done[i]++; sched[i]++ }
                        HState.NOT_COMPLETED -> sched[i]++
                        else -> { }
                    }
                }
            }
            val any = sched.any { it > 0 }
            if (!any) {
                col.emptyState("Nothing to chart yet", "Habits scheduled on past days will show up here.")
            } else {
                val pct = days.indices.map { if (sched[it] == 0) 0f else done[it] * 100f / sched[it] }
                col.card {
                    cardTitle("Habits completed per day, last 14 days")
                    val chart = BarChart(host)
                    chart.set(pct, days.map { Days.dayLetter(it) }, 100f, "%", days.size - 1)
                    addView(chart, lp(MATCH, WRAP))
                    para("Calculated: for each day, the share of habits scheduled that day that you completed. Skipped habits are left out and days with nothing scheduled show as empty bars.", sp = 12f, bottom = 0)
                }
            }
            col.heading("Runs", 10)
            for (h in habits) {
                val st = HabitRepo.stats(h)
                val last30 = if (st.scheduled30 == 0) "no scheduled days yet" else "${st.done30} of ${st.scheduled30} in 30 days"
                col.tapCard(h.name, "Current run ${st.currentRun} · best run ${st.bestRun} · $last30") { host.push(HabitDetailScreen(host, h.id)) }
            }
            col.para("Runs count scheduled days in a row that you completed. Skips and unscheduled days do not break a run.", sp = 12f)
        }

        // ------------------------------------------------------------ optional trackers
        val heights = if (Center.moduleOn("height", false)) MeasureRepo.list("height") else emptyList()
        if (heights.isNotEmpty()) {
            col.heading("Height", 12)
            col.tapCard("${heights.size} readings recorded", "Latest: " + String.format(java.util.Locale.US, "%.1f cm", heights.last().value) + " on " + Days.pretty(heights.last().day)) {
                host.openSection("height")
            }
        }
        val sleeps = if (Center.moduleOn("sleep", false)) SleepRepo.recent(14) else emptyList()
        if (sleeps.isNotEmpty()) {
            col.heading("Sleep", 12)
            val avg = sleeps.mapNotNull { it.hours }.average()
            col.tapCard("${sleeps.size} nights logged", "Average " + String.format(java.util.Locale.US, "%.1f", avg) + " h (calculated from your bed and wake times)") {
                host.openSection("sleep")
            }
        }
        col.gap(8)
        col.tapCard("Workout history", "Every session you completed or ended early") { host.openSection("history") }
        return col
    }
}
