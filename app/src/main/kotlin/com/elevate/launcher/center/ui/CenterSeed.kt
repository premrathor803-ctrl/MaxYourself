package com.elevate.launcher.center.ui

import com.elevate.launcher.center.content.Plans
import com.elevate.launcher.center.content.Starters
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.Goal
import com.elevate.launcher.center.data.GoalRepo
import com.elevate.launcher.center.data.Habit
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.MeasureRepo
import com.elevate.launcher.center.data.SessionEx
import com.elevate.launcher.center.data.SleepEntry
import com.elevate.launcher.center.data.SleepRepo
import com.elevate.launcher.center.data.WorkoutRepo

/**
 * Sample data used only by the automated emulator test, which starts the Center with the "ci_seed" extra so the
 * screenshots show charts and lists with content. Normal use never calls this, and it does nothing if the person
 * already has habits or workouts.
 */
object CenterSeed {
    fun run() {
        if (HabitRepo.all(true).isNotEmpty() || WorkoutRepo.sessions(1).isNotEmpty()) return
        val today = Days.today()
        val start = Days.add(today, -20)

        val picks = Starters.all.take(5) + Starters.all.last()
        val ids = picks.map { s ->
            HabitRepo.insert(Habit(0, s.name, s.descr, s.category, s.slot, s.freq, s.days, start, s.target, s.method))
        }
        val past = Days.range(start, Days.add(today, -1))
        for ((hi, hid) in ids.withIndex()) {
            for ((di, d) in past.withIndex()) {
                val r = (di * 7 + hi * 3) % 10
                if (r < 6) HabitRepo.setState(hid, d, HabitRepo.LOG_DONE)
                else if (r == 6) HabitRepo.setState(hid, d, HabitRepo.LOG_SKIPPED)
            }
        }

        val keys = listOf("beginner_full_a", "beginner_full_b", "core_basic", "mobility_flex")
        val now = System.currentTimeMillis()
        var n = 0
        for (offset in listOf(-18, -16, -13, -11, -9, -6, -4, -2)) {
            val plan = Plans.get(keys[n % keys.size]) ?: continue
            n++
            val ended = now + offset * 86400000L
            val started = ended - (plan.minutes * 60000L)
            WorkoutRepo.saveSession(plan.key, plan.name, plan.category, started, ended, "completed", plan.steps.map { SessionEx(it.key, it.ex.name, "done") })
        }

        Center.setModule("height", true)
        val hs = listOf(-90 to 170.2, -60 to 170.5, -35 to 170.3, -14 to 170.6, -1 to 170.4)
        for ((off, v) in hs) MeasureRepo.add("height", v, Days.add(today, off), "")

        Center.setModule("sleep", true)
        for (i in 1..7) {
            val bed = if (i % 2 == 0) "23:30" else "23:00"
            val wake = if (i % 3 == 0) "06:30" else "07:00"
            SleepRepo.save(SleepEntry(Days.add(today, -i), bed, wake, 2 + i % 3, ""))
        }

        GoalRepo.insert(Goal(0, "Twelve workouts this month", "", GoalRepo.KIND_WORKOUTS, 0L, 12.0, "", Days.add(today, -20), Days.add(today, 10), "", false))
    }
}
