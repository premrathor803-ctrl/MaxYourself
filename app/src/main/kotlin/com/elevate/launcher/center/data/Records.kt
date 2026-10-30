package com.elevate.launcher.center.data

import android.content.ContentValues

// ------------------------------------------------------------------ height / measurements
class Measure(val id: Long, val kind: String, var value: Double, var day: String, var note: String)

object MeasureRepo {
    fun list(kind: String): List<Measure> {
        val c = Center.db.rawQuery("SELECT id, kind, value, day, note FROM measures WHERE kind=? ORDER BY day, id", arrayOf(kind))
        val out = ArrayList<Measure>()
        c.use { while (it.moveToNext()) out.add(Measure(it.getLong(0), it.getString(1), it.getDouble(2), it.getString(3), it.getString(4))) }
        return out
    }

    fun add(kind: String, value: Double, day: String, note: String): Long {
        val cv = ContentValues().apply { put("kind", kind); put("value", value); put("day", day); put("note", note) }
        return Center.db.insert("measures", null, cv)
    }

    fun update(m: Measure) {
        val cv = ContentValues().apply { put("value", m.value); put("day", m.day); put("note", m.note) }
        Center.db.update("measures", cv, "id=?", arrayOf(m.id.toString()))
    }

    fun delete(id: Long) { Center.db.delete("measures", "id=?", arrayOf(id.toString())) }
}

// ------------------------------------------------------------------ goals
class Goal(
    val id: Long, var title: String, var descr: String, var kind: String, var habitId: Long, var target: Double,
    var unit: String, var startDay: String, var endDay: String, var review: String, var done: Boolean
)

class GoalEntry(val id: Long, val goalId: Long, val day: String, val value: Double, val note: String)

object GoalRepo {
    const val KIND_HABIT = "habit"
    const val KIND_WORKOUTS = "workouts"
    const val KIND_MANUAL = "manual"

    private const val COLS = "id, title, descr, kind, habit_id, target, unit, start_day, end_day, review, done"

    private fun read(c: android.database.Cursor) = Goal(
        c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getDouble(5),
        c.getString(6), c.getString(7), c.getString(8), c.getString(9), c.getInt(10) == 1
    )

    fun all(): List<Goal> {
        val c = Center.db.rawQuery("SELECT $COLS FROM goals ORDER BY done, end_day", null)
        val out = ArrayList<Goal>()
        c.use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }

    fun get(id: Long): Goal? {
        val c = Center.db.rawQuery("SELECT $COLS FROM goals WHERE id=?", arrayOf(id.toString()))
        return c.use { if (it.moveToFirst()) read(it) else null }
    }

    private fun values(g: Goal) = ContentValues().apply {
        put("title", g.title); put("descr", g.descr); put("kind", g.kind); put("habit_id", g.habitId)
        put("target", g.target); put("unit", g.unit); put("start_day", g.startDay); put("end_day", g.endDay)
        put("review", g.review); put("done", if (g.done) 1 else 0)
    }

    fun insert(g: Goal): Long {
        val cv = values(g)
        cv.put("created", System.currentTimeMillis())
        return Center.db.insert("goals", null, cv)
    }

    fun update(g: Goal) { Center.db.update("goals", values(g), "id=?", arrayOf(g.id.toString())) }

    fun delete(id: Long) {
        Center.db.delete("goal_entries", "goal_id=?", arrayOf(id.toString()))
        Center.db.delete("goals", "id=?", arrayOf(id.toString()))
    }

    fun entries(goalId: Long): List<GoalEntry> {
        val c = Center.db.rawQuery("SELECT id, goal_id, day, value, note FROM goal_entries WHERE goal_id=? ORDER BY day DESC, id DESC", arrayOf(goalId.toString()))
        val out = ArrayList<GoalEntry>()
        c.use { while (it.moveToNext()) out.add(GoalEntry(it.getLong(0), it.getLong(1), it.getString(2), it.getDouble(3), it.getString(4))) }
        return out
    }

    fun addEntry(goalId: Long, day: String, value: Double, note: String) {
        val cv = ContentValues().apply { put("goal_id", goalId); put("day", day); put("value", value); put("note", note) }
        Center.db.insert("goal_entries", null, cv)
    }

    fun deleteEntry(id: Long) { Center.db.delete("goal_entries", "id=?", arrayOf(id.toString())) }

    /** Progress computed from real stored data. Returns current to target. */
    fun progress(g: Goal): Pair<Double, Double> {
        val today = Days.today()
        return when (g.kind) {
            KIND_HABIT -> {
                val h = HabitRepo.get(g.habitId)
                val cur = if (h == null) 0 else HabitRepo.completedCount(h, g.startDay, g.endDay)
                cur.toDouble() to g.target
            }
            KIND_WORKOUTS -> WorkoutRepo.completedCount(g.startDay, if (g.endDay > today) today else g.endDay).toDouble() to g.target
            else -> entries(g.id).sumOf { it.value } to g.target
        }
    }
}

