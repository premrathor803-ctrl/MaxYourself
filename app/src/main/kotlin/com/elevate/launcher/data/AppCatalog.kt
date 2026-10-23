package com.elevate.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class AppEntry(val pkg: String, val cls: String, val label: String, val group: String) {
    val key: String get() = "$pkg/$cls"
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
            if (ai.packageName == ctx.packageName) continue
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
