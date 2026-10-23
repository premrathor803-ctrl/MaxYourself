package com.elevate.launcher.data

import android.content.ContentValues

/** Per-app flags kept by the launcher: hidden, pinned, renamed, launch history. */
class Meta(
    var hidden: Boolean = false,
    var pinned: Int = 0,           // 0 = not pinned, otherwise pin order
    var label: String = "",
    var launches: Int = 0,
    var lastUsed: Long = 0L,
    var icon: String = ""
)

object AppMeta {
    private val map = java.util.concurrent.ConcurrentHashMap<String, Meta>()

    fun load() {
        map.clear()
        val c = Store.db.readableDatabase.rawQuery(
            "SELECT key, hidden, pinned, label, launches, last_used, icon FROM app_meta", null
        )
        c.use {
            while (it.moveToNext()) {
                map[it.getString(0)] = Meta(
                    it.getInt(1) == 1, it.getInt(2), it.getString(3), it.getInt(4), it.getLong(5), it.getString(6)
                )
            }
        }
    }

    fun get(key: String): Meta? = map[key]
    fun isHidden(key: String) = map[key]?.hidden == true
    fun hiddenKeys(): List<String> = map.filter { it.value.hidden }.keys.toList()
    fun isPinned(key: String) = (map[key]?.pinned ?: 0) > 0

    fun pinnedKeys(): List<String> =
        map.filter { it.value.pinned > 0 }.entries.sortedBy { it.value.pinned }.map { it.key }

    fun recentKeys(max: Int): List<String> =
        map.filter { it.value.lastUsed > 0 && !it.value.hidden }.entries
            .sortedByDescending { it.value.lastUsed }.take(max).map { it.key }

    private fun edit(key: String, block: (Meta) -> Unit) {
        val m = map.getOrPut(key) { Meta() }
        block(m)
        save(key, m)
    }

    fun setHidden(key: String, v: Boolean) = edit(key) { it.hidden = v }
    fun setLabel(key: String, v: String) = edit(key) { it.label = v.trim() }
    fun setIcon(key: String, v: String) = edit(key) { it.icon = v }

    fun setPinned(key: String, v: Boolean) = edit(key) {
        it.pinned = if (v) (map.values.maxOfOrNull { m -> m.pinned } ?: 0) + 1 else 0
    }

    fun touch(key: String) = edit(key) {
        it.launches += 1
        it.lastUsed = System.currentTimeMillis()
    }

    fun clearHistory() {
        for ((k, m) in map) {
            m.launches = 0
            m.lastUsed = 0L
            save(k, m)
        }
    }

    fun clearAll() {
        map.clear()
        Io.write { Store.db.writableDatabase.delete("app_meta", null, null) }
    }

    private fun save(key: String, m: Meta) {
        val cv = ContentValues().apply {
            put("key", key)
            put("hidden", if (m.hidden) 1 else 0)
            put("pinned", m.pinned)
            put("label", m.label)
            put("launches", m.launches)
            put("last_used", m.lastUsed)
            put("icon", m.icon)
        }
        Io.write { Store.db.writableDatabase.insertWithOnConflict("app_meta", null, cv, 5) } // 5 = CONFLICT_REPLACE
    }
}
