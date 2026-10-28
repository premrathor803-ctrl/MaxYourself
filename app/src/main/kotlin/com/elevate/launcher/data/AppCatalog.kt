package com.elevate.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import com.elevate.launcher.core.Prefs
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class AppEntry(val pkg: String, val cls: String, val label: String, val autoGroup: String) {
    val key: String get() = "$pkg/$cls"
    /** The group shown in the drawer: the one the person picked, or the automatic guess. */
    val group: String get() = GroupOverride.get(key) ?: autoGroup
    val component: ComponentName get() = ComponentName(pkg, cls)
    val displayLabel: String
        get() = AppMeta.get(key)?.label?.takeIf { it.isNotBlank() } ?: label
}

/** The list of launchable apps, loaded on a background thread and refreshed when packages change. */
object AppCatalog {
    const val GAMES = "Games"
    const val SOCIAL = "Social"
    const val MEDIA = "Media"
    const val PRODUCTIVITY = "Productivity"
    const val UTILITIES = "Utilities"
    val groups = listOf(GAMES, SOCIAL, MEDIA, PRODUCTIVITY, UTILITIES)

    @Volatile var apps: List<AppEntry> = emptyList()
        private set
    @Volatile private var byKey: Map<String, AppEntry> = emptyMap()
    @Volatile var loaded = false
        private set
    @Volatile private var lastLoad = 0L

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val loading = AtomicBoolean(false)
    @Volatile private var again = false

    fun addListener(l: () -> Unit) { listeners.addIfAbsent(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun get(key: String): AppEntry? = byKey[key]
    fun byPackage(pkg: String): AppEntry? = apps.firstOrNull { it.pkg == pkg }
    fun visible(): List<AppEntry> = apps.filter { !AppMeta.isHidden(it.key) }

    /** Cheap safety net for missed package broadcasts (e.g. after the process was killed). */
    fun refreshIfStale(ctx: Context) {
        if (!loaded || System.currentTimeMillis() - lastLoad > 5 * 60 * 1000L) reload(ctx)
    }

    fun reload(ctx: Context) {
        val app = ctx.applicationContext
        if (!loading.compareAndSet(false, true)) { again = true; return }
        Io.work {
            try {
                do {
                    again = false
                    val list = query(app)
                    apps = list
                    byKey = list.associateBy { it.key }
                    loaded = true
                    lastLoad = System.currentTimeMillis()
                    Io.ui { listeners.forEach { l -> l() } }
                } while (again)
            } finally {
                loading.set(false)
            }
        }
    }

    private fun query(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = ArrayList<AppEntry>()
        for (ri in pm.queryIntentActivities(i, 0)) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == ctx.packageName && ai.name != com.elevate.launcher.privacy.Auth.ALIAS) continue
            val label = try { ri.loadLabel(pm)?.toString() } catch (e: Exception) { null }
            out.add(AppEntry(ai.packageName, ai.name, label?.takeIf { it.isNotBlank() } ?: ai.packageName, groupOf(ai.applicationInfo, ai.packageName)))
        }
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayLabel })
        return out
    }

    private fun groupOf(info: ApplicationInfo?, pkg: String): String {
        if (info != null && Build.VERSION.SDK_INT >= 26) {
            when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> return GAMES
                ApplicationInfo.CATEGORY_SOCIAL -> return SOCIAL
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> return MEDIA
                ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_NEWS -> return PRODUCTIVITY
                ApplicationInfo.CATEGORY_MAPS -> return UTILITIES
            }
        }
        if (info != null && (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0) return GAMES
        val p = pkg.lowercase()
        return when {
            listOf("whatsapp", "instagram", "facebook", "telegram", "snapchat", "twitter", "discord", "messenger", "reddit", "linkedin", "threads").any { p.contains(it) } -> SOCIAL
            listOf("youtube", "spotify", "music", "video", "gallery", "photos", "camera", "player", "netflix", "podcast").any { p.contains(it) } -> MEDIA
            listOf("docs", "office", "sheets", "slides", "drive", "keep", "notes", "mail", "calendar", "gmail", "outlook", "teams", "zoom", "meet").any { p.contains(it) } -> PRODUCTIVITY
            p.contains("game") || p.contains("games") -> GAMES
            else -> UTILITIES
        }
    }
}

/**
 * The person's own choice of drawer group for an app, for when the automatic guess is wrong. Kept as one small text
 * setting, so it is saved in backups with the other settings and cleared by "Reset settings".
 */
object GroupOverride {
    private const val KEY = "app_groups"
    private var raw = ""
    private var map: Map<String, String> = emptyMap()

    @Synchronized private fun current(): Map<String, String> {
        val r = Prefs.sp.getString(KEY, "") ?: ""
        if (r != raw) {
            raw = r
            val m = HashMap<String, String>()
            try {
                val o = org.json.JSONObject(if (r.isEmpty()) "{}" else r)
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val g = o.optString(k)
                    if (g in AppCatalog.groups) m[k] = g
                }
            } catch (e: Exception) {
                // a damaged value is simply ignored
            }
            map = m
        }
        return map
    }

    fun get(key: String): String? = current()[key]

    @Synchronized fun set(key: String, group: String?) {
        val m = HashMap(current())
        if (group == null || group !in AppCatalog.groups) m.remove(key) else m[key] = group
        val o = org.json.JSONObject()
        for ((k, v) in m) o.put(k, v)
        Prefs.putString(KEY, if (m.isEmpty()) "" else o.toString())
    }
}
