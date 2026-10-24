package com.elevate.launcher.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.elevate.launcher.core.Prefs

class HomeItem(
    var id: Long,
    var area: String,
    var page: Int,
    var pos: Int,
    var kind: String,
    var pkg: String,
    var cls: String,
    var label: String,
    var parent: Long
) {
    val key: String get() = "$pkg/$cls"
    val isFolder: Boolean get() = kind == HomeRepo.KIND_FOLDER
    val isApp: Boolean get() = kind == HomeRepo.KIND_APP
}

/**
 * The home layout, held in memory and written through to SQLite on a background thread.
 * Positions are stored as row * cols + col for the grid size recorded in layout_cols/layout_rows.
 */
object HomeRepo {
    const val PAGE = "page"
    const val DOCK = "dock"
    const val FOLDER = "folder"
    const val KIND_APP = "app"
    const val KIND_FOLDER = "folder"
    const val KIND_CENTER = "center"
    const val MAX_PAGES = 8

    val items = ArrayList<HomeItem>()
    private var nextId = 1L

    fun load() {
        items.clear()
        val c = Store.db.readableDatabase.rawQuery(
            "SELECT id, area, page, pos, kind, pkg, cls, label, parent FROM home_items", null
        )
        c.use {
            while (it.moveToNext()) {
                items.add(HomeItem(it.getLong(0), it.getString(1), it.getInt(2), it.getInt(3), it.getString(4),
                    it.getString(5), it.getString(6), it.getString(7), it.getLong(8)))
            }
        }
        nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
    }

    // ---------------------------------------------------------------- queries
    fun pageItems(p: Int): List<HomeItem> = items.filter { it.area == PAGE && it.page == p }
    fun dockItems(): List<HomeItem> = items.filter { it.area == DOCK }
    fun folderItems(fid: Long): List<HomeItem> = items.filter { it.area == FOLDER && it.parent == fid }.sortedBy { it.pos }
    fun find(id: Long): HomeItem? = items.firstOrNull { it.id == id }

    fun at(area: String, page: Int, pos: Int): HomeItem? = items.firstOrNull {
        it.area == area && it.pos == pos && (area == DOCK || it.page == page)
    }

    fun hasApp(key: String): Boolean = items.any { it.kind == KIND_APP && it.key == key && it.area != FOLDER }

    fun firstFree(area: String, page: Int, capacity: Int): Int? =
        (0 until capacity).firstOrNull { at(area, page, it) == null }

    // ---------------------------------------------------------------- persistence
    private fun save(it: HomeItem) {
        val cv = ContentValues().apply {
            put("id", it.id); put("area", it.area); put("page", it.page); put("pos", it.pos)
            put("kind", it.kind); put("pkg", it.pkg); put("cls", it.cls); put("label", it.label); put("parent", it.parent)
        }
        Io.write { Store.db.writableDatabase.insertWithOnConflict("home_items", null, cv, 5) }
    }

    private fun delete(id: Long) {
        Io.write { Store.db.writableDatabase.delete("home_items", "id=?", arrayOf(id.toString())) }
    }

    private fun clearAll() {
        items.clear()
        Io.write { Store.db.writableDatabase.delete("home_items", null, null) }
    }

    // ---------------------------------------------------------------- mutations
    fun add(kind: String, pkg: String, cls: String, label: String, area: String, page: Int, pos: Int, parent: Long = 0L): HomeItem {
        val it = HomeItem(nextId++, area, page, pos, kind, pkg, cls, label, parent)
        items.add(it)
        save(it)
        return it
    }

    /** Adds an app to the first empty cell on any page (adding a page if everything is full). */
    fun addAppToFirstFree(e: AppEntry): HomeItem? {
        val cap = Prefs.cols * Prefs.rows
        for (p in 0 until Prefs.pages) {
            val pos = firstFree(PAGE, p, cap)
            if (pos != null) return add(KIND_APP, e.pkg, e.cls, e.label, PAGE, p, pos)
        }
        if (Prefs.pages >= MAX_PAGES) return null
        Prefs.pages = Prefs.pages + 1
        return add(KIND_APP, e.pkg, e.cls, e.label, PAGE, Prefs.pages - 1, 0)
    }

