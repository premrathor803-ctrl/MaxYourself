package com.elevate.launcher.core

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Applies accent + palette and offers edge-to-edge helpers. */
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        ctl.isAppearanceLightStatusBars = lightIcons
        ctl.isAppearanceLightNavigationBars = lightIcons
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
