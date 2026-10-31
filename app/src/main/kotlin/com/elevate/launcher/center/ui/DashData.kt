package com.elevate.launcher.center.ui

import com.elevate.launcher.center.data.ActivityRepo
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.Measure
import com.elevate.launcher.center.data.MeasureRepo
import com.elevate.launcher.center.data.SleepRepo
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.center.data.XpMath
import com.elevate.launcher.center.content.Plans
import com.elevate.launcher.gaming.Gaming
import com.elevate.launcher.gaming.GameStore
import com.elevate.launcher.gaming.SessionMath

/** One habit that is due today, with everything the dashboard card needs so it never has to ask the database. */
class DashHabit(
    val id: Long, val name: String, val sub: String, val state: HState, val method: Int, val manualDone: Boolean
)

/** Everything the dashboard shows, read in one go on a background thread. */
class DashData(
    val today: String,
    val habitsAll: Int,
    val due: List<DashHabit>,
    val workoutDone: Boolean,
    val resumeName: String?, val resumeDone: Int, val resumeTotal: Int, val resumeStarted: String,
    val planKey: String?,
    val weekDoneDays: Set<String>,
    val plannedThisWeek: Int,
    val moveDays: Int, val daysSoFar: Int,
    val moveItemsWeek: Int,
    val heightOn: Boolean, val heights: List<Measure>,
    val gamingOn: Boolean, val gameMinToday: Int, val gameSessionsToday: Int,
    val stats: XpMath.Stats,
    val xp: Int,
    val level: XpMath.Level,
    val achievements: List<XpMath.Achievement>
) {
    val habitsDone: Int get() = due.count { it.state == HState.COMPLETED }

    /** Changes whenever anything shown changes, so the screen only redraws when it must. */
    fun sig(): String =
        listOf(
            today, habitsAll, due.joinToString("|") { it.id.toString() + ":" + it.state.name }, workoutDone, resumeName, resumeDone, resumeTotal,
            planKey, weekDoneDays.sorted().joinToString(","), plannedThisWeek, moveDays, moveItemsWeek, heightOn,
            heights.takeLast(8).joinToString(",") { it.id.toString() + "=" + it.value }, gamingOn, gameMinToday, gameSessionsToday, xp,
            achievements.count { it.unlocked }, Center.heightUnit
        ).joinToString(";")

    val ringHabits: Float get() = if (due.isEmpty()) 0f else habitsDone.toFloat() / due.size
    val ringWorkouts: Float
        get() = if (plannedThisWeek > 0) Math.min(1f, weekDoneDays.size.toFloat() / plannedThisWeek) else if (workoutDone) 1f else 0f
    val ringMovement: Float get() = if (daysSoFar <= 0) 0f else Math.min(1f, moveDays.toFloat() / daysSoFar)
}

object DashLoad {
    private const val LONG_AGO = "2000-01-01"

    fun load(): DashData {
        val today = Days.today()
        val ws = Days.weekStart(today)
        val habits = HabitRepo.all()
        val workoutDays = WorkoutRepo.completedDays(today, today)
        val due = ArrayList<DashHabit>()
        for (h in HabitRepo.dueToday()) {
            val logs = HabitRepo.logs(h.id, today, today)
            val st = HabitRepo.stateOf(h, today, logs, today, workoutDays)
            val slot = HabitRepo.slots.firstOrNull { it.first == h.slot }?.second ?: ""
            val bits = ArrayList<String>()
            if (h.slot != "anytime") bits.add(slot)
            if (h.target.isNotEmpty()) bits.add(h.target)
            due.add(DashHabit(h.id, h.name, bits.joinToString(" · "), st, h.method, logs[today] == HabitRepo.LOG_DONE))
        }

        val resume = WorkoutRepo.loadResume()
        val resumePlan = resume?.let { Plans.get(it.planKey) }
        val weekDone = WorkoutRepo.completedDays(ws, Days.add(ws, 6))
        val planned = Integer.bitCount(Center.trainingDays and 127)
        val weekAct = ActivityRepo.between(ws, today)

        val heightOn = Center.moduleOn("height", false)
        val heights = if (heightOn) MeasureRepo.list("height") else emptyList()

        // Games: minutes of tracked sessions that overlap today (the part of a session that falls before midnight is left out).
        val dayStart = SessionMath.dayStart(System.currentTimeMillis())
        val dayEnd = dayStart + 24L * 60L * 60_000L
        var gameMs = 0L
        var gameN = 0
        try {
            for (s in GameStore.overlapping(dayStart, dayEnd)) {
                val part = Math.min(s.end, dayEnd) - Math.max(s.start, dayStart)
                if (part > 0) { gameMs += part; gameN++ }
            }
        } catch (t: Throwable) {
            android.util.Log.w("Elevate", "could not read game sessions for the dashboard", t)
        }

        var checks = 0
        for (h in HabitRepo.all(true)) checks += HabitRepo.completedCount(h, LONG_AGO, today)
        val stats = XpMath.Stats(
            habitChecks = checks,
            workouts = WorkoutRepo.completedCount(LONG_AGO, today),
            movements = ActivityRepo.count(),
            sleepNights = SleepRepo.recent(100000).size,
            heightReadings = MeasureRepo.list("height").size
        )
        val xp = XpMath.xp(stats)
        return DashData(
            today, habits.size, due, WorkoutRepo.completedToday(),
            resumePlan?.name, resume?.states?.values?.count { it == "done" || it == "skipped" } ?: 0, resumePlan?.steps?.size ?: 0,
            resume?.let { Days.timeOf(it.startedMs) } ?: "",
            Plans.suggestedFor(today)?.key,
            weekDone, planned,
            weekAct.map { it.day }.toSet().size, Days.dow(today) + 1,
            weekAct.count { it.kind == ActivityRepo.KIND_EXERCISE },
            heightOn, heights,
            Gaming.on, (gameMs / 60_000L).toInt(), gameN,
            stats, xp, XpMath.level(xp), XpMath.achievements(stats)
        )
    }
}
