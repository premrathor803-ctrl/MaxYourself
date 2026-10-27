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
    val theme get() = s("theme", "system")            // system | dark | light
    val amoled get() = b("amoled", false)
    val accent get() = s("accent", "teal")
    val wallpaper get() = b("wallpaper", true)
    val perf get() = s("perf", "balanced")            // balanced | lightweight | minimal
    val iconShape get() = s("icon_shape", "squircle") // system | circle | squircle | rounded | square

    // ---- home grid
    val cols get() = i("cols", 4).coerceIn(3, 6)
    val rows get() = i("rows", 5).coerceIn(3, 7)
    val dockCount get() = i("dock_count", 4).coerceIn(3, 6)
    val iconScale get() = i("icon_scale", 100).coerceIn(70, 130)
    val spacing get() = s("spacing", "normal")        // compact | normal | spacious
    val showLabels get() = b("show_labels", true)
    val showClock get() = b("show_clock", true)
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
