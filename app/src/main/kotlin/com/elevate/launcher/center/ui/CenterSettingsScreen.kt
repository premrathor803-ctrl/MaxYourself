package com.elevate.launcher.center.ui

import android.view.View
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

class CenterSettingsScreen(host: CenterActivity) : Screen(host) {
    override val title = "Center settings"

    override fun build(): View {
        val col = host.vbox()

        col.tapCard("Reminders", if (com.elevate.launcher.reminders.Reminders.enabled) "On" else "Off. Optional nudges for habits and workouts.") { host.openSection("reminders") }

        col.heading("Workouts", 10)
        col.trainingPrefsCard()
        col.card {
            cardTitle("Timers")
            para("When on, timed exercises count down for you and rest periods run on a timer.", sp = 13f)
            addView(host.chips(listOf("1" to "On", "0" to "Off"), if (Center.useTimers) "1" else "0") { Center.useTimers = it == "1" }, lp(MATCH, WRAP))
            gap(10)
            cardTitle("Sound cues")
            para("A short beep for the last three seconds and when a timer ends. Uses your media volume.", sp = 13f)
            addView(host.chips(listOf("1" to "On", "0" to "Off"), if (Center.audioCues) "1" else "0") { Center.audioCues = it == "1" }, lp(MATCH, WRAP))
        }

        col.heading("Optional trackers", 10)
        col.card {
            cardTitle("Height tracker")
            addView(host.chips(listOf("1" to "On", "0" to "Off"), if (Center.moduleOn("height", false)) "1" else "0") { Center.setModule("height", it == "1") }, lp(MATCH, WRAP))
            gap(10)
            cardTitle("Sleep log")
            addView(host.chips(listOf("1" to "On", "0" to "Off"), if (Center.moduleOn("sleep", false)) "1" else "0") { Center.setModule("sleep", it == "1") }, lp(MATCH, WRAP))
            gap(10)
            cardTitle("Height units")
            addView(host.chips(listOf("cm" to "Centimetres", "ftin" to "Feet and inches"), Center.heightUnit) { Center.heightUnit = it }, lp(MATCH, WRAP))
        }

        col.heading("Your data", 10)
        col.para("Everything in the Center is stored only on this phone. There are no accounts and nothing is uploaded. Each set of records can be deleted on its own.", sp = 13f)
        val items = listOf(
            Triple("habits", "Delete habits and their history", "All habits and every tick, skip and miss recorded for them."),
            Triple("habit_history", "Delete habit history only", "Keeps your habits but clears all recorded days."),
            Triple("workouts", "Delete workout history", "Every completed or ended workout, and any workout in progress."),
            Triple("measures", "Delete height readings", "All recorded heights."),
            Triple("sleep", "Delete sleep log", "All logged nights."),
            Triple("goals", "Delete goals", "Goals and their progress entries."),
            Triple("care", "Delete product reaction log", "All reaction notes."),
            Triple("photos", "Delete journal photos", "Every photo in the Photo journal, including the picture files."),
            Triple("all", "Delete everything in the Center", "All of the above, plus your Center settings.")
        )
        for ((key, label, detail) in items) {
            col.tapCard(label, detail, "") {
                Dialogs.confirm(host, label + "?", "$detail This cannot be undone.", "Delete") {
                    Center.wipe(key)
                    host.toast("Deleted")
                    host.refresh()
                }
            }
        }

        col.heading("About", 12)
        col.noteCard("Included: habits with optional reminders, guided workouts with exercise pictures, grooming and skincare guides, posture and mobility, optional height and sleep logs, goals, and progress charts. All content works offline.")
        col.noteCard("Photo journal: pictures you take stay in Elevate's private storage on this phone. Nothing rates or scores your looks, and nothing here promises to change your height or face shape.")
        col.noteCard("General fitness and wellness information. It is not medical advice. If something worries you, talk to a doctor.")
        return col
    }
}
