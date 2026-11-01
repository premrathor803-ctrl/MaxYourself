package com.elevate.launcher.core

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Applies accent + palette and offers edge-to-edge helpers. */
open class BaseActivity : AppCompatActivity() {

    /**
     * Test hook for the emulator run: "theme:light+accent:violet" sets appearance settings before the screen is built, so
     * every theme can be looked at without tapping through Settings. Only appearance keys are accepted.
     */
    protected fun ciStyle() {
        val spec = intent?.getStringExtra("ci_style") ?: return
        val allowed = setOf("theme", "accent", "amoled", "card_style", "dash_layout", "dash_order", "dash_hidden", "nav_style", "motion", "user_name", "perf")
        var nightChanged = false
        for (pair in spec.split('+')) {
            val kv = pair.split(':', limit = 2)
            if (kv.size != 2 || kv[0] !in allowed) continue
            if (kv[0] == "amoled") Prefs.putBool("amoled", kv[1] == "true") else Prefs.putString(kv[0], kv[1])
            if (kv[0] == "theme") nightChanged = true
        }
        if (nightChanged) com.elevate.launcher.ElevateApp.applyNightMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // only on a fresh start: a restart for a theme change must not put the test's settings back
        if (savedInstanceState == null) ciStyle()
        theme.applyStyle(Palette.accentStyle(Prefs.accent), true)
        super.onCreate(savedInstanceState)
        Palette.update(this)
        if (opaqueWindow) window.setBackgroundDrawable(ColorDrawable(Palette.bg))
    }

    /** Home overrides this to let the wallpaper show through. */
    protected open val opaqueWindow: Boolean get() = true

    /** Draw behind the system bars and pad [root] by their size (and the keyboard when it is open). */
    protected fun edgeToEdge(root: View, top: Boolean = true, bottom: Boolean = true, lightIcons: Boolean = !Palette.dark) {
        setupWindow(lightIcons)
        padForInsets(root, top, bottom)
    }

    protected fun setupWindow(lightIcons: Boolean = !Palette.dark) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        ctl.isAppearanceLightStatusBars = lightIcons
        ctl.isAppearanceLightNavigationBars = lightIcons
    }

    protected fun setLightIcons(light: Boolean) {
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        ctl.isAppearanceLightStatusBars = light
        ctl.isAppearanceLightNavigationBars = light
    }

    @Suppress("DEPRECATION")
    protected fun padForInsets(v: View, top: Boolean = true, bottom: Boolean = true) {
        ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomInset = maxOf(bars.bottom, ime.bottom, insets.systemWindowInsetBottom)
            view.setPadding(bars.left, if (top) bars.top else 0, bars.right, if (bottom) bottomInset else 0)
            insets
        }
        v.requestApplyInsets()
    }
}