    /** Moves an existing item (e.g. out of a folder) to the first empty cell on any page. */
    fun moveToFirstFree(item: HomeItem): Boolean {
        val cap = Prefs.cols * Prefs.rows
        for (p in 0 until Prefs.pages) {
            val pos = firstFree(PAGE, p, cap) ?: continue
            place(item, PAGE, p, pos)
            return true
        }
        if (Prefs.pages >= MAX_PAGES) return false
        Prefs.pages = Prefs.pages + 1
        place(item, PAGE, Prefs.pages - 1, 0)
        return true
    }

    fun addAppToDock(e: AppEntry): HomeItem? {
        val pos = firstFree(DOCK, 0, Prefs.dockCount) ?: return null
        return add(KIND_APP, e.pkg, e.cls, e.label, DOCK, 0, pos)
    }

    fun place(item: HomeItem, area: String, page: Int, pos: Int) {
        val oldParent = if (item.area == FOLDER) item.parent else 0L
        item.area = area; item.page = page; item.pos = pos; item.parent = 0L
        save(item)
        if (oldParent != 0L) tidyFolder(oldParent)
    }

    fun remove(item: HomeItem) {
        items.remove(item)
        delete(item.id)
        if (item.isFolder) {
            items.filter { it.parent == item.id && it.area == FOLDER }.toList().forEach { items.remove(it); delete(it.id) }
        } else if (item.area == FOLDER) {
            tidyFolder(item.parent)
        }
    }

    fun removeByKey(key: String) {
        items.filter { it.kind == KIND_APP && it.key == key }.toList().forEach { remove(it) }
    }

    fun renameFolder(f: HomeItem, name: String) {
        f.label = name.trim().ifEmpty { "Folder" }
        save(f)
    }

    /** Dropping [dragged] on [target]: add to an existing folder, or make a new folder out of the two. */
    fun mergeIntoFolder(target: HomeItem, dragged: HomeItem): HomeItem {
        val oldParent = if (dragged.area == FOLDER) dragged.parent else 0L
        val folder: HomeItem
        if (target.isFolder) {
            folder = target
        } else {
            folder = add(KIND_FOLDER, "", "", "Folder", target.area, target.page, target.pos)
            target.area = FOLDER; target.parent = folder.id; target.page = 0; target.pos = 0
            save(target)
        }
        dragged.area = FOLDER; dragged.parent = folder.id; dragged.page = 0
        dragged.pos = folderItems(folder.id).count { it.id != dragged.id }
        save(dragged)
        if (oldParent != 0L && oldParent != folder.id) tidyFolder(oldParent)
        return folder
    }

    /** Dissolves folders with one app, removes empty ones, and renumbers the rest. */
    fun tidyFolder(fid: Long) {
        val f = find(fid) ?: return
        val kids = folderItems(fid)
        when {
            kids.isEmpty() -> { items.remove(f); delete(f.id) }
            kids.size == 1 -> {
                val k = kids[0]
                k.area = f.area; k.page = f.page; k.pos = f.pos; k.parent = 0L
                save(k)
                items.remove(f); delete(f.id)
            }
            else -> kids.forEachIndexed { i, k -> if (k.pos != i) { k.pos = i; save(k) } }
        }
    }

    fun reorderFolder(fid: Long, from: Int, to: Int) {
        val kids = folderItems(fid).toMutableList()
        if (from !in kids.indices || to !in kids.indices) return
        kids.add(to, kids.removeAt(from))
        kids.forEachIndexed { i, k -> if (k.pos != i) { k.pos = i; save(k) } }
    }

    // ---------------------------------------------------------------- pages
    fun removePage(p: Int) {
        if (Prefs.pages <= 1) return
        val onPage = pageItems(p)
        onPage.forEach { remove(it) }
        items.filter { it.area == PAGE && it.page > p }.forEach { it.page -= 1; save(it) }
        Prefs.pages = Prefs.pages - 1
    }

    fun addPage(): Boolean {
        if (Prefs.pages >= MAX_PAGES) return false
        Prefs.pages = Prefs.pages + 1
        return true
    }

    fun swapPages(a: Int, b: Int) {
        if (a == b || a < 0 || b < 0 || a >= Prefs.pages || b >= Prefs.pages) return
        for (it in items) if (it.area == PAGE) {
            if (it.page == a) { it.page = b; save(it) } else if (it.page == b) { it.page = a; save(it) }
        }
    }

