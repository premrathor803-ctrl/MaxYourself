package com.elevate.launcher.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.elevate.launcher.ElevateApp
import com.elevate.launcher.core.BaseActivity
import com.elevate.launcher.core.DefaultHome
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Perf
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppMeta
import com.elevate.launcher.data.HomeRepo
import com.elevate.launcher.ui.IconLoader

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val back = UiKit.text(this, "←", 24f, Palette.text, true).apply {
            gravity = Gravity.CENTER
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        bar.addView(back, lp(dp(48), dp(48)))
        bar.addView(UiKit.text(this, "Settings", 20f, Palette.text, true), lp(WRAP, WRAP).apply { leftMargin = dp(4) })
        root.addView(bar, lp(MATCH, WRAP))
        val container = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(container, lp(MATCH, 0, 1f))
        setContentView(root)
        edgeToEdge(root)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(container.id, SettingsFragment()).commit()
        }
    }
}

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var defaultHomePref: Preference? = null
    private val gestureLabels = ArrayList<Pair<Preference, String>>()

    companion object {
        val gestureActions = listOf(
            "none" to "Do nothing",
            "drawer" to "Open app drawer",
            "search" to "Open search",
            "notifications" to "Pull down notification panel",
            "center" to "Open Center",
            "settings" to "Launcher settings",
            "menu" to "Home screen menu",
            "edit" to "Edit home screen",
            "app" to "Open an app…"
        )

        fun actionLabel(id: String): String =
            if (id.startsWith("app:")) "Open " + (AppCatalog.get(id.removePrefix("app:"))?.displayLabel ?: "an app")
            else gestureActions.firstOrNull { it.first == id }?.second ?: "Do nothing"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(ctx)
        preferenceScreen = screen

        fun cat(title: String): PreferenceCategory {
            val c = PreferenceCategory(ctx)
            c.title = title
            c.isIconSpaceReserved = false
            screen.addPreference(c)
            return c
        }
        fun list(g: PreferenceGroup, key: String, title: String, def: String, entries: List<Pair<String, String>>) {
            val p = ListPreference(ctx)
            p.key = key
            p.title = title
            p.entries = entries.map { it.second }.toTypedArray()
            p.entryValues = entries.map { it.first }.toTypedArray()
            p.setDefaultValue(def)
            p.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            p.isIconSpaceReserved = false
            g.addPreference(p)
        }
        fun switch(g: PreferenceGroup, key: String, title: String, summary: String, def: Boolean) {
            val p = SwitchPreferenceCompat(ctx)
            p.key = key
            p.title = title
            p.summary = summary
            p.setDefaultValue(def)
            p.isIconSpaceReserved = false
            g.addPreference(p)
        }
        fun action(g: PreferenceGroup, title: String, summary: String, onClick: (Preference) -> Unit): Preference {
            val p = Preference(ctx)
            p.title = title
            p.summary = summary
            p.isIconSpaceReserved = false
            p.setOnPreferenceClickListener { onClick(p); true }
            g.addPreference(p)
            return p
        }
        fun nums(a: Int, b: Int) = (a..b).map { it.toString() to it.toString() }

        // ---------------------------------------------------------------- appearance
        val look = cat("Appearance")
        list(look, "theme", "Theme", "system", listOf("system" to "System default", "dark" to "Dark", "light" to "Light"))
        switch(look, "amoled", "Pure black dark mode", "Black background in dark mode. Saves battery on OLED screens.", false)
        list(look, "accent", "Accent colour", "teal", Palette.accents.keys.map { it to it.replaceFirstChar { c -> c.uppercase() } })
        list(look, "icon_shape", "Icon shape", "squircle", listOf(
            "system" to "Keep each app's own shape", "circle" to "Circle", "squircle" to "Squircle",
            "rounded" to "Rounded square", "square" to "Square"
        ))
        switch(look, "wallpaper", "Show wallpaper on home", "Turn off for a plain themed background.", true)

        // ---------------------------------------------------------------- home
        val home = cat("Home screen")
        list(home, "cols", "Columns", "4", nums(3, 6))
        list(home, "rows", "Rows", "5", nums(3, 7))
        list(home, "dock_count", "Dock icons", "4", nums(3, 6))
        list(home, "icon_scale", "Icon size", "100", listOf("70" to "Tiny", "85" to "Small", "100" to "Normal", "115" to "Large", "130" to "Extra large"))
        list(home, "spacing", "Spacing", "normal", listOf("compact" to "Compact", "normal" to "Normal", "spacious" to "Spacious"))
        switch(home, "show_labels", "App names under icons", "", true)
        switch(home, "show_clock", "Clock and date", "", true)
        switch(home, "show_search", "Search bar", "", true)
        action(home, "Reset home layout", "Back to the default arrangement. Apps stay installed.") {
            Dialogs.confirm(requireContext(), "Reset home layout", "Your icons, folders and pages go back to the default arrangement. Your apps are not uninstalled.", "Reset") {
                HomeRepo.reset(requireContext())
                requireContext().toast("Home layout reset")
            }
        }

        // ---------------------------------------------------------------- drawer
        val drawer = cat("App drawer")
        list(drawer, "drawer_style", "Layout", "list", listOf("list" to "List", "grid" to "Grid"))
        list(drawer, "drawer_cols", "Grid columns", "4", nums(3, 6))
        action(drawer, "Clear recent apps", "Forget which apps you opened from here.") {
            AppMeta.clearHistory()
            requireContext().toast("Recent apps cleared")
        }
        action(drawer, "Forget pins and custom names", "Unpins apps and removes names you changed. Hidden apps show again.") {
            Dialogs.confirm(requireContext(), "Forget pins and names?", "Pinned apps, renamed apps and the hidden-apps list are cleared. Hidden apps will show in the drawer again.", "Clear") {
                AppMeta.clearAll()
                requireContext().toast("Cleared")
            }
        }

        // ---------------------------------------------------------------- gestures
        val gest = cat("Gestures")
        for ((key, title, def) in listOf(
            Triple("gesture_up", "Swipe up", "drawer"),
            Triple("gesture_down", "Swipe down", "notifications"),
            Triple("gesture_double", "Double tap on empty space", "none"),
            Triple("gesture_long", "Long press on empty space", "menu")
        )) {
            val current = Prefs.sp.getString(key, def) ?: def
            val p = action(gest, title, actionLabel(current)) { pref -> pickGesture(pref, key, title) }
            gestureLabels.add(p to key)
        }
        action(gest, "Note", "Opening the notification panel from a launcher is blocked by some phone makers.") { }

        // ---------------------------------------------------------------- performance
        val perf = cat("Performance")
        list(perf, "perf", "Profile", "balanced", listOf(
            "balanced" to "Balanced", "lightweight" to "Lightweight", "minimal" to "Minimal"
        ))
        action(perf, "What the profiles do", "Lightweight shortens animations and uses a smaller icon cache. Minimal turns animations off. They only change this launcher, not the rest of the phone.") { }
        action(perf, "Clear icon cache", "Frees memory; icons reload as needed.") {
            IconLoader.clear()
            requireContext().toast("Icon cache cleared")
        }

        // ---------------------------------------------------------------- default launcher
        val def = cat("Default launcher")
        defaultHomePref = action(def, "Make Elevate your Home app", defaultSummary()) {
            DefaultHome.request(requireActivity())
        }
        action(def, "Do I have to?", "No. Elevate also works as a normal app. Setting it as default just makes the Home button open it.") { }

        // ---------------------------------------------------------------- data
        val data = cat("Your data")
        action(data, "Where is my data?", "Everything stays on this phone. Elevate has no account and does not send your data anywhere.") { }
        action(data, "Center data and settings", "Habits, workouts, goals and logs. Delete any of them, or all of them.") {
            com.elevate.launcher.center.ui.CenterActivity.open(requireContext(), "settings")
        }
        action(data, "Reset launcher settings", "Theme, grid, gestures and other options go back to defaults. Home layout is kept.") {
            Dialogs.confirm(requireContext(), "Reset settings?", "All launcher options return to their defaults. Your home layout and Center data are kept.", "Reset") { resetSettings() }
        }

        // ---------------------------------------------------------------- about
        val about = cat("About")
        val ver = try { requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName } catch (e: Exception) { "" }
        action(about, "Elevate Launcher", "Version $ver  •  ${Perf.label}") { }
    }

    private fun defaultSummary(): String =
        if (DefaultHome.isDefault(requireContext())) "Elevate is your default Home app" else "Not set yet. Tap to choose."

    private fun pickGesture(pref: Preference, key: String, title: String) {
        val ctx = requireContext()
        Dialogs.list(ctx, title, gestureActions.map { it.second }) { i ->
            val id = gestureActions[i].first
            if (id == "app") {
                val apps = AppCatalog.visible()
                if (apps.isEmpty()) { ctx.toast("Apps are still loading, try again"); return@list }
                Dialogs.list(ctx, "Open which app?", apps.map { it.displayLabel }) { j ->
                    val v = "app:" + apps[j].key
                    Prefs.putString(key, v)
                    pref.summary = actionLabel(v)
                }
            } else {
                Prefs.putString(key, id)
                pref.summary = actionLabel(id)
            }
        }
    }

    private fun resetSettings() {
        val lc = Prefs.getInt("layout_cols", Prefs.cols)
        val lr = Prefs.getInt("layout_rows", Prefs.rows)
        val ld = Prefs.getInt("layout_dock", Prefs.dockCount)
        val pages = Prefs.pages
        Prefs.sp.edit().clear().commit()
        Prefs.onboarded = true
        Prefs.seeded = true
        Prefs.pages = pages
        Prefs.putInt("layout_cols", lc); Prefs.putInt("layout_rows", lr); Prefs.putInt("layout_dock", ld)
        ElevateApp.applyNightMode()
        IconLoader.clear()
        requireContext().toast("Settings reset")
        activity?.recreate()
    }

    override fun onResume() {
        super.onResume()
        Prefs.sp.registerOnSharedPreferenceChangeListener(this)
        defaultHomePref?.summary = defaultSummary()
    }

    override fun onPause() {
        Prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String?) {
        when (key) {
            "theme" -> ElevateApp.applyNightMode()
            "accent", "amoled" -> activity?.recreate()
            "icon_shape", "perf", "icon_pack", "icon_scale" -> IconLoader.clear()
        }
    }
}
