package com.elevate.launcher.backup

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.elevate.launcher.ElevateApp
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppMeta
import com.elevate.launcher.data.HomeItem
import com.elevate.launcher.data.HomeRepo
import com.elevate.launcher.data.Io
import com.elevate.launcher.data.Meta
import com.elevate.launcher.data.Store
import com.elevate.launcher.reminders.Reminders
import com.elevate.launcher.ui.IconLoader
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export and import of the launcher layout and (only if you choose) Center records, as one JSON file.
 *
 * What is never included: the access code for hidden apps, and any photos. The hidden-apps list, Center settings
 * and Center records are included only when selected. A file made by a newer version of the app is refused, and
 * invalid rows in a file are skipped instead of crashing.
 */
object Backup {
    const val FORMAT = "elevate-backup"
    const val VERSION = 1
    const val MAX_BYTES = 30 * 1024 * 1024

    private val CENTER_TABLES = listOf("habits", "habit_log", "sessions", "session_ex", "measures", "goals", "goal_entries", "sleep", "care_log", "kv")
    private val KEEP_PREFS = setOf("onboarded", "seeded")
    private val AREAS = setOf("page", "dock", "folder")
    private val KINDS = setOf("app", "folder", "center")

    class Options(val hidden: Boolean, val centerSettings: Boolean, val centerRecords: Boolean)

    class Preview(
        val version: Int, val created: Long, val appVersion: String,
        val hasLayout: Boolean, val hasHidden: Boolean, val hasCenterSettings: Boolean, val hasCenterRecords: Boolean,
        val items: Int, val habits: Int, val sessions: Int
    )

    class Report(val ok: Boolean, val message: String)

    // ------------------------------------------------------------------ create
    fun create(ctx: Context, o: Options): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("created", System.currentTimeMillis())
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        root.put("app_version", ver)

        val launcher = JSONObject()
        launcher.put("prefs", prefsToJson(Prefs.sp.all, KEEP_PREFS))
        val items = JSONArray()
        for (it in ArrayList(HomeRepo.items)) {
            items.put(JSONObject()
                .put("id", it.id).put("area", it.area).put("page", it.page).put("pos", it.pos)
                .put("kind", it.kind).put("pkg", it.pkg).put("cls", it.cls).put("label", it.label).put("parent", it.parent))
        }
        launcher.put("home_items", items)
        val metas = JSONArray()
        for ((key, m) in AppMeta.snapshot()) {
            val hidden = o.hidden && m.hidden
            // Entries that only said "hidden" are skipped when the hidden list is not included.
            if (!o.hidden && m.pinned == 0 && m.label.isEmpty() && m.icon.isEmpty() && m.launches == 0) continue
            metas.put(JSONObject()
                .put("key", key).put("hidden", hidden).put("pinned", m.pinned).put("label", m.label)
                .put("launches", m.launches).put("last_used", m.lastUsed).put("icon", m.icon))
        }
        launcher.put("app_meta", metas)
        launcher.put("hidden_included", o.hidden)
        root.put("launcher", launcher)

