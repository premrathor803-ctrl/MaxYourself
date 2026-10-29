package com.elevate.launcher.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Home layout + per-app metadata. The Center (habits, workouts...) has its own database. */
class LauncherDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "launcher.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE home_items (" +
                "id INTEGER PRIMARY KEY, area TEXT NOT NULL, page INTEGER NOT NULL DEFAULT 0, " +
                "pos INTEGER NOT NULL DEFAULT 0, kind TEXT NOT NULL, pkg TEXT NOT NULL DEFAULT '', " +
                "cls TEXT NOT NULL DEFAULT '', label TEXT NOT NULL DEFAULT '', parent INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE app_meta (" +
                "key TEXT PRIMARY KEY, hidden INTEGER NOT NULL DEFAULT 0, pinned INTEGER NOT NULL DEFAULT 0, " +
                "label TEXT NOT NULL DEFAULT '', launches INTEGER NOT NULL DEFAULT 0, " +
                "last_used INTEGER NOT NULL DEFAULT 0, icon TEXT NOT NULL DEFAULT '')"
        )
        createWidgets(db)
        createGaming(db)
    }

    private fun createGaming(db: SQLiteDatabase) {
        // kind: tracked (started from Elevate) or manual (typed in). end_kind says how the end time was decided.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS game_sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT NOT NULL, name TEXT NOT NULL, " +
                "start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL, kind TEXT NOT NULL, " +
                "end_kind TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_game_sessions_start ON game_sessions(start_ms)")
    }

    private fun createWidgets(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS widgets (" +
                "id INTEGER PRIMARY KEY, appwidget_id INTEGER NOT NULL, provider TEXT NOT NULL, " +
                "height_dp INTEGER NOT NULL DEFAULT 160, pos INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Upgrades only add columns or tables; user data is never dropped.
        if (oldVersion < 2) createWidgets(db)
        if (oldVersion < 3) createGaming(db)
    }
}

object Store {
    lateinit var db: LauncherDb
        private set

    fun init(ctx: Context) {
        db = LauncherDb(ctx)
        AppMeta.load()
        HomeRepo.load()
        com.elevate.launcher.widgets.WidgetStore.load()
    }
}
