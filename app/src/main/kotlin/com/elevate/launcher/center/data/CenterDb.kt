package com.elevate.launcher.center.data

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The Center's own database. It is kept apart from the launcher layout database, so resetting the
 * home screen can never erase habits, workouts or measurements (and the reverse).
 */
class CenterDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "center.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE habits (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, descr TEXT NOT NULL DEFAULT '', " +
                "category TEXT NOT NULL DEFAULT 'General', slot TEXT NOT NULL DEFAULT 'anytime', freq TEXT NOT NULL DEFAULT 'daily', " +
                "days INTEGER NOT NULL DEFAULT 127, start_day TEXT NOT NULL, target TEXT NOT NULL DEFAULT '', " +
                "method INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL DEFAULT 0, " +
                "remind TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE habit_log (habit_id INTEGER NOT NULL, day TEXT NOT NULL, state INTEGER NOT NULL, " +
                "note TEXT NOT NULL DEFAULT '', PRIMARY KEY (habit_id, day))"
        )
        db.execSQL(
            "CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, plan_key TEXT NOT NULL, plan_name TEXT NOT NULL, " +
                "category TEXT NOT NULL DEFAULT '', day TEXT NOT NULL, started INTEGER NOT NULL, ended INTEGER NOT NULL, " +
                "status TEXT NOT NULL, difficulty INTEGER NOT NULL DEFAULT 0, notes TEXT NOT NULL DEFAULT '', " +
                "done INTEGER NOT NULL DEFAULT 0, skipped INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE TABLE session_ex (session_id INTEGER NOT NULL, ex_key TEXT NOT NULL, name TEXT NOT NULL, state TEXT NOT NULL)")
        db.execSQL("CREATE TABLE measures (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, value REAL NOT NULL, day TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')")
        db.execSQL(
            "CREATE TABLE goals (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, descr TEXT NOT NULL DEFAULT '', " +
                "kind TEXT NOT NULL, habit_id INTEGER NOT NULL DEFAULT 0, target REAL NOT NULL DEFAULT 0, unit TEXT NOT NULL DEFAULT '', " +
                "start_day TEXT NOT NULL, end_day TEXT NOT NULL, review TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, " +
                "created INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE TABLE goal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT, goal_id INTEGER NOT NULL, day TEXT NOT NULL, value REAL NOT NULL, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE sleep (day TEXT PRIMARY KEY, bed TEXT NOT NULL DEFAULT '', wake TEXT NOT NULL DEFAULT '', energy INTEGER NOT NULL DEFAULT 0, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE care_log (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, product TEXT NOT NULL, reaction TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        createPhotos(db)
    }

    private fun createPhotos(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS photos (id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, kind TEXT NOT NULL, " +
                "note TEXT NOT NULL DEFAULT '', file TEXT NOT NULL, created INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Existing records are never dropped; upgrades only add columns or tables.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE habits ADD COLUMN remind TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) createPhotos(db)
    }
}

/** Entry point to Center storage and Center-only settings. */
object Center {
    private var dbInst: CenterDb? = null
    private var spInst: SharedPreferences? = null
    private var appCtx: Context? = null

    fun init(ctx: Context) {
        if (dbInst == null) {
            val app = ctx.applicationContext
            appCtx = app
            dbInst = CenterDb(app)
            spInst = app.getSharedPreferences("center_prefs", Context.MODE_PRIVATE)
        }
    }

    val db: SQLiteDatabase get() = dbInst!!.writableDatabase
    val sp: SharedPreferences get() = spInst!!

    // ---- settings
    fun moduleOn(name: String, default: Boolean = true) = sp.getBoolean("mod_$name", default)
    fun setModule(name: String, on: Boolean) = sp.edit().putBoolean("mod_$name", on).apply()
    var level: String
        get() = sp.getString("level", "beginner") ?: "beginner"
        set(v) = sp.edit().putString("level", v).apply()
    /** Bit 0 = Monday ... bit 6 = Sunday. */
    var trainingDays: Int
        get() = sp.getInt("training_days", 0b0010101)
        set(v) = sp.edit().putInt("training_days", v).apply()
    var useTimers: Boolean
        get() = sp.getBoolean("use_timers", true)
        set(v) = sp.edit().putBoolean("use_timers", v).apply()
    var audioCues: Boolean
        get() = sp.getBoolean("audio_cues", false)
        set(v) = sp.edit().putBoolean("audio_cues", v).apply()
    var heightUnit: String
        get() = sp.getString("height_unit", "cm") ?: "cm"
        set(v) = sp.edit().putString("height_unit", v).apply()

    // ---- small key/value store (for workout resume state etc.)
    fun kvGet(k: String): String? {
        val c = db.rawQuery("SELECT v FROM kv WHERE k=?", arrayOf(k))
        return c.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    fun kvSet(k: String, v: String) {
        val cv = android.content.ContentValues().apply { put("k", k); put("v", v) }
        db.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun kvDel(k: String) { db.delete("kv", "k=?", arrayOf(k)) }

    // ---- deletion (each set of records can be removed on its own)
    fun wipe(what: String) {
        when (what) {
            "habits" -> { db.delete("habit_log", null, null); db.delete("habits", null, null) }
            "habit_history" -> db.delete("habit_log", null, null)
            "workouts" -> { db.delete("session_ex", null, null); db.delete("sessions", null, null); kvDel("resume") }
            "measures" -> db.delete("measures", null, null)
            "goals" -> { db.delete("goal_entries", null, null); db.delete("goals", null, null) }
            "sleep" -> db.delete("sleep", null, null)
            "care" -> db.delete("care_log", null, null)
            "photos" -> appCtx?.let { PhotoRepo.deleteAll(it) }
            "all" -> {
                appCtx?.let { PhotoRepo.deleteAll(it) }
                for (t in listOf("habit_log", "habits", "session_ex", "sessions", "measures", "goal_entries", "goals", "sleep", "care_log", "kv")) db.delete(t, null, null)
                sp.edit().clear().apply()
            }
        }
    }
}
