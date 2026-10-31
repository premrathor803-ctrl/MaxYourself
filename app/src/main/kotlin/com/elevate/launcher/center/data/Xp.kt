package com.elevate.launcher.center.data

/**
 * Experience points and levels, worked out only from what is in the person's own logs.
 *
 * Nothing is stored: XP is recalculated from the records each time, so deleting a record lowers it and it can never
 * drift away from the data. Playing games and recording height earn nothing, on purpose (neither should be encouraged
 * for points), and nothing is ever taken away for missing a day.
 */
object XpMath {
    const val PER_HABIT_CHECK = 10
    const val PER_WORKOUT = 50
    const val PER_MOVEMENT = 10
    const val PER_SLEEP_LOG = 5

    class Stats(
        val habitChecks: Int,
        val workouts: Int,
        val movements: Int,
        val sleepNights: Int,
        val heightReadings: Int
    )

    class Level(val level: Int, val into: Int, val span: Int, val total: Int) {
        val fraction: Float get() = if (span <= 0) 0f else (into.toFloat() / span).coerceIn(0f, 1f)
        val toNext: Int get() = Math.max(0, span - into)
    }

    fun xp(s: Stats): Int =
        Math.max(0, s.habitChecks) * PER_HABIT_CHECK +
            Math.max(0, s.workouts) * PER_WORKOUT +
            Math.max(0, s.movements) * PER_MOVEMENT +
            Math.max(0, s.sleepNights) * PER_SLEEP_LOG

    /** XP at which [level] starts: 0, 100, 300, 600, 1000 ... (each level asks for 100 more than the one before). */
    fun levelStart(level: Int): Int = 50 * (level - 1) * level

    fun level(totalXp: Int): Level {
        val xp = Math.max(0, totalXp)
        var n = 1
        while (levelStart(n + 1) <= xp) n++
        val start = levelStart(n)
        val next = levelStart(n + 1)
        return Level(n, xp - start, next - start, xp)
    }

    fun title(level: Int): String = when {
        level <= 1 -> "Starter"
        level == 2 -> "Steady"
        level == 3 -> "Consistent"
        level == 4 -> "Focused"
        level == 5 -> "Dedicated"
        else -> "Elevated"
    }

    // -------------------------------------------------------------------------- achievements
    class Achievement(val id: String, val title: String, val blurb: String, val icon: String, val goal: Int, value: Int) {
        val value: Int = Math.min(Math.max(0, value), goal)
        val unlocked: Boolean get() = this.value >= goal
        val fraction: Float get() = if (goal <= 0) 1f else this.value.toFloat() / goal
    }

    fun achievements(s: Stats): List<Achievement> {
        val categories = listOf(s.habitChecks, s.workouts, s.movements, s.sleepNights).count { it >= 1 }
        return listOf(
            Achievement("first_check", "First check-in", "Tick off a habit", "habits", 1, s.habitChecks),
            Achievement("checks_10", "Ten check-ins", "Tick off habits ten times", "habits", 10, s.habitChecks),
            Achievement("checks_100", "Hundred check-ins", "A hundred ticks, one at a time", "habits", 100, s.habitChecks),
            Achievement("first_workout", "First workout", "Finish a workout", "workouts", 1, s.workouts),
            Achievement("workouts_5", "Five workouts", "Finish five workouts", "workouts", 5, s.workouts),
            Achievement("workouts_25", "Twenty-five workouts", "Finish twenty-five workouts", "workouts", 25, s.workouts),
            Achievement("movement_5", "Easy mover", "Log five movement or posture activities", "posture", 5, s.movements),
            Achievement("sleep_7", "A week of sleep notes", "Log seven nights of sleep", "sleep", 7, s.sleepNights),
            Achievement("first_measure", "First measurement", "Record your height once", "growth", 1, s.heightReadings),
            Achievement("all_round", "All-rounder", "Log a habit, a workout, a movement and a night of sleep", "trophy", 4, categories)
        )
    }

    // -------------------------------------------------------------------------- milestones
    /** What is new since the last time the person saw the dashboard. */
    class Milestones(val levelUp: Int?, val unlocked: List<String>)

    /**
     * [seenLevel] and [seenIds] are what was recorded last time; null means this is the first look, so everything already
     * earned is recorded quietly without a celebration.
     */
    fun milestones(seenLevel: Int?, seenIds: Set<String>?, level: Int, unlockedNow: Set<String>): Milestones {
        if (seenLevel == null || seenIds == null) return Milestones(null, emptyList())
        val up = if (level > seenLevel) level else null
        val fresh = unlockedNow.filter { it !in seenIds }.sorted()
        return Milestones(up, fresh)
    }
}
