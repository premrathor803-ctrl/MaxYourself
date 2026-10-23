package com.elevate.launcher.ui.center

/** Lets app search also find Center features ("posture", "workout"...). Filled in with the Center. */
object CenterIndex {
    class Hit(val title: String, val sub: String, val section: String)

    fun search(q: String): List<Hit> = emptyList()
}
