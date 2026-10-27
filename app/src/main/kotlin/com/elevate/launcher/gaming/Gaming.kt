package com.elevate.launcher.gaming

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.elevate.launcher.R
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.data.AppCatalog
import com.elevate.launcher.data.AppEntry

/**
 * Gaming mode. What it really does: pauses Elevate's own reminders, optionally switches Android's Do Not Disturb to
 * "priority only" (if the person allowed that access), keeps chosen games one tap away, and can nudge a break.
 * It does not speed games up, free memory or change graphics, and it says so on its screen.
 */
object Gaming {
    const val ACTION_BREAK = "com.elevate.launcher.GAMING_BREAK"
    private const val CHANNEL = "gaming"
    private const val BREAK_NOTIF_ID = 5001
    private const val BREAK_REQ = 5002

    val on: Boolean get() = Prefs.sp.getBoolean("gaming_on", false)

    var muteOthers: Boolean
        get() = Prefs.sp.getBoolean("gaming_mute", false)
        set(v) = Prefs.putBool("gaming_mute", v)

    /** Minutes between break nudges, 0 = off. */
    var breakMin: Int
        get() = Prefs.getInt("gaming_break", 0)
        set(v) = Prefs.putInt("gaming_break", v)

    val startedAt: Long
        get() = try { Prefs.sp.getLong("gaming_started", 0L) } catch (e: ClassCastException) { 0L }

    var apps: Set<String>
        get() = Prefs.sp.getStringSet("gaming_apps", null)?.toSet() ?: emptySet()
        set(v) = Prefs.sp.edit().putStringSet("gaming_apps", HashSet(v)).apply()

    /** The games to show: the ones the person chose, or every app Elevate sorts under Games if none were chosen. */
    fun games(): List<AppEntry> {
        val chosen = apps
        val list = if (chosen.isEmpty()) AppCatalog.visible().filter { it.group == AppCatalog.GAMES }
        else AppCatalog.visible().filter { it.key in chosen }
        return list.sortedBy { it.displayLabel.lowercase() }
    }

    fun dndAllowed(ctx: Context): Boolean =
        try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted } catch (t: Throwable) { false }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        Prefs.sp.edit().putBoolean("gaming_on", true).putLong("gaming_started", System.currentTimeMillis()).commit()
        if (muteOthers && dndAllowed(app) && !Prefs.sp.getBoolean("gaming_dnd_set", false)) {
            try {
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val prev = nm.currentInterruptionFilter
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                Prefs.sp.edit().putInt("gaming_prev_filter", prev).putBoolean("gaming_dnd_set", true).commit()
            } catch (t: Throwable) {
                android.util.Log.w("Elevate", "could not change Do Not Disturb", t)
            }
        }
        scheduleBreak(app)
    }

    fun stop(ctx: Context) {
        val app = ctx.applicationContext
        Prefs.sp.edit().putBoolean("gaming_on", false).commit()
        if (Prefs.sp.getBoolean("gaming_dnd_set", false)) {
            try {
                val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val prev = Prefs.getInt("gaming_prev_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
                val ok = prev in NotificationManager.INTERRUPTION_FILTER_ALL..NotificationManager.INTERRUPTION_FILTER_ALARMS
                if (nm.isNotificationPolicyAccessGranted) nm.setInterruptionFilter(if (ok) prev else NotificationManager.INTERRUPTION_FILTER_ALL)
            } catch (t: Throwable) {
                android.util.Log.w("Elevate", "could not restore Do Not Disturb", t)
            }
            Prefs.sp.edit().putBoolean("gaming_dnd_set", false).commit()
        }
        cancelBreak(app)
    }

    // ------------------------------------------------------------ break nudges
    private fun breakIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, BREAK_REQ, Intent(ctx, GamingReceiver::class.java).setAction(ACTION_BREAK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun scheduleBreak(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (!on || breakMin <= 0) { am.cancel(breakIntent(app)); return }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + breakMin * 60_000L, breakIntent(app))
    }

    private fun cancelBreak(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(breakIntent(ctx))
        NotificationManagerCompat.from(ctx).cancel(BREAK_NOTIF_ID)
    }

    fun onBreak(ctx: Context) {
        if (!on) return
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null) {
                val ch = NotificationChannel(CHANNEL, "Gaming breaks", NotificationManager.IMPORTANCE_LOW)
                ch.description = "A gentle nudge to take a short break while Gaming mode is on."
                nm.createNotificationChannel(ch)
            }
        }
        val mins = ((System.currentTimeMillis() - startedAt) / 60000L).toInt().coerceAtLeast(1)
        val open = PendingIntent.getActivity(ctx, BREAK_REQ, Intent(ctx, GamingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Time for a short break?")
            .setContentText("Gaming mode has been on for $mins min. Stretch, drink water, rest your eyes.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) NotificationManagerCompat.from(ctx).notify(BREAK_NOTIF_ID, n)
        } catch (t: SecurityException) {
            android.util.Log.w("Elevate", "break notification blocked", t)
        }
        scheduleBreak(ctx)
    }
}

class GamingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Gaming.ACTION_BREAK) {
            try { Gaming.onBreak(context) } catch (t: Throwable) { android.util.Log.e("Elevate", "break nudge failed", t) }
        }
    }
}

/** Quick Settings tile that switches Gaming mode on and off. */
class GamingTile : TileService() {
    override fun onStartListening() { update() }

    override fun onClick() {
        if (Gaming.on) Gaming.stop(this) else Gaming.start(this)
        update()
    }

    private fun update() {
        val t = qsTile ?: return
        t.state = if (Gaming.on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Gaming mode"
        t.updateTile()
    }
}