// ------------------------------------------------------------------ sleep
class SleepEntry(val day: String, var bed: String, var wake: String, var energy: Int, var note: String) {
    /** Hours between bedtime and wake time, or null if either is missing. */
    val hours: Double?
        get() {
            val b = minutes(bed) ?: return null
            val w = minutes(wake) ?: return null
            var d = w - b
            if (d <= 0) d += 24 * 60
            return d / 60.0
        }

    private fun minutes(t: String): Int? {
        val p = t.split(":")
        if (p.size != 2) return null
        val h = p[0].toIntOrNull() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        return h * 60 + m
    }
}

object SleepRepo {
    fun recent(limit: Int): List<SleepEntry> {
        val c = Center.db.rawQuery("SELECT day, bed, wake, energy, note FROM sleep ORDER BY day DESC LIMIT $limit", null)
        val out = ArrayList<SleepEntry>()
        c.use { while (it.moveToNext()) out.add(SleepEntry(it.getString(0), it.getString(1), it.getString(2), it.getInt(3), it.getString(4))) }
        return out
    }

    fun save(e: SleepEntry) {
        val cv = ContentValues().apply { put("day", e.day); put("bed", e.bed); put("wake", e.wake); put("energy", e.energy); put("note", e.note) }
        Center.db.insertWithOnConflict("sleep", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun delete(day: String) { Center.db.delete("sleep", "day=?", arrayOf(day)) }
}

// ------------------------------------------------------------------ personal-care product reactions
class CareEntry(val id: Long, val day: String, val product: String, val reaction: String, val note: String)

object CareRepo {
    val reactions = listOf("none" to "No reaction", "mild" to "Mild (slight redness or dryness)", "irritated" to "Irritated (stinging, rash, swelling)")

    fun list(): List<CareEntry> {
        val c = Center.db.rawQuery("SELECT id, day, product, reaction, note FROM care_log ORDER BY day DESC, id DESC", null)
        val out = ArrayList<CareEntry>()
        c.use { while (it.moveToNext()) out.add(CareEntry(it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.getString(4))) }
        return out
    }

    fun add(day: String, product: String, reaction: String, note: String) {
        val cv = ContentValues().apply { put("day", day); put("product", product); put("reaction", reaction); put("note", note) }
        Center.db.insert("care_log", null, cv)
    }

    fun delete(id: Long) { Center.db.delete("care_log", "id=?", arrayOf(id.toString())) }
}


// ------------------------------------------------------------------ movement done and physical activity (entered by hand)
class Activity(val id: Long, var day: String, var kind: String, var ref: String, var label: String, var minutes: Int, var note: String)

object ActivityRepo {
    /** An item from the posture and mobility library that the person marked as done. */
    const val KIND_EXERCISE = "exercise"
    /** Physical activity the person typed in (a walk, a match, a swim). */
    const val KIND_ACTIVITY = "activity"

    private const val COLS = "id, day, kind, ref, label, minutes, note"

    private fun read(c: android.database.Cursor) =
        Activity(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5), c.getString(6))

    fun between(from: String, to: String): List<Activity> {
        val c = Center.db.rawQuery("SELECT $COLS FROM activity WHERE day >= ? AND day <= ? ORDER BY day DESC, id DESC", arrayOf(from, to))
        val out = ArrayList<Activity>()
        c.use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }

    fun recent(limit: Int): List<Activity> {
        val c = Center.db.rawQuery("SELECT $COLS FROM activity ORDER BY day DESC, id DESC LIMIT $limit", null)
        val out = ArrayList<Activity>()
        c.use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }

    fun get(id: Long): Activity? {
        val c = Center.db.rawQuery("SELECT $COLS FROM activity WHERE id=?", arrayOf(id.toString()))
        return c.use { if (it.moveToFirst()) read(it) else null }
    }

    fun count(): Int {
        val c = Center.db.rawQuery("SELECT COUNT(*) FROM activity", null)
        return c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** Is this library item already marked done on that day? Used so a repeated tap does not add it twice. */
    fun doneOn(ref: String, day: String): Boolean {
        val c = Center.db.rawQuery("SELECT COUNT(*) FROM activity WHERE kind=? AND ref=? AND day=?", arrayOf(KIND_EXERCISE, ref, day))
        return c.use { it.moveToFirst() && it.getInt(0) > 0 }
    }

    /** How many days each library item was marked done on or after [from]. */
    fun doneCounts(from: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        val c = Center.db.rawQuery("SELECT ref, COUNT(DISTINCT day) FROM activity WHERE kind=? AND day >= ? GROUP BY ref", arrayOf(KIND_EXERCISE, from))
        c.use { while (it.moveToNext()) out[it.getString(0)] = it.getInt(1) }
        return out
    }

    private fun values(a: Activity) = ContentValues().apply {
        put("day", a.day); put("kind", a.kind); put("ref", a.ref); put("label", a.label); put("minutes", a.minutes); put("note", a.note)
    }

    fun add(a: Activity): Long = Center.db.insert("activity", null, values(a))
    fun update(a: Activity) { Center.db.update("activity", values(a), "id=?", arrayOf(a.id.toString())) }
    fun delete(id: Long) { Center.db.delete("activity", "id=?", arrayOf(id.toString())) }
}