    // ---------------------------------------------------------------- grid changes
    /** Re-maps stored positions when the grid size or dock size in settings no longer matches the stored layout. */
    fun ensureGrid() {
        val oc = Prefs.getInt("layout_cols", Prefs.cols)
        val orows = Prefs.getInt("layout_rows", Prefs.rows)
        val od = Prefs.getInt("layout_dock", Prefs.dockCount)
        val nc = Prefs.cols; val nr = Prefs.rows; val nd = Prefs.dockCount
        if (oc == nc && orows == nr && od == nd) {
            Prefs.putInt("layout_cols", nc); Prefs.putInt("layout_rows", nr); Prefs.putInt("layout_dock", nd)
            return
        }
        val overflow = ArrayList<HomeItem>()
        for (it in items.filter { x -> x.area == PAGE }.sortedWith(compareBy({ x -> x.page }, { x -> x.pos }))) {
            val col = it.pos % oc; val row = it.pos / oc
            if (col < nc && row < nr) { it.pos = row * nc + col; save(it) } else overflow.add(it)
        }
        for (it in items.filter { x -> x.area == DOCK && x.pos >= nd }.sortedBy { x -> x.pos }) overflow.add(it)
        Prefs.putInt("layout_cols", nc); Prefs.putInt("layout_rows", nr); Prefs.putInt("layout_dock", nd)
        val cap = nc * nr
        for (it in overflow) {
            var placed = false
            for (p in 0 until Prefs.pages) {
                val pos = firstFree(PAGE, p, cap) ?: continue
                it.area = PAGE; it.page = p; it.pos = pos; save(it); placed = true; break
            }
            if (!placed && Prefs.pages < MAX_PAGES) {
                Prefs.pages = Prefs.pages + 1
                it.area = PAGE; it.page = Prefs.pages - 1; it.pos = 0; save(it)
            }
        }
    }

    // ---------------------------------------------------------------- defaults / reset
    fun reset(ctx: Context) {
        clearAll()
        Prefs.pages = 2
        Prefs.putInt("layout_cols", Prefs.cols); Prefs.putInt("layout_rows", Prefs.rows); Prefs.putInt("layout_dock", Prefs.dockCount)
        seedDefaults(ctx)
    }

    private fun resolve(ctx: Context, intent: Intent): AppEntry? {
        val ri = try { ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) } catch (e: Exception) { null } ?: return null
        val ai = ri.activityInfo ?: return null
        return AppCatalog.get("${ai.packageName}/${ai.name}") ?: AppCatalog.byPackage(ai.packageName)
    }

    private fun cat(ctx: Context, category: String): AppEntry? =
        resolve(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category))

    /** A clean first layout: a few essential apps in the dock, the Center shortcut and common apps on page 1. */
    fun seedDefaults(ctx: Context) {
        val dockPicks = listOf(
            resolve(ctx, Intent(Intent.ACTION_DIAL)),
            cat(ctx, Intent.CATEGORY_APP_MESSAGING),
            cat(ctx, Intent.CATEGORY_APP_BROWSER),
            resolve(ctx, Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            cat(ctx, Intent.CATEGORY_APP_CONTACTS),
            cat(ctx, Intent.CATEGORY_APP_GALLERY)
        ).filterNotNull().distinctBy { it.key }.take(Prefs.dockCount)
        dockPicks.forEachIndexed { i, e -> add(KIND_APP, e.pkg, e.cls, e.label, DOCK, 0, i) }
        val used = dockPicks.map { it.key }.toHashSet()

        add(KIND_CENTER, "", "", "Center", PAGE, 0, 0)
        val pagePicks = listOf(
            resolve(ctx, Intent(Settings.ACTION_SETTINGS)),
            cat(ctx, Intent.CATEGORY_APP_MARKET),
            cat(ctx, Intent.CATEGORY_APP_GALLERY),
            cat(ctx, Intent.CATEGORY_APP_MAPS),
            resolve(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS)),
            cat(ctx, Intent.CATEGORY_APP_CALCULATOR),
            cat(ctx, Intent.CATEGORY_APP_CALENDAR),
            cat(ctx, Intent.CATEGORY_APP_EMAIL),
            cat(ctx, Intent.CATEGORY_APP_MUSIC)
        ).filterNotNull().distinctBy { it.key }.filter { it.key !in used }
        var pos = 1
        for (e in pagePicks) {
            if (pos >= Prefs.cols * Prefs.rows) break
            add(KIND_APP, e.pkg, e.cls, e.label, PAGE, 0, pos++)
        }
    }
}