        if (o.centerSettings || o.centerRecords) {
            Center.init(ctx)
            val center = JSONObject()
            if (o.centerSettings) center.put("settings", prefsToJson(Center.sp.all, setOf("rem_last")))
            if (o.centerRecords) {
                val tables = JSONObject()
                for (t in CENTER_TABLES) tables.put(t, tableToJson(Center.db, t))
                center.put("tables", tables)
            }
            root.put("center", center)
        }
        return root.toString()
    }

    private fun prefsToJson(all: Map<String, *>, skip: Set<String>): JSONArray {
        val arr = JSONArray()
        for ((k, v) in all) {
            if (k in skip || v == null) continue
            val o = JSONObject().put("k", k)
            when (v) {
                is String -> o.put("t", "s").put("v", v)
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is Set<*> -> o.put("t", "set").put("v", JSONArray(v.filterIsInstance<String>()))
                else -> continue
            }
            arr.put(o)
        }
        return arr
    }

    private fun tableToJson(db: SQLiteDatabase, table: String): JSONObject {
        val out = JSONObject()
        val c = db.rawQuery("SELECT * FROM $table", null)
        c.use {
            val cols = JSONArray()
            for (i in 0 until it.columnCount) cols.put(it.getColumnName(i))
            out.put("cols", cols)
            val rows = JSONArray()
            while (it.moveToNext()) {
                val r = JSONArray()
                for (i in 0 until it.columnCount) {
                    when (it.getType(i)) {
                        Cursor.FIELD_TYPE_INTEGER -> r.put(it.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> r.put(it.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> r.put(it.getString(i))
                        else -> r.put(JSONObject.NULL)
                    }
                }
                rows.put(r)
            }
            out.put("rows", rows)
        }
        return out
    }

    // ------------------------------------------------------------------ read a file
    /** Parses a file and says what is in it, or why it cannot be used. */
    fun inspect(text: String): Pair<Preview?, String?> {
        val root = try { JSONObject(text) } catch (e: Exception) { return null to "This is not an Elevate backup file." }
        if (root.optString("format") != FORMAT) return null to "This is not an Elevate backup file."
        val v = root.optInt("version", 0)
        if (v < 1) return null to "This backup file is damaged."
        if (v > VERSION) return null to "This backup was made by a newer version of Elevate. Update the app, then try again."
        val launcher = root.optJSONObject("launcher")
        val center = root.optJSONObject("center")
        val tables = center?.optJSONObject("tables")
        fun rowCount(t: String): Int = tables?.optJSONObject(t)?.optJSONArray("rows")?.length() ?: 0
        return Preview(
            v, root.optLong("created", 0L), root.optString("app_version", ""),
            launcher != null, launcher?.optBoolean("hidden_included", false) == true,
            center?.has("settings") == true, tables != null,
            launcher?.optJSONArray("home_items")?.length() ?: 0, rowCount("habits"), rowCount("sessions")
        ) to null
    }

    // ------------------------------------------------------------------ restore
    class Parts(val layout: Boolean, val hidden: Boolean, val centerSettings: Boolean, val centerRecords: Boolean)

    /** Applies the chosen parts. Must run off the main thread. Calls [done] on the main thread. */
    fun restore(ctx: Context, text: String, parts: Parts, done: (Report) -> Unit) {
        val app = ctx.applicationContext
        val root = try { JSONObject(text) } catch (e: Exception) { Io.ui { done(Report(false, "The file could not be read.")) }; return }
        val msgs = ArrayList<String>()
        try {
            if (parts.centerRecords) {
                val tables = root.optJSONObject("center")?.optJSONObject("tables")
                if (tables != null) {
                    Center.init(app)
                    val n = restoreTables(Center.db, tables)
                    msgs.add("Center records restored ($n rows)")
                }
            }
            if (parts.centerSettings) {
                val arr = root.optJSONObject("center")?.optJSONArray("settings")
                if (arr != null) {
                    Center.init(app)
                    applyPrefs(Center.sp, arr, emptySet())
                    msgs.add("Center settings restored")
                }
            }
            var layoutPlan: LayoutPlan? = null
            if (parts.layout) {
                val launcher = root.optJSONObject("launcher")
                if (launcher != null) layoutPlan = planLayout(launcher, parts.hidden)
            }
            val plan = layoutPlan
            if (plan != null) {
                // launcher preferences first, so the layout loads against the right grid
                applyPrefs(Prefs.sp, plan.prefs, KEEP_PREFS)
                Prefs.onboarded = true
                Prefs.seeded = true
                val maxPage = plan.items.filter { it.area == "page" }.maxOfOrNull { it.page } ?: 0
                if (maxPage + 1 > Prefs.pages) Prefs.pages = maxPage + 1
                Io.write {
                    val db = Store.db.writableDatabase
                    db.beginTransaction()
                    try {
                        db.delete("home_items", null, null)
                        for (it in plan.items) {
                            val cv = ContentValues().apply {
                                put("id", it.id); put("area", it.area); put("page", it.page); put("pos", it.pos)
                                put("kind", it.kind); put("pkg", it.pkg); put("cls", it.cls); put("label", it.label); put("parent", it.parent)
                            }
                            db.insertWithOnConflict("home_items", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                        db.delete("app_meta", null, null)
                        for ((k, m) in plan.meta) {
                            val cv = ContentValues().apply {
                                put("key", k); put("hidden", if (m.hidden) 1 else 0); put("pinned", m.pinned); put("label", m.label)
                                put("launches", m.launches); put("last_used", m.lastUsed); put("icon", m.icon)
                            }
                            db.insertWithOnConflict("app_meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                        db.setTransactionSuccessful()
                    } catch (t: Throwable) {
                        android.util.Log.e("Elevate", "restoring the layout failed", t)
                        Io.ui { done(Report(false, "The home layout could not be restored. Your current layout was kept.")) }
                        return@write
                    } finally {
                        db.endTransaction()
                    }
                    Io.ui {
                        HomeRepo.load()
                        AppMeta.load()
                        HomeRepo.ensureGrid()
                        finish(app, msgs, plan.items.size, done)
                    }
                }
                return
            }
            Io.ui { finish(app, msgs, -1, done) }
        } catch (t: Throwable) {
            android.util.Log.e("Elevate", "restore failed", t)
            Io.ui { done(Report(false, "Restore stopped because of a problem in the file. Nothing more was changed.")) }
        }
    }

    private fun finish(app: Context, msgs: ArrayList<String>, items: Int, done: (Report) -> Unit) {
        try {
            IconLoader.clear()
            ElevateApp.applyNightMode()
            Reminders.afterRestore(app)
        } catch (t: Throwable) {
            android.util.Log.w("Elevate", "post-restore refresh failed", t)
        }
        if (items >= 0) msgs.add(0, "Home layout and settings restored ($items items)")
        done(Report(true, if (msgs.isEmpty()) "Nothing to restore." else msgs.joinToString("\n")))
    }

    private class LayoutPlan(val prefs: JSONArray, val items: List<HomeItem>, val meta: Map<String, Meta>)

    private fun planLayout(launcher: JSONObject, restoreHidden: Boolean): LayoutPlan {
        val prefs = launcher.optJSONArray("prefs") ?: JSONArray()

        // ---- home items, validated
        val raw = launcher.optJSONArray("home_items") ?: JSONArray()
        val items = ArrayList<HomeItem>()
        val seen = HashSet<Long>()
        for (i in 0 until raw.length()) {
            val o = raw.optJSONObject(i) ?: continue
            val id = o.optLong("id", -1L)
            val area = o.optString("area")
            val kind = o.optString("kind")
            if (id <= 0 || id in seen || area !in AREAS || kind !in KINDS) continue
            val page = o.optInt("page", 0)
            val pos = o.optInt("pos", 0)
            if (page !in 0 until HomeRepo.MAX_PAGES || pos !in 0..199) continue
            val pkg = o.optString("pkg").take(300)
            val cls = o.optString("cls").take(300)
            if (kind == "app" && (pkg.isEmpty() || cls.isEmpty())) continue
            seen.add(id)
            items.add(HomeItem(id, area, page, pos, kind, pkg, cls, o.optString("label").take(100), o.optLong("parent", 0L)))
        }
        // apps from another phone that are not installed here are left out (only once the app list is known)
        if (AppCatalog.loaded) items.removeAll { it.kind == "app" && AppCatalog.get(it.key) == null }
        // folder members need a folder to live in
        val folderIds = items.filter { it.kind == "folder" }.map { it.id }.toHashSet()
        items.removeAll { it.area == "folder" && it.parent !in folderIds }

        // ---- per-app data
        val meta = HashMap<String, Meta>()
        val rawMeta = launcher.optJSONArray("app_meta") ?: JSONArray()
        for (i in 0 until rawMeta.length()) {
            val o = rawMeta.optJSONObject(i) ?: continue
            val key = o.optString("key")
            if (key.isEmpty() || key.length > 600) continue
            meta[key] = Meta(
                restoreHidden && o.optBoolean("hidden", false), o.optInt("pinned", 0).coerceAtLeast(0),
                o.optString("label").take(100), o.optInt("launches", 0).coerceAtLeast(0), o.optLong("last_used", 0L),
                o.optString("icon").take(300)
            )
        }
        // A hidden app should not stay on the home screen.
        if (restoreHidden) {
            val hid = meta.filter { it.value.hidden }.keys
            items.removeAll { it.kind == "app" && it.key in hid }
        } else {
            // Keep whatever is hidden on this phone right now.
            for (k in AppMeta.hiddenKeys()) {
                val m = meta.getOrPut(k) { Meta() }
                m.hidden = true
            }
        }
        return LayoutPlan(prefs, items, meta)
    }

    private fun applyPrefs(sp: SharedPreferences, arr: JSONArray, keep: Set<String>) {
        val e = sp.edit()
        val kept = HashMap<String, Any?>()
        for (k in keep) if (sp.contains(k)) kept[k] = sp.all[k]
        e.clear()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val k = o.optString("k")
            if (k.isEmpty() || k in keep) continue
            when (o.optString("t")) {
                "s" -> e.putString(k, o.optString("v"))
                "b" -> e.putBoolean(k, o.optBoolean("v"))
                "i" -> e.putInt(k, o.optInt("v"))
                "l" -> e.putLong(k, o.optLong("v"))
                "f" -> e.putFloat(k, o.optDouble("v").toFloat())
                "set" -> {
                    val a = o.optJSONArray("v") ?: continue
                    val s = HashSet<String>()
                    for (j in 0 until a.length()) s.add(a.optString(j))
                    e.putStringSet(k, s)
                }
            }
        }
        for ((k, v) in kept) if (v is Boolean) e.putBoolean(k, v)
        e.commit()
    }

    private fun restoreTables(db: SQLiteDatabase, tables: JSONObject): Int {
        var count = 0
        db.beginTransaction()
        try {
            for (t in CENTER_TABLES) {
                val obj = tables.optJSONObject(t) ?: continue
                val cols = obj.optJSONArray("cols") ?: continue
                val rows = obj.optJSONArray("rows") ?: continue
                val valid = tableColumns(db, t)
                db.delete(t, null, null)
                for (i in 0 until rows.length()) {
                    val r = rows.optJSONArray(i) ?: continue
                    val cv = ContentValues()
                    for (j in 0 until cols.length()) {
                        val name = cols.optString(j)
                        if (name !in valid || j >= r.length()) continue
                        when (val v = r.opt(j)) {
                            null, JSONObject.NULL -> {}
                            is Int -> cv.put(name, v.toLong())
                            is Long -> cv.put(name, v)
                            is Double -> cv.put(name, v)
                            is Boolean -> cv.put(name, if (v) 1L else 0L)
                            else -> cv.put(name, v.toString())
                        }
                    }
                    if (cv.size() == 0) continue
                    if (db.insertWithOnConflict(t, null, cv, SQLiteDatabase.CONFLICT_REPLACE) != -1L) count++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    private fun tableColumns(db: SQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        val c = db.rawQuery("PRAGMA table_info($table)", null)
        c.use { while (it.moveToNext()) out.add(it.getString(1)) }
        return out
    }
}
