package com.elevate.launcher.core

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/**
 * All launcher settings. Backed by the default SharedPreferences so the settings screen
 * (PreferenceFragmentCompat) and the rest of the app see the same values.
 * List-type settings are stored as strings, so numbers are parsed defensively here.
 */
object Prefs {
    lateinit var sp: SharedPreferences
        private set

    fun init(app: Context) {
        sp = PreferenceManager.getDefaultSharedPreferences(app.applicationContext)
    }

    private fun s(k: String, d: String): String = sp.getString(k, d) ?: d
    private fun i(k: String, d: Int): Int = sp.getString(k, null)?.toIntOrNull() ?: d
    private fun b(k: String, d: Boolean): Boolean = sp.getBoolean(k, d)

    fun putString(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
    fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun getInt(k: String, d: Int): Int = try { sp.getInt(k, d) } catch (e: ClassCastException) { d }

    // ---- appearance
    val theme get() = s("theme", "dark")              // dark | system | light (dark is the default look)
    val amoled get() = b("amoled", false)
    val accent get() = s("accent", "electric")
    val wallpaper get() = b("wallpaper", true)
    val perf get() = s("perf", "balanced")            // balanced | lightweight | minimal
    val cardStyle get() = s("card_style", "soft")     // soft | flat | outline | accent
    val motion get() = s("motion", "full")            // full | reduced | off (the performance profile can lower it further)
    val navStyle get() = s("nav_style", "pill")       // pill | dots | bar (page indicator on the home screen)
    val dashLayout get() = s("dash_layout", "balanced") // balanced | focus | explorer
    /** Dashboard card order and hidden cards, kept as comma separated ids. Empty means "use the layout's own". */
    val dashOrder get() = s("dash_order", "")
    val dashHidden get() = s("dash_hidden", "")
    /** First name used in the greeting. Optional, never leaves the phone. */
    var userName: String
        get() = s("user_name", "")
        set(v) = putString("user_name", v.trim().take(24))
    val iconShape get() = s("icon_shape", "squircle") // system | circle | squircle | rounded | square
    val font get() = s("font", "system")              // system | serif | mono | condensed | custom

    // ---- home grid
    val cols get() = i("cols", 4).coerceIn(3, 6)
    val rows get() = i("rows", 5).coerceIn(3, 7)
    val dockCount get() = i("dock_count", 4).coerceIn(3, 6)
    val iconScale get() = i("icon_scale", 100).coerceIn(70, 130)
    val spacing get() = s("spacing", "normal")        // compact | normal | spacious
    val showLabels get() = b("show_labels", true)
    val showClock get() = b("show_clock", true)
    val clockStyle get() = s("clock_style", "classic") // classic | bold | stacked | compact | analog
    val showSearch get() = b("show_search", true)
    /** An extra page at the end of the home pager that holds widgets. */
    var widgetsPage: Boolean
        get() = b("widgets_page", false)
        set(v) = putBool("widgets_page", v)
    var pages: Int
        get() = getInt("pages", 2).coerceIn(1, 8)
        set(v) = putInt("pages", v.coerceIn(1, 8))

    // ---- drawer
    val drawerStyle get() = s("drawer_style", "list") // list | grid
    val drawerCols get() = i("drawer_cols", 4).coerceIn(3, 6)

    // ---- gestures (action ids, see Gestures.kt)
    val gestureUp get() = s("gesture_up", "drawer")
    val gestureDown get() = s("gesture_down", "notifications")
    val gestureDouble get() = s("gesture_double", "none")
    val gestureLong get() = s("gesture_long", "menu")
    val gesturePinchIn get() = s("gesture_pinch_in", "none")
    val gesturePinchOut get() = s("gesture_pinch_out", "none")
    val gestureTwoUp get() = s("gesture_two_up", "none")
    val gestureTwoDown get() = s("gesture_two_down", "none")

    // ---- state
    var onboarded: Boolean
        get() = b("onboarded", false)
        set(v) = putBool("onboarded", v)
    var seeded: Boolean
        get() = b("seeded", false)
        set(v) = putBool("seeded", v)
    val iconPack get() = s("icon_pack", "")
}
