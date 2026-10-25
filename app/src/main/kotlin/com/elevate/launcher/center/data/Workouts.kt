package com.elevate.launcher.center.data

import android.content.ContentValues
import org.json.JSONObject

class Session(
    val id: Long, val planKey: String, val planName: String, val category: String, val day: String,
    val started: Long, val ended: Long, val status: String, var difficulty: Int, var notes: String,
    val done: Int, val skipped: Int
) {
    val minutes: Int get() = Math.max(1, ((ended - started) / 60000L).toInt())
    val statusLabel: String get() = if (status == "completed") "Completed" else "Ended early"
}

class SessionEx(val key: String, val name: String, val state: String)

/** What is saved when someone leaves a workout part-way, so it can be resumed. */
class ResumeState(val planKey: String, val startedMs: Long, val index: Int, val states: LinkedHashMap<String, String>)

object WorkoutRepo {
    fun saveSession(
        planKey: String, planName: String, category: String, started: Long, ended: Long, status: String,
        exercises: List<SessionEx>
    ): Long {
        val done = exercises.count { it.state == "done" }
        val skipped = exercises.count { it.state == "skipped" }
        val cv = ContentValues().apply {
            put("plan_key", planKey); put("plan_name", planName); put("category", category)
            put("day", Days.of(ended)); put("started", started); put("ended", ended); put("status", status)
            put("done", done); put("skipped", skipped)
        }
        val id = Center.db.insert("sessions", null, cv)
        for (e in exercises) {
            val r = ContentValues().apply { put("session_id", id); put("ex_key", e.key); put("name", e.name); put("state", e.state) }
            Center.db.insert("session_ex", null, r)
        }
        return id
    }

    private fun read(c: android.database.Cursor) = Session(
        c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5), c.getLong(6),
        c.getString(7), c.getInt(8), c.getString(9), c.getInt(10), c.getInt(11)
    )

    private const val COLS = "id, plan_key, plan_name, category, day, started, ended, status, difficulty, notes, done, skipped"

    fun sessions(limit: Int = 200): List<Session> {
        val c = Center.db.rawQuery("SELECT $COLS FROM sessions ORDER BY ended DESC LIMIT $limit", null)
        val out = ArrayList<Session>()
        c.use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }

    fun session(id: Long): Session? {
        val c = Center.db.rawQuery("SELECT $COLS FROM sessions WHERE id=?", arrayOf(id.toString()))
        return c.use { if (it.moveToFirst()) read(it) else null }
    }

    fun exercisesOf(id: Long): List<SessionEx> {
        val c = Center.db.rawQuery("SELECT ex_key, name, state FROM session_ex WHERE session_id=?", arrayOf(id.toString()))
        val out = ArrayList<SessionEx>()
        c.use { while (it.moveToNext()) out.add(SessionEx(it.getString(0), it.getString(1), it.getString(2))) }
        return out
    }

    fun setFeedback(id: Long, difficulty: Int, notes: String) {
        val cv = ContentValues().apply { put("difficulty", difficulty); put("notes", notes) }
        Center.db.update("sessions", cv, "id=?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        Center.db.delete("session_ex", "session_id=?", arrayOf(id.toString()))
        Center.db.delete("sessions", "id=?", arrayOf(id.toString()))
    }

    /** Days with at least one fully completed session. */
    fun completedDays(from: String, to: String): Set<String> {
        val c = Center.db.rawQuery(
            "SELECT DISTINCT day FROM sessions WHERE status='completed' AND day>=? AND day<=?", arrayOf(from, to)
        )
        val out = HashSet<String>()
        c.use { while (it.moveToNext()) out.add(it.getString(0)) }
        return out
    }

    fun completedCount(from: String, to: String): Int {
        val c = Center.db.rawQuery("SELECT COUNT(*) FROM sessions WHERE status='completed' AND day>=? AND day<=?", arrayOf(from, to))
        return c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun completedToday(): Boolean = Days.today().let { completedDays(it, it).isNotEmpty() }

    /** Completed sessions per category, for the progress screen. */
    fun categoryCounts(from: String, to: String): List<Pair<String, Int>> {
        val c = Center.db.rawQuery(
            "SELECT category, COUNT(*) FROM sessions WHERE status='completed' AND day>=? AND day<=? GROUP BY category ORDER BY COUNT(*) DESC",
            arrayOf(from, to)
        )
        val out = ArrayList<Pair<String, Int>>()
        c.use { while (it.moveToNext()) out.add(it.getString(0) to it.getInt(1)) }
        return out
    }

    // ------------------------------------------------------------ resume
    fun saveResume(r: ResumeState) {
        val j = JSONObject()
        j.put("plan", r.planKey); j.put("started", r.startedMs); j.put("idx", r.index)
        val st = JSONObject()
        for ((k, v) in r.states) st.put(k, v)
        j.put("states", st)
        Center.kvSet("resume", j.toString())
    }

    fun loadResume(): ResumeState? {
        val s = Center.kvGet("resume") ?: return null
        return try {
            val j = JSONObject(s)
            val st = LinkedHashMap<String, String>()
            val so = j.getJSONObject("states")
            for (k in so.keys()) st[k] = so.getString(k)
            ResumeState(j.getString("plan"), j.getLong("started"), j.getInt("idx"), st)
        } catch (e: Exception) {
            Center.kvDel("resume")
            null
        }
    }

    fun clearResume() = Center.kvDel("resume")
}
