package com.elevate.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.data.AppCatalog
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Icon pack support for packs that follow the common ADW / Nova / Apex "appfilter.xml" format.
 *
 * What it does: finds installed packs, reads their appfilter, and draws the pack's icon for every app that has
 * an entry. Apps without an entry keep their normal icon. Every step is wrapped so a broken or removed pack falls
 * back to normal icons instead of crashing.
 *
 * What it does not do: it is not tested against every pack. Packs that ship icon backs, masks or other special
 * rules are read only for their direct per-app icons.
 */
object IconPacks {
    class Pack(val pkg: String, val label: String)

    private class Loaded(
        val pkg: String,
        val res: Resources,
        val byComp: HashMap<String, String>,
        val byPkg: HashMap<String, String>,
        val names: List<String>
    )

    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme"
    )

    private val lock = Any()
    @Volatile private var loaded: Loaded? = null
    @Volatile private var loadedFor = ""
    @Volatile private var failedFor = ""

    /** Installed packs that advertise one of the common icon-pack actions. */
    fun discover(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, Pack>()
        for (a in ACTIONS) {
            try {
                for (ri in pm.queryIntentActivities(Intent(a), 0)) {
                    val ai = ri.activityInfo ?: continue
                    if (ai.packageName == ctx.packageName || seen.containsKey(ai.packageName)) continue
                    val label = try { ai.applicationInfo.loadLabel(pm).toString() } catch (e: Exception) { ai.packageName }
                    seen[ai.packageName] = Pack(ai.packageName, label)
                }
            } catch (e: Exception) {
                // ignore: some devices restrict this query
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun labelOf(ctx: Context, pkg: String): String =
        try { ctx.packageManager.getApplicationInfo(pkg, 0).loadLabel(ctx.packageManager).toString() } catch (e: Exception) { pkg }

    fun installed(ctx: Context, pkg: String): Boolean =
        pkg.isNotEmpty() && try { ctx.packageManager.getApplicationInfo(pkg, 0); true } catch (e: Exception) { false }

    /** Forget what was read, so the next icon request re-reads the selected pack. Call after changing or removing a pack. */
    fun invalidate() {
        synchronized(lock) {
            loaded = null
            loadedFor = ""
            failedFor = ""
        }
    }

    private fun ensure(ctx: Context, pkg: String): Loaded? {
        val cur = loaded
        if (cur != null && loadedFor == pkg) return cur
        if (failedFor == pkg) return null
        synchronized(lock) {
            val again = loaded
            if (again != null && loadedFor == pkg) return again
            val l = try { parse(ctx, pkg) } catch (t: Throwable) { null }
            if (l == null) {
                failedFor = pkg
                loaded = null
                loadedFor = ""
            } else {
                loaded = l
                loadedFor = pkg
                failedFor = ""
            }
            return l
        }
    }

    private fun parse(ctx: Context, pkg: String): Loaded? {
        val res = ctx.packageManager.getResourcesForApplication(pkg)
        val parser: XmlPullParser = run {
            val id = res.getIdentifier("appfilter", "xml", pkg)
            if (id != 0) {
                res.getXml(id)
            } else {
                val p = XmlPullParserFactory.newInstance().newPullParser()
                p.setInput(res.assets.open("appfilter.xml"), null)
                p
            }
        }
        val byComp = HashMap<String, String>()
        val byPkg = HashMap<String, String>()
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val dr = parser.getAttributeValue(null, "drawable")
                if (comp != null && dr != null && dr.isNotBlank()) {
                    val cn = componentOf(comp)
                    if (cn != null) {
                        val key = cn.packageName + "/" + cn.className
                        if (!byComp.containsKey(key)) byComp[key] = dr
                        val prev = byPkg[cn.packageName]
                        if (prev == null) byPkg[cn.packageName] = dr else if (prev != dr) byPkg[cn.packageName] = ""
                    }
                }
            }
            ev = parser.next()
        }
        if (byComp.isEmpty()) return null
        return Loaded(pkg, res, byComp, byPkg, byComp.values.distinct().sorted())
    }

    /** "ComponentInfo{com.app/com.app.Main}" -> ComponentName. */
    private fun componentOf(raw: String): ComponentName? {
        val s = raw.trim()
        if (!s.startsWith("ComponentInfo{") || !s.endsWith("}")) return null
        return ComponentName.unflattenFromString(s.substring(14, s.length - 1).trim())
    }

    private fun drawableOf(l: Loaded, name: String): Drawable? {
        val id = l.res.getIdentifier(name, "drawable", l.pkg)
        if (id == 0) return null
        return try { ResourcesCompat.getDrawable(l.res, id, null) } catch (e: Exception) { null }
    }

    /**
     * The icon to draw for an app, or null to use its normal icon.
     * [override] is the per-app choice: "" follow the selected pack, "default" always the original icon,
     * "pack:<package>:<drawable>" a specific icon from an installed pack.
     */
    fun load(ctx: Context, comp: ComponentName, override: String): Drawable? {
        try {
            if (override == "default") return null
            if (override.startsWith("pack:")) {
                val rest = override.removePrefix("pack:")
                val i = rest.indexOf(':')
                if (i > 0) {
                    val l = ensure(ctx, rest.substring(0, i)) ?: return null
                    return drawableOf(l, rest.substring(i + 1))
                }
            }
            val pack = Prefs.iconPack
            if (pack.isEmpty()) return null
            val l = ensure(ctx, pack) ?: return null
            val name = lookup(l, comp) ?: return null
            return drawableOf(l, name)
        } catch (t: Throwable) {
            return null
        }
    }

    private fun lookup(l: Loaded, comp: ComponentName): String? {
        l.byComp[comp.packageName + "/" + comp.className]?.let { return it }
        val p = l.byPkg[comp.packageName]
        return if (p != null && p.isNotEmpty()) p else null
    }

    // ------------------------------------------------------------ helpers for settings screens
    /** (apps with an icon in the pack, apps checked). Reads the pack, so call off the main thread if the pack is big. */
    fun coverage(ctx: Context, pkg: String): Pair<Int, Int>? {
        val l = ensure(ctx, pkg) ?: return null
        val apps = AppCatalog.visible()
        var n = 0
        for (a in apps) {
            val name = lookup(l, a.component) ?: continue
            if (l.res.getIdentifier(name, "drawable", l.pkg) != 0) n++
        }
        return n to apps.size
    }

    /** A few icon names to preview a pack. */
    fun sampleNames(ctx: Context, pkg: String, count: Int): List<String> {
        val l = ensure(ctx, pkg) ?: return emptyList()
        val out = ArrayList<String>()
        for (a in AppCatalog.visible()) {
            val name = lookup(l, a.component) ?: continue
            if (!out.contains(name) && l.res.getIdentifier(name, "drawable", l.pkg) != 0) out.add(name)
            if (out.size >= count) return out
        }
        for (n in l.names) {
            if (out.size >= count) break
            if (!out.contains(n) && l.res.getIdentifier(n, "drawable", l.pkg) != 0) out.add(n)
        }
        return out
    }

    fun search(ctx: Context, pkg: String, query: String, limit: Int): List<String> {
        val l = ensure(ctx, pkg) ?: return emptyList()
        val q = query.trim().lowercase()
        val out = ArrayList<String>()
        for (n in l.names) {
            if (q.isEmpty() || n.lowercase().contains(q)) {
                if (l.res.getIdentifier(n, "drawable", l.pkg) != 0) out.add(n)
                if (out.size >= limit) break
            }
        }
        return out
    }

    fun drawable(ctx: Context, pkg: String, name: String): Drawable? {
        val l = ensure(ctx, pkg) ?: return null
        return drawableOf(l, name)
    }
}
