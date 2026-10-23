package com.elevate.launcher.core

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.elevate.launcher.R

/** Runtime colour set. Updated from the current configuration + settings before views are built. */
object Palette {
    var dark = true
    var bg = 0xFF0F1115.toInt()
    var surface = 0xFF1B1E26.toInt()
    var surface2 = 0xFF262A34.toInt()
    var text = 0xFFF2F4F8.toInt()
    var dim = 0xFFA0A7B4.toInt()
    var accent = 0xFF19B5A5.toInt()
    var onAccent = Color.WHITE
    var divider = 0x22FFFFFF
    val danger = 0xFFE5484D.toInt()
    val ok = 0xFF3FB950.toInt()

    val accents = linkedMapOf(
        "teal" to 0xFF19B5A5.toInt(),
        "violet" to 0xFF8B6CFF.toInt(),
        "orange" to 0xFFF2882E.toInt(),
        "rose" to 0xFFE5527A.toInt(),
        "blue" to 0xFF3B82F6.toInt(),
        "green" to 0xFF3FB950.toInt(),
        "gold" to 0xFFE3B341.toInt()
    )

    fun accentStyle(name: String): Int = when (name) {
        "violet" -> R.style.Accent_Violet
        "orange" -> R.style.Accent_Orange
        "rose" -> R.style.Accent_Rose
        "blue" -> R.style.Accent_Blue
        "green" -> R.style.Accent_Green
        "gold" -> R.style.Accent_Gold
        else -> R.style.Accent_Teal
    }

    fun update(ctx: Context) {
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        dark = night
        if (night) {
            val black = Prefs.amoled
            bg = if (black) 0xFF000000.toInt() else 0xFF0F1115.toInt()
            surface = if (black) 0xFF121212.toInt() else 0xFF1B1E26.toInt()
            surface2 = if (black) 0xFF1E1E1E.toInt() else 0xFF262A34.toInt()
            text = 0xFFF2F4F8.toInt()
            dim = 0xFFA0A7B4.toInt()
            divider = 0x22FFFFFF
        } else {
            bg = 0xFFF5F6F9.toInt()
            surface = 0xFFFFFFFF.toInt()
            surface2 = 0xFFE8EBF1.toInt()
            text = 0xFF14171C.toInt()
            dim = 0xFF5D6472.toInt()
            divider = 0x22000000
        }
        accent = accents[Prefs.accent] ?: accents.getValue("teal")
        onAccent = if (ColorUtils.calculateLuminance(accent) > 0.55) 0xFF101010.toInt() else Color.WHITE
    }
}
