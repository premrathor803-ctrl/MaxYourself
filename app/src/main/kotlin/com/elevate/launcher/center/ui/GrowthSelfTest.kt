package com.elevate.launcher.center.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.elevate.launcher.center.data.Activity
import com.elevate.launcher.center.data.ActivityRepo
import com.elevate.launcher.center.data.CenterDb
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.GrowthMath
import com.elevate.launcher.center.data.MeasureRepo
import com.elevate.launcher.core.HeightUnits
import com.elevate.launcher.data.Io
import java.io.File

/**
 * Checks run on the emulator by the automated test (never in normal use): adding, editing and deleting height readings,
 * the activity log, the duplicate check, unit conversion, and the database upgrade that creates the activity table.
 * Results go to the log as "growth selftest" lines.
 */
object GrowthSelfTest {
    private fun log(m: String) { android.util.Log.i("ElevateCI", "growth selftest: $m") }

    fun run(ctx: Context) {
        Io.work {
            try {
                val today = Days.today()
                val cm = HeightUnits.feetInchesToCm(5.0, 7.0)
                log("5 ft 7 in = " + String.format(java.util.Locale.US, "%.2f", cm) + " cm, back to " + HeightUnits.formatFeetInches(cm))

                val before = MeasureRepo.list("height").size
                val a = MeasureRepo.add("height", 150.0, Days.add(today, -60), "selftest")
                val b = MeasureRepo.add("height", 150.6, Days.add(today, -30), "selftest")
                val afterAdd = MeasureRepo.list("height")
                val added = afterAdd.size - before == 2
                val m = afterAdd.first { it.id == b }
                m.value = 151.0
                MeasureRepo.update(m)
                val updated = MeasureRepo.list("height").first { it.id == b }.value == 151.0
                val dup = GrowthMath.isDuplicate(MeasureRepo.list("height").map { GrowthMath.Point(it.id, it.day, it.value) }, Days.add(today, -60), 150.0)
                MeasureRepo.delete(a)
                MeasureRepo.delete(b)
                val deleted = MeasureRepo.list("height").size == before
                log("height add=$added update=$updated duplicate_detected=$dup delete=$deleted")

                val actBefore = ActivityRepo.count()
                val x = ActivityRepo.add(Activity(0, today, ActivityRepo.KIND_EXERCISE, "selftest_key", "Selftest move", 0, ""))
                val y = ActivityRepo.add(Activity(0, today, ActivityRepo.KIND_ACTIVITY, "", "Selftest walk", 20, ""))
                val doneToday = ActivityRepo.doneOn("selftest_key", today)
                val counts = ActivityRepo.doneCounts(Days.add(today, -29))["selftest_key"]
                val walk = ActivityRepo.get(y)!!
                walk.minutes = 25
                ActivityRepo.update(walk)
                val edited = ActivityRepo.get(y)?.minutes == 25
                ActivityRepo.delete(x)
                ActivityRepo.delete(y)
                log("activity done_today=$doneToday count=$counts edit=$edited delete=${ActivityRepo.count() == actBefore}")

                // Upgrade from a database that has no activity table (version 3) keeps its readings and adds the table.
                val name = "center_migration_test.db"
                ctx.deleteDatabase(name)
                val path = ctx.getDatabasePath(name)
                path.parentFile?.mkdirs()
                val old = SQLiteDatabase.openOrCreateDatabase(path, null)
                old.execSQL("CREATE TABLE measures (id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, value REAL NOT NULL, day TEXT NOT NULL, note TEXT NOT NULL DEFAULT '')")
                old.execSQL("INSERT INTO measures (kind, value, day, note) VALUES ('height', 170.5, '2026-01-01', 'old')")
                old.version = 3
                old.close()
                val up = CenterDb(ctx, name)
                val db = up.writableDatabase
                var kept = false
                db.rawQuery("SELECT value FROM measures WHERE note='old'", null).use { kept = it.moveToFirst() && it.getDouble(0) == 170.5 }
                var hasTable = false
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='activity'", null).use { hasTable = it.moveToFirst() }
                db.execSQL("INSERT INTO activity (day, kind, label) VALUES ('2026-01-02', 'activity', 'x')")
                val version = db.version
                up.close()
                ctx.deleteDatabase(name)
                File(path.path + "-journal").delete()
                log("migration 3 to 4: version=$version old_row_kept=$kept activity_table=$hasTable")
            } catch (t: Throwable) {
                log("FAILED " + t)
            }
        }
    }
}
