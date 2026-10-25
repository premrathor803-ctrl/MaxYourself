package com.elevate.launcher.center.data

import android.content.ContentValues

class Habit(
    val id: Long,
    var name: String,
    var descr: String = "",
    var category: String = "General",
    var slot: String = "anytime",       // anytime | morning | evening
    var freq: String = "daily",         // daily | weekdays | custom
    var days: Int = 127,                // bit 0 = Monday ... bit 6 = Sunday (used when freq == custom)
    var startDay: String = Days.today(),
    var target: String = "",
    var method: Int = 0,                // 0 = I tick it off, 1 = counts automatically when I finish a workout
    var archived: Boolean = false
)

/** The four states the spec asks to keep distinct, plus "pending" for today's not-yet-done items. */
enum class HState { COMPLETED, SKIPPED, NOT_COMPLETED, NOT_SCHEDULED, PENDING }

class HabitStats(val currentRun: Int, val bestRun: Int, val done30: Int, val scheduled30: Int, val totalDone: Int)

object HabitRepo {
    const val LOG_DONE = 1
    const val LOG_SKIPPED = 2

    val categories = listOf("General", "Fitness", "Skincare", "Grooming", "Posture", "Sleep", "Learning", "Health")
    val slots = listOf("anytime" to "Any time", "morning" to "Morning", "evening" to "Evening")

    private fun read(c: android.database.Cursor) = Habit(
        c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5),
        c.getInt(6), c.getString(7), c.getString(8), c.getInt(9), c.getInt(10) == 1
    )

    private const val COLS = "id, name, descr, category, slot, freq, days, start_day, target, method, archived"

    fun all(includeArchived: Boolean = false): List<Habit> {
        val c = Center.db.rawQuery(
            "SELECT $COLS FROM habits ${if (includeArchived) "" else "WHERE archived=0"} ORDER BY id", null
        )
        val out = ArrayList<Habit>()
        c.use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }

    fun get(id: Long): Habit? {
        val c = Center.db.rawQuery("SELECT $COLS FROM habits WHERE id=?", arrayOf(id.toString()))
        return c.use { if (it.moveToFirst()) read(it) else null }
    }

    private fun values(h: Habit) = ContentValues().apply {
        put("name", h.name); put("descr", h.descr); put("category", h.category); put("slot", h.slot)
        put("freq", h.freq); put("days", h.days); put("start_day", h.startDay); put("target", h.target)
        put("method", h.method); put("archived", if (h.archived) 1 else 0)
    }

    fun insert(h: Habit): Long {
        val cv = values(h)
        cv.put("created", System.currentTimeMillis())
        return Center.db.insert("habits", null, cv)
    }

    fun update(h: Habit) { Center.db.update("habits", values(h), "id=?", arrayOf(h.id.toString())) }

    fun delete(id: Long) {
        Center.db.delete("habit_log", "habit_id=?", arrayOf(id.toString()))
        Center.db.delete("habits", "id=?", arrayOf(id.toString()))
    }

    fun hasName(name: String): Boolean {
        val c = Center.db.rawQuery("SELECT 1 FROM habits WHERE name=? AND archived=0", arrayOf(name))
        return c.use { it.moveToFirst() }
    }

    // ------------------------------------------------------------ logging
    /** state: 0 clears the entry, 1 completed, 2 skipped. */
    fun setState(habitId: Long, day: String, state: Int) {
        if (state == 0) {
            Center.db.delete("habit_log", "habit_id=? AND day=?", arrayOf(habitId.toString(), day))
        } else {
            val cv = ContentValues().apply { put("habit_id", habitId); put("day", day); put("state", state) }
            Center.db.insertWithOnConflict("habit_log", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun logs(habitId: Long, from: String, to: String): Map<String, Int> {
        val c = Center.db.rawQuery(
            "SELECT day, state FROM habit_log WHERE habit_id=? AND day>=? AND day<=?",
            arrayOf(habitId.toString(), from, to)
        )
        val out = HashMap<String, Int>()
        c.use { while (it.moveToNext()) out[it.getString(0)] = it.getInt(1) }
        return out
    }

    // ------------------------------------------------------------ schedule + state
    fun scheduled(h: Habit, day: String): Boolean {
        if (day < h.startDay) return false
        return when (h.freq) {
            "weekdays" -> Days.dow(day) < 5
            "custom" -> (h.days shr Days.dow(day)) and 1 == 1
            else -> true
        }
    }

    fun stateOf(h: Habit, day: String, logs: Map<String, Int>, today: String, workoutDays: Set<String>): HState {
        if (!scheduled(h, day)) return HState.NOT_SCHEDULED
        when (logs[day]) {
            LOG_DONE -> return HState.COMPLETED
            LOG_SKIPPED -> return HState.SKIPPED
        }
        if (h.method == 1 && day in workoutDays) return HState.COMPLETED
        return if (day < today) HState.NOT_COMPLETED else HState.PENDING
    }

    fun stateToday(h: Habit): HState {
        val t = Days.today()
        return stateOf(h, t, logs(h.id, t, t), t, WorkoutRepo.completedDays(t, t))
    }

    fun dueToday(): List<Habit> {
        val t = Days.today()
        return all().filter { scheduled(it, t) }
    }

    /**
     * Run = consecutive scheduled days completed. Skipped days and days the habit was not scheduled neither
     * count nor break the run. A scheduled day that was not completed ends the run, but history and best run stay.
     */
    fun stats(h: Habit): HabitStats {
        val today = Days.today()
        val from = maxOf(h.startDay, Days.add(today, -400))
        val lg = logs(h.id, from, today)
        val wd = if (h.method == 1) WorkoutRepo.completedDays(from, today) else emptySet()
        var run = 0; var best = 0; var total = 0
        var done30 = 0; var sched30 = 0
        val cut30 = Days.add(today, -29)
        for (d in Days.range(from, today)) {
            val s = stateOf(h, d, lg, today, wd)
            if (s == HState.COMPLETED) { run++; total++; if (run > best) best = run }
            else if (s == HState.NOT_COMPLETED) run = 0
            if (d >= cut30 && s != HState.NOT_SCHEDULED && s != HState.SKIPPED && s != HState.PENDING) {
                sched30++
                if (s == HState.COMPLETED) done30++
            } else if (d >= cut30 && s == HState.PENDING && d == today) {
                // today is only counted once it is done
            }
        }
        return HabitStats(run, best, done30, sched30, total)
    }

    /** Number of completed days for [h] between two days (inclusive). */
    fun completedCount(h: Habit, from: String, to: String): Int {
        val today = Days.today()
        val end = if (to > today) today else to
        if (from > end) return 0
        val lg = logs(h.id, from, end)
        val wd = if (h.method == 1) WorkoutRepo.completedDays(from, end) else emptySet()
        return Days.range(from, end).count { stateOf(h, it, lg, today, wd) == HState.COMPLETED }
    }

    /** Completed-habit counts per day over a range, for the progress charts. */
    fun completedPerDay(from: String, to: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        val c = Center.db.rawQuery(
            "SELECT day, COUNT(*) FROM habit_log WHERE state=1 AND day>=? AND day<=? GROUP BY day", arrayOf(from, to)
        )
        c.use { while (it.moveToNext()) out[it.getString(0)] = it.getInt(1) }
        return out
    }

    fun describeSchedule(h: Habit): String = when (h.freq) {
        "weekdays" -> "Weekdays"
        "custom" -> (0..6).filter { (h.days shr it) and 1 == 1 }.joinToString(", ") { Days.dowNames[it] }.ifEmpty { "No days selected" }
        else -> "Every day"
    }
}
