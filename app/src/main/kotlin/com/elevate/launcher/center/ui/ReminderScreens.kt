package com.elevate.launcher.center.ui

import android.content.Intent
import android.provider.Settings
import android.view.View
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.reminders.Reminders

/** Turn reminders on or off and choose when they appear. Everything here is optional. */
class RemindersScreen(host: CenterActivity) : Screen(host) {
    override val title = "Reminders"
    override fun onResumeScreen() { host.refresh() }

    private fun dayCount(): Int = (0..6).count { (Center.trainingDays shr it) and 1 == 1 }

    override fun build(): View {
        val col = host.vbox()
        col.para("Reminders are off until you turn them on. They are shown as notifications on this phone. Nothing is sent anywhere.", sp = 13.5f)

        col.card {
            cardTitle("Reminders")
            addView(host.chips(listOf("1" to "On", "0" to "Off"), if (Reminders.enabled) "1" else "0") { v ->
                if (v == "1") {
                    host.askNotificationPermission { _ ->
                        Reminders.turnOn(host)
                        host.refresh()
                    }
                } else {
                    Reminders.turnOff(host)
                    host.refresh()
                }
            }, lp(MATCH, WRAP))
        }

        if (Reminders.enabled && !Reminders.allowed(host)) {
            col.noteCard("Android is blocking notifications for Elevate, so reminders cannot appear yet. Allow notifications for Elevate in your phone's settings.")
            col.actionButton("Open notification settings", false) {
                val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, host.packageName)
                try { host.startActivity(i) } catch (e: Exception) { host.toast("Could not open the settings screen") }
            }
        }

        if (Reminders.enabled) {
            col.actionButton("Send a test reminder", false) {
                if (Reminders.allowed(host)) {
                    Reminders.sendTest(host)
                    host.toast("Test reminder sent")
                } else {
                    host.toast("Notifications are blocked for Elevate")
                }
            }

            // ---- workout
            col.heading("Workout reminder", 10)
            val wt = Reminders.workoutTime
            col.pickRow("Time", if (wt.isEmpty()) "Off" else clockText(wt)) {
                Dialogs.list(host, "Workout reminder", listOf("Set a time", "Turn off")) { i ->
                    if (i == 0) host.pickTime(if (wt.isEmpty()) "18:00" else wt) { t ->
                        Reminders.workoutTime = t
                        Reminders.reschedule(host)
                        host.refresh()
                    } else {
                        Reminders.workoutTime = ""
                        Reminders.reschedule(host)
                        host.refresh()
                    }
                }
            }
            col.para(
                if (dayCount() == 0) "No training days are set, so this will not fire. Choose training days in Workouts."
                else "Repeats on your ${dayCount()} training days. Change them in Workouts. It is skipped if you already finished a workout that day.",
                sp = 12.5f
            )

            // ---- habits
            col.heading("Habit reminders", 10)
            val habits = HabitRepo.all()
            if (habits.isEmpty()) {
                col.para("You have no habits yet. Add one in Habits, then come back to give it a time.", sp = 13f)
            } else {
                for (h in habits) {
                    val sub = if (h.remind.isEmpty()) "No reminder" else clockText(h.remind) + " · " + HabitRepo.describeSchedule(h)
                    col.tapCard(h.name, sub, "Set") {
                        Dialogs.list(host, h.name, listOf("Set a time", "Remove reminder")) { i ->
                            if (i == 0) host.pickTime(if (h.remind.isEmpty()) "08:00" else h.remind) { t ->
                                h.remind = t
                                HabitRepo.update(h)
                                Reminders.reschedule(host)
                                host.refresh()
                            } else {
                                h.remind = ""
                                HabitRepo.update(h)
                                Reminders.reschedule(host)
                                host.refresh()
                            }
                        }
                    }
                }
            }

            // ---- quiet hours
            col.heading("Quiet hours", 10)
            col.card {
                para("Reminders that come due during quiet hours are skipped, not saved for later.", sp = 12.5f)
                addView(host.chips(listOf("1" to "On", "0" to "Off"), if (Reminders.quietOn) "1" else "0") { v ->
                    Reminders.quietOn = v == "1"
                    host.refresh()
                }, lp(MATCH, WRAP))
                if (Reminders.quietOn) {
                    gap(8)
                    pickRow("From", clockText(Reminders.quietStart)) {
                        host.pickTime(Reminders.quietStart) { t -> Reminders.quietStart = t; host.refresh() }
                    }
                    pickRow("Until", clockText(Reminders.quietEnd)) {
                        host.pickTime(Reminders.quietEnd) { t -> Reminders.quietEnd = t; host.refresh() }
                    }
                }
            }

            // ---- pause
            col.heading("Pause", 10)
            col.card {
                val now = System.currentTimeMillis()
                if (Reminders.isPaused(now)) {
                    para("Paused until " + Days.pretty(Days.of(Reminders.pausedUntil)) + ".", dim = false, sp = 14f)
                    actionButton("Resume reminders now", false) { Reminders.pausedUntil = 0L; host.refresh() }
                } else {
                    para("Take a break without losing your settings.", sp = 12.5f)
                    addView(host.chips(listOf("0" to "Not paused", "1" to "1 day", "3" to "3 days", "7" to "7 days"), "0") { v ->
                        val d = v.toInt()
                        if (d > 0) {
                            Reminders.pausedUntil = System.currentTimeMillis() + d * 86400000L
                            host.refresh()
                        }
                    }, lp(MATCH, WRAP))
                }
            }

        }

        col.noteCard("Reminders are approximate. To save battery, Android may show them a few minutes late, and some phones delay them more when battery saver is on. If they never arrive, check that Elevate is not restricted in your phone's battery settings. Reminders are not medical or safety alerts.")
        return col
    }
}
