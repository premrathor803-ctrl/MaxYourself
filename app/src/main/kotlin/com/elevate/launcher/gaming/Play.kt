package com.elevate.launcher.gaming

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elevate.launcher.R
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.Io

/**
 * Tracks one play session at a time, and only from what Elevate can really see.
 *
 * What is known: the moment a game was opened from Elevate. What is not known: when you stop playing, because Android does
 * not tell a launcher that. So a session ends when you come back to Elevate (or open another game from it), and that end
 * time is saved as approximate. Nothing runs in the background while you play: no service, no polling. At most one alarm
 * is set, and only if you asked for a session reminder for that game.
 */
object Play {
    const val ACTION_REMIND = "com.elevate.launcher.GAME_REMIND"

    /** Shorter sessions are treated as an accidental tap and not saved. */
    const val MIN_SAVED_MS = 60_000L

    /** Normally [MIN_SAVED_MS]; the automated emulator test lowers it so it does not have to wait a minute. */
    @Volatile var minSavedMs = MIN_SAVED_MS

    /** Longer ones are not saved either: the real end time cannot be known after that long. */
    const val MAX_SAVED_MS = 8L * 60L * 60_000L

    private const val NOTIF_ID = 5003
    private const val REQ = 5004

    class Active(val key: String, val name: String, val start: Long, val record: Boolean, val autoGaming: Boolean)

    /** The game opened from Elevate that has not been closed out yet, or null. */
    val active: Active?
        get() {
            val sp = Prefs.sp
            val key = sp.getString("play_key", null) ?: return null
            val start = try { sp.getLong("play_start", 0L) } catch (e: ClassCastException) { 0L }
            if (start <= 0L) return null
            return Active(key, sp.getString("play_name", "") ?: "", start, sp.getBoolean("play_record", true), sp.getBoolean("play_auto", false))
        }

    /** Called after Elevate has started an app. Does nothing unless that app is one of the person's games. */
    fun onLaunched(ctx: Context, key: String) {
        try {
            val app = ctx.applicationContext
            if (!Gaming.isGame(key)) return
            val entry = AppCatalog.get(key) ?: return
            if (active != null) finish(app, System.currentTimeMillis(), GameStore.END_REPLACED)
            val profile = GameStore.profile(key)
            var auto = false
            if (profile.autoMode && !Gaming.on) { Gaming.start(app); auto = true }
            Prefs.sp.edit()
                .putString("play_key", key).putString("play_name", entry.displayLabel)
                .putLong("play_start", System.currentTimeMillis())
                .putBoolean("play_record", profile.record).putBoolean("play_auto", auto)
                .commit()
            Gaming.refreshDnd(app)
            scheduleRemind(app, profile.remindMin, System.currentTimeMillis())
        } catch (t: Throwable) {
            android.util.Log.w("Elevate", "could not start tracking a session", t)
        }
    }

    /** Called when Elevate's home or Gaming screen comes back to the front: the person has left the game. */
    fun noteReturn(ctx: Context) {
        val a = active ?: return
        val now = System.currentTimeMillis()
        if (now - a.start < 1500L) return
        finish(ctx, now, GameStore.END_RETURNED)
    }

    /** Ends the open session at [end]. Saves it only if it is a sensible length and recording is on for that game. */
    fun finish(ctx: Context, end: Long, endKind: String) {
        val a = active ?: return
        val app = ctx.applicationContext
        clear(app)
        if (a.record) {
            val ms = SessionMath.durationMs(a.start, end)
            if (ms >= minSavedMs && ms <= MAX_SAVED_MS) {
                Io.write { GameStore.add(a.key, a.name, a.start, end, GameStore.KIND_TRACKED, endKind) }
            } else if (ms > MAX_SAVED_MS) {
                Prefs.putString("play_notice",
                    "A session of ${a.name} lasted more than 8 hours before you came back to Elevate, so it was not saved: " +
                        "Elevate cannot know when you really stopped. You can add it by hand.")
            }
        }
        if (a.autoGaming && Gaming.on) Gaming.stop(app)
        Gaming.refreshDnd(app)
    }

    /** A message about something that was not saved, shown once. */
    fun takeNotice(): String {
        val n = Prefs.sp.getString("play_notice", "") ?: ""
        if (n.isNotEmpty()) Prefs.putString("play_notice", "")
        return n
    }

    private fun clear(ctx: Context) {
        Prefs.sp.edit().remove("play_key").remove("play_name").remove("play_start").remove("play_record").remove("play_auto").commit()
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(remindIntent(ctx))
            NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
        } catch (t: Throwable) {
            android.util.Log.w("Elevate", "could not cancel session reminder", t)
        }
    }

    /** After a restart nothing can still be running: forget the open session without saving it, and give Do Not Disturb back. */
    fun clearAfterBoot(ctx: Context) {
        val a = active ?: return
        clear(ctx)
        if (a.record) Prefs.putString("play_notice", "The phone restarted while ${a.name} was open, so that session was not saved.")
        if (a.autoGaming && Gaming.on) Gaming.stop(ctx) else Gaming.refreshDnd(ctx)
    }

    /** After an app update Android drops alarms: set the reminder again for the time that is left. */
    fun restoreAlarm(ctx: Context) {
        val a = active ?: return
        scheduleRemind(ctx, GameStore.profile(a.key).remindMin, a.start)
    }

    // ------------------------------------------------------------ session reminders
    private fun remindIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, REQ, Intent(ctx, GamingReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** One alarm at a time. The first fires [minutes] after [from]; later ones count on from now. */
    private fun scheduleRemind(ctx: Context, minutes: Int, from: Long) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (minutes <= 0) { am.cancel(remindIntent(ctx)); return }
        var at = from + minutes * 60_000L
        val now = System.currentTimeMillis()
        if (at <= now) at = now + minutes * 60_000L
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, remindIntent(ctx))
    }

    fun onRemind(ctx: Context) {
        val a = active ?: return
        val mins = GameStore.profile(a.key).remindMin
        if (mins <= 0) return
        Gaming.ensureChannel(ctx)
        val played = ((System.currentTimeMillis() - a.start) / 60_000L).toInt().coerceAtLeast(1)
        val open = PendingIntent.getActivity(ctx, REQ, Intent(ctx, GamingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, Gaming.CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Still playing " + a.name + "?")
            .setContentText("You opened it from Elevate $played min ago. Time for water, a stretch, or a rest for your eyes?")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (t: SecurityException) {
            android.util.Log.w("Elevate", "session reminder blocked", t)
        }
        scheduleRemind(ctx, mins, System.currentTimeMillis())
    }
}
