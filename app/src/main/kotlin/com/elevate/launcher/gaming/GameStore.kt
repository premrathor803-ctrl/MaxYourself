package com.elevate.launcher.gaming

import android.content.ContentValues
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.data.Store
import org.json.JSONObject

/**
 * One saved play session.
 * [kind] "tracked": Elevate saw the game being opened from it. "manual": typed in by hand.
 * [endKind] says how the end time was decided: "stopped" (you pressed Stop), "returned" (the time you came back to
 * Elevate, so only approximate), "replaced" (you opened another game), "entered" (typed in).
 */
class GameSession(
    val id: Long, val key: String, val name: String, val start: Long, val end: Long,
    val kind: String, val endKind: String, val note: String
) {
    val durationMs: Long get() = SessionMath.durationMs(start, end)
    val confirmedStart: Boolean get() = kind == GameStore.KIND_TRACKED
    val approximateEnd: Boolean get() = endKind == GameStore.END_RETURNED || endKind == GameStore.END_REPLACED
}

/** Per-game choices. Defaults: record sessions, nothing else on. */
class GameProfile(
    val favorite: Boolean = false,
    val record: Boolean = true,
    val dnd: Boolean = false,
    val remindMin: Int = 0,
    val autoMode: Boolean = false
)

/** Session history (SQLite) and per-game profiles (a small JSON setting, so reading one never touches the database). */
object GameStore {
    const val KIND_TRACKED = "tracked"
    const val KIND_MANUAL = "manual"
    const val END_STOPPED = "stopped"
    const val END_RETURNED = "returned"
    const val END_REPLACED = "replaced"
    const val END_ENTERED = "entered"

    private const val COLS = "id, key, name, start_ms, end_ms, kind, end_kind, note"

    // ------------------------------------------------------------ sessions (call from a background thread)
    fun add(key: String, name: String, start: Long, end: Long, kind: String, endKind: String, note: String = ""): Long {
        if (end <= start) return -1L
        val cv = ContentValues().apply {
            put("key", key); put("name", name); put("start_ms", start); put("end_ms", end)
            put("kind", kind); put("end_kind", endKind); put("note", note)
        }
        return Store.db.writableDatabase.insert("game_sessions", null, cv)
    }

    private fun read(c: android.database.Cursor) =
        GameSession(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getLong(4), c.getString(5), c.getString(6), c.getString(7))

    /** Sessions that overlap the time window [from, to). */
    fun overlapping(from: Long, to: Long): List<GameSession> {
        val out = ArrayList<GameSession>()
        val c = Store.db.readableDatabase.rawQuery(
            "SELECT $COLS FROM game_sessions WHERE end_ms > ? AND start_ms < ? ORDER BY start_ms DESC",
            arrayOf(from.toString(), to.toString())
        )
        c.use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }

    fun recent(limit: Int): List<GameSession> {
        val out = ArrayList<GameSession>()
        val c = Store.db.readableDatabase.rawQuery("SELECT $COLS FROM game_sessions ORDER BY start_ms DESC LIMIT $limit", null)
        c.use { while (it.moveToNext()) out.add(read(it)) }
        return out
    }

    fun count(): Int {
        val c = Store.db.readableDatabase.rawQuery("SELECT COUNT(*) FROM game_sessions", null)
        return c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** For every game with saved sessions: the most recent start time and the total time played. */
    fun totals(): Map<String, Pair<Long, Long>> {
        val out = HashMap<String, Pair<Long, Long>>()
        val c = Store.db.readableDatabase.rawQuery("SELECT key, MAX(start_ms), SUM(end_ms - start_ms) FROM game_sessions GROUP BY key", null)
        c.use { while (it.moveToNext()) out[it.getString(0)] = Pair(it.getLong(1), it.getLong(2)) }
        return out
    }

    fun delete(id: Long) { Store.db.writableDatabase.delete("game_sessions", "id=?", arrayOf(id.toString())) }
    fun deleteAll() { Store.db.writableDatabase.delete("game_sessions", null, null) }

    // ------------------------------------------------------------ profiles
    private const val KEY = "game_profiles"
    private var raw = ""
    private var cache: Map<String, GameProfile> = emptyMap()

    @Synchronized private fun all(): Map<String, GameProfile> {
        val r = Prefs.sp.getString(KEY, "") ?: ""
        if (r != raw) {
            raw = r
            val m = HashMap<String, GameProfile>()
            try {
                val o = JSONObject(if (r.isEmpty()) "{}" else r)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val p = o.optJSONObject(k) ?: continue
                    m[k] = GameProfile(
                        p.optInt("f", 0) == 1, p.optInt("r", 1) == 1, p.optInt("d", 0) == 1,
                        p.optInt("m", 0).coerceIn(0, 600), p.optInt("a", 0) == 1
                    )
                }
            } catch (e: Exception) {
                // a damaged value is ignored and defaults are used
            }
            cache = m
        }
        return cache
    }

    fun profile(key: String): GameProfile = all()[key] ?: GameProfile()

    @Synchronized fun setProfile(key: String, p: GameProfile) {
        val m = HashMap(all())
        val plain = !p.favorite && p.record && !p.dnd && p.remindMin == 0 && !p.autoMode
        if (plain) m.remove(key) else m[key] = p
        save(m)
    }

    @Synchronized fun forget(key: String) {
        val m = HashMap(all())
        if (m.remove(key) != null) save(m)
    }

    private fun save(m: Map<String, GameProfile>) {
        val o = JSONObject()
        for ((k, p) in m) {
            o.put(k, JSONObject().put("f", if (p.favorite) 1 else 0).put("r", if (p.record) 1 else 0)
                .put("d", if (p.dnd) 1 else 0).put("m", p.remindMin).put("a", if (p.autoMode) 1 else 0))
        }
        Prefs.putString(KEY, if (m.isEmpty()) "" else o.toString())
    }
}
