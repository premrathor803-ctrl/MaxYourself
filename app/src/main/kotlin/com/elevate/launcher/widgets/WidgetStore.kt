package com.elevate.launcher.widgets

import android.content.ContentValues
import com.elevate.launcher.data.Io
import com.elevate.launcher.data.Store

/** The widgets placed on the widgets page: which Android widget id, which provider, how tall, in what order. */
object WidgetStore {
    class Entry(val id: Long, val appWidgetId: Int, val provider: String, var heightDp: Int, var pos: Int)

    val entries = ArrayList<Entry>()
    private var nextId = 1L

    fun load() {
        entries.clear()
        val c = Store.db.readableDatabase.rawQuery(
            "SELECT id, appwidget_id, provider, height_dp, pos FROM widgets ORDER BY pos, id", null
        )
        c.use {
            while (it.moveToNext()) entries.add(Entry(it.getLong(0), it.getInt(1), it.getString(2), it.getInt(3), it.getInt(4)))
        }
        nextId = (entries.maxOfOrNull { it.id } ?: 0L) + 1
    }

    fun add(appWidgetId: Int, provider: String, heightDp: Int): Entry {
        val e = Entry(nextId++, appWidgetId, provider, heightDp, (entries.maxOfOrNull { it.pos } ?: -1) + 1)
        entries.add(e)
        save(e)
        return e
    }

    fun find(appWidgetId: Int): Entry? = entries.firstOrNull { it.appWidgetId == appWidgetId }

    fun remove(e: Entry) {
        entries.remove(e)
        val id = e.id
        Io.write { Store.db.writableDatabase.delete("widgets", "id=?", arrayOf(id.toString())) }
    }

    fun setHeight(e: Entry, dp: Int) {
        e.heightDp = dp.coerceIn(MIN_HEIGHT, MAX_HEIGHT)
        save(e)
    }

    /** Moves [e] one step up (dir = -1) or down (dir = +1). */
    fun move(e: Entry, dir: Int) {
        val i = entries.indexOf(e)
        val j = i + dir
        if (i < 0 || j < 0 || j >= entries.size) return
        entries.add(j, entries.removeAt(i))
        entries.forEachIndexed { k, x -> if (x.pos != k) { x.pos = k; save(x) } }
    }

    private fun save(e: Entry) {
        val cv = ContentValues().apply {
            put("id", e.id); put("appwidget_id", e.appWidgetId); put("provider", e.provider)
            put("height_dp", e.heightDp); put("pos", e.pos)
        }
        Io.write { Store.db.writableDatabase.insertWithOnConflict("widgets", null, cv, 5) } // 5 = CONFLICT_REPLACE
    }

    const val MIN_HEIGHT = 60
    const val MAX_HEIGHT = 600
}
