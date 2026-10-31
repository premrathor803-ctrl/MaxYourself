package com.elevate.launcher.core

/**
 * Performance profiles. They only change what the launcher itself does
 * (animations, icon cache); they do not change how fast the phone is.
 */
object Perf {
    val animate: Boolean get() = Motion.on

    fun duration(base: Long): Long = Motion.dur(base)

    /** Fraction of the app heap the icon cache may use, in 1/N. */
    fun cacheDivisor(): Int = when (Prefs.perf) {
        "minimal" -> 24
        "lightweight" -> 16
        else -> 10
    }

    val label: String
        get() = when (Prefs.perf) {
            "minimal" -> "Minimal: no animations, smallest cache"
            "lightweight" -> "Lightweight: short animations, smaller cache"
            else -> "Balanced: normal animations and cache"
        }
}
