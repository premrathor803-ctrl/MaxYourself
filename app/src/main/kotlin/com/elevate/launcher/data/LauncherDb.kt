package com.elevate.launcher.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Home layout + per-app metadata. The Center (habits, workouts...) has its own database. */
class LauncherDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "launcher.db", null, 1) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No upgrades yet. Future versions add columns/tables here without dropping user data.
    }
}

object Store {
    lateinit var db: LauncherDb
        private set

    fun init(ctx: Context) {
        db = LauncherDb(ctx)
        AppMeta.load()
        HomeRepo.load()
    }
}
