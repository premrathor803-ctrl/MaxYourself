package com.elevate.launcher.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the reminder alarm and the buttons on a reminder notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val kind = intent.getStringExtra(Reminders.EXTRA_KIND) ?: ""
            val id = intent.getLongExtra(Reminders.EXTRA_ID, 0L)
            val day = intent.getStringExtra(Reminders.EXTRA_DAY) ?: ""
            when (intent.action) {
                Reminders.ACTION_FIRE ->
                    if (intent.getBooleanExtra(Reminders.EXTRA_SNOOZED, false)) Reminders.onSnoozeFire(context, kind, id, day)
                    else Reminders.onFire(context)
                Reminders.ACTION_DONE -> Reminders.markDone(context, id, day)
                Reminders.ACTION_SNOOZE -> Reminders.snooze(context, kind, id, day)
                Reminders.ACTION_TEST -> Reminders.sendTest(context)
            }
        } catch (t: Throwable) {
            android.util.Log.e("Elevate", "reminder receiver failed", t)
        }
    }
}

/** Puts the alarm back after a restart, an app update, or a clock or time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A Gaming mode session does not survive a restart: leave it, and give Do Not Disturb back if we changed it.
        try { if (com.elevate.launcher.gaming.Gaming.on) com.elevate.launcher.gaming.Gaming.stop(context) } catch (t: Throwable) { android.util.Log.e("Elevate", "gaming reset failed", t) }
        try { Reminders.reschedule(context) } catch (t: Throwable) { android.util.Log.e("Elevate", "boot reschedule failed", t) }
    }
}
