package com.elevate.launcher.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elevate.launcher.R
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.center.ui.CenterActivity
import com.elevate.launcher.core.Prefs
import java.util.Calendar

/**
 * Optional reminders for habits and workouts. Everything is off until the person turns it on.
 *
 * How it works: one inexact alarm is always set for the next reminder that is due. When it fires, the reminders
 * whose time has passed since the last check are shown (unless they are paused, in quiet hours, or already done),
 * and the next alarm is set. Android may delay inexact alarms to save battery, so reminders are approximate.
 */
object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_FIRE = "com.elevate.launcher.REMINDER_FIRE"
    const val ACTION_DONE = "com.elevate.launcher.REMINDER_DONE"
    const val ACTION_SNOOZE = "com.elevate.launcher.REMINDER_SNOOZE"
    const val ACTION_TEST = "com.elevate.launcher.REMINDER_TEST"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ID = "id"
    const val EXTRA_DAY = "day"
    const val EXTRA_SNOOZED = "snoozed"

    private const val SNOOZE_MS = 30 * 60 * 1000L
    private const val MAX_REPLAY_MS = 3 * 60 * 60 * 1000L

    // ------------------------------------------------------------ settings (stored with the Center's settings)
    var enabled: Boolean
        get() = Center.sp.getBoolean("rem_on", false)
        set(v) = Center.sp.edit().putBoolean("rem_on", v).apply()
    var quietOn: Boolean
        get() = Center.sp.getBoolean("rem_quiet_on", false)
        set(v) = Center.sp.edit().putBoolean("rem_quiet_on", v).apply()
    var quietStart: String
        get() = Center.sp.getString("rem_quiet_start", "22:00") ?: "22:00"
        set(v) = Center.sp.edit().putString("rem_quiet_start", v).apply()
    var quietEnd: String
        get() = Center.sp.getString("rem_quiet_end", "07:00") ?: "07:00"
        set(v) = Center.sp.edit().putString("rem_quiet_end", v).apply()
    var pausedUntil: Long
        get() = Center.sp.getLong("rem_paused_until", 0L)
        set(v) = Center.sp.edit().putLong("rem_paused_until", v).apply()
    /** "HH:mm" or "" for no workout reminder. It repeats on the training days set in the Workouts screen. */
    var workoutTime: String
        get() = Center.sp.getString("rem_workout", "") ?: ""
        set(v) = Center.sp.edit().putString("rem_workout", v).apply()
    private var lastChecked: Long
        get() = Center.sp.getLong("rem_last", 0L)
        set(v) = Center.sp.edit().putLong("rem_last", v).apply()

    fun isPaused(now: Long = System.currentTimeMillis()): Boolean = pausedUntil > now

    fun allowed(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Call when the person turns reminders on. */
    fun turnOn(ctx: Context) {
        enabled = true
        lastChecked = System.currentTimeMillis()
        ensureChannel(ctx)
        reschedule(ctx)
    }

    fun turnOff(ctx: Context) {
        enabled = false
        reschedule(ctx)
    }

    // ------------------------------------------------------------ what is due
    class Item(val kind: String, val id: Long, val time: String, val title: String, val text: String)

    fun itemsFor(day: String): List<Item> {
        val out = ArrayList<Item>()
        for (h in HabitRepo.all()) {
            if (h.remind.isNotEmpty() && HabitRepo.scheduled(h, day)) {
                val text = if (h.target.isNotEmpty()) h.target else "Time for this habit."
                out.add(Item("habit", h.id, h.remind, h.name, text))
            }
        }
        val wt = workoutTime
        if (wt.isNotEmpty() && ((Center.trainingDays shr Days.dow(day)) and 1) == 1) {
            out.add(Item("workout", 0L, wt, "Workout time", "A session is planned for today. Skip it if you are not up for it."))
        }
        return out
    }

    private fun millis(day: String, hhmm: String): Long {
        val p = hhmm.split(":")
        val c = Days.cal(day)
        c.set(Calendar.HOUR_OF_DAY, (p.getOrNull(0)?.toIntOrNull() ?: 9).coerceIn(0, 23))
        c.set(Calendar.MINUTE, (p.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 59))
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun minutesOf(hhmm: String): Int {
        val p = hhmm.split(":")
        return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    fun inQuiet(now: Long = System.currentTimeMillis()): Boolean {
        if (!quietOn) return false
        val c = Calendar.getInstance()
        c.timeInMillis = now
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val a = minutesOf(quietStart)
        val b = minutesOf(quietEnd)
        return when {
            a == b -> false
            a < b -> m in a until b
            else -> m >= a || m < b
        }
    }

    /** The earliest reminder time strictly after [after], looking up to eight days ahead. */
    fun nextTrigger(after: Long): Long? {
        val today = Days.today()
        for (off in -1..8) {
            val day = Days.add(today, off)
            var best: Long? = null
            for (it in itemsFor(day)) {
                val t = millis(day, it.time)
                if (t > after && (best == null || t < best)) best = t
            }
            if (best != null) return best
        }
        return null
    }

    // ------------------------------------------------------------ alarms
    private fun firePending(ctx: Context): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(ctx, 7001, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Sets (or clears) the single alarm for the next due reminder. Safe to call as often as needed. */
    fun reschedule(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = firePending(app)
            am.cancel(pi)
            if (!enabled) return
            val now = System.currentTimeMillis()
            val from = maxOf(lastChecked, now - MAX_REPLAY_MS)
            val next = nextTrigger(from) ?: return
            // A reminder that came due while the alarm was cancelled or delayed is handled almost at once.
            val at = if (next <= now) now + 1000L else next
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (t: Throwable) {
            android.util.Log.w("Elevate", "could not schedule reminder", t)
        }
    }

    /** Called when the alarm fires. */
    fun onFire(ctx: Context, now: Long = System.currentTimeMillis()) {
        if (enabled) {
            val from = maxOf(lastChecked, now - MAX_REPLAY_MS)
            val today = Days.today()
            for (day in listOf(Days.add(today, -1), today)) {
                for (it in itemsFor(day)) {
                    val t = millis(day, it.time)
                    if (t > from && t <= now) post(ctx, it, day, now)
                }
            }
        }
        lastChecked = now
        reschedule(ctx)
    }

    private fun suppressed(now: Long): Boolean =
        isPaused(now) || inQuiet(now) || Prefs.sp.getBoolean("gaming_on", false)

    private fun alreadyDone(it: Item, day: String): Boolean {
        if (it.kind == "workout") return WorkoutRepo.completedCount(day, day) > 0
        val h = HabitRepo.get(it.id) ?: return true
        if (h.archived) return true
        val st = HabitRepo.stateOf(h, day, HabitRepo.logs(h.id, day, day), Days.today(), WorkoutRepo.completedDays(day, day))
        return st == HState.COMPLETED || st == HState.SKIPPED || st == HState.NOT_SCHEDULED
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
                ch.description = "Habit and workout reminders you turned on"
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun notifId(kind: String, id: Long): Int = if (kind == "workout") 2 else 1000 + id.toInt()

    private fun actionPending(ctx: Context, action: String, kind: String, id: Long, day: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(action)
            .putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id).putExtra(EXTRA_DAY, day)
        val code = notifId(kind, id) * 4 + (if (action == ACTION_DONE) 1 else 2)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun post(ctx: Context, it: Item, day: String, now: Long, force: Boolean = false) {
        if (!force && (suppressed(now) || alreadyDone(it, day))) return
        show(ctx, it.kind, it.id, it.title, it.text, day, true)
    }

    private fun show(ctx: Context, kind: String, id: Long, title: String, text: String, day: String, actions: Boolean) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(ctx)
        val open = Intent(ctx, CenterActivity::class.java)
            .putExtra(CenterActivity.EXTRA_SECTION, if (kind == "workout") "workouts" else "habits")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentPi = PendingIntent.getActivity(
            ctx, notifId(kind, id) * 4, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPi)
        if (actions) {
            if (kind == "habit") b.addAction(0, "Done", actionPending(ctx, ACTION_DONE, kind, id, day))
            b.addAction(0, "Snooze 30 min", actionPending(ctx, ACTION_SNOOZE, kind, id, day))
        }
        try {
            nm.notify(notifId(kind, id), b.build())
        } catch (t: SecurityException) {
            android.util.Log.w("Elevate", "notification not allowed", t)
        }
    }

    // ------------------------------------------------------------ actions from the notification
    fun markDone(ctx: Context, id: Long, day: String) {
        HabitRepo.setState(id, day, HabitRepo.LOG_DONE)
        NotificationManagerCompat.from(ctx).cancel(notifId("habit", id))
    }

    fun snooze(ctx: Context, kind: String, id: Long, day: String) {
        NotificationManagerCompat.from(ctx).cancel(notifId(kind, id))
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra(EXTRA_SNOOZED, true).putExtra(EXTRA_KIND, kind).putExtra(EXTRA_ID, id).putExtra(EXTRA_DAY, day)
        val pi = PendingIntent.getBroadcast(
            ctx, 8000 + notifId(kind, id), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + SNOOZE_MS, pi)
    }

    /** A snoozed reminder comes back. */
    fun onSnoozeFire(ctx: Context, kind: String, id: Long, day: String) {
        val now = System.currentTimeMillis()
        if (!enabled) return
        val it = if (kind == "workout") Item("workout", 0L, workoutTime, "Workout time", "A session is planned for today. Skip it if you are not up for it.")
        else HabitRepo.get(id)?.let { h -> Item("habit", h.id, h.remind, h.name, if (h.target.isNotEmpty()) h.target else "Time for this habit.") }
        if (it != null) post(ctx, it, day, now)
    }

    /** Shows a sample reminder right now so the person can see how they look. */
    fun sendTest(ctx: Context) {
        show(ctx, "test", 0L, "Test reminder", "This is how your reminders will look.", Days.today(), false)
    }
}
