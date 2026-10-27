package com.elevate.launcher.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.elevate.launcher.R
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.center.ui.CenterActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Elevate's own widget: habits done today and the workout plan. Tapping it opens the Center. It works on any launcher. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val rv = build(context)
        for (id in appWidgetIds) manager.updateAppWidget(id, rv)
    }

    companion object {
        /** Redraws every placed copy of the widget. Cheap; does nothing if the widget is not placed. */
        fun refresh(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayWidget::class.java))
                if (ids.isEmpty()) return
                val rv = build(ctx)
                for (id in ids) mgr.updateAppWidget(id, rv)
            } catch (t: Throwable) {
                android.util.Log.w("Elevate", "widget refresh failed", t)
            }
        }

        fun build(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_today)
            var habits = "Open Elevate to set up habits"
            var workout = ""
            try {
                Center.init(ctx)
                val today = Days.today()
                rv.setTextViewText(R.id.w_title, "Today · " + SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date()))
                val all = HabitRepo.all()
                habits = if (all.isEmpty()) {
                    "No habits yet"
                } else {
                    val due = HabitRepo.dueToday()
                    if (due.isEmpty()) "No habits due today"
                    else "${due.count { HabitRepo.stateToday(it) == HState.COMPLETED }} of ${due.size} habits done"
                }
                workout = when {
                    WorkoutRepo.completedToday() -> "Workout done"
                    ((Center.trainingDays shr Days.dow(today)) and 1) == 1 -> "Workout planned today"
                    else -> "Rest day"
                }
            } catch (t: Throwable) {
                android.util.Log.w("Elevate", "widget data failed", t)
            }
            rv.setTextViewText(R.id.w_habits, habits)
            rv.setTextViewText(R.id.w_workout, workout)
            val open = Intent(ctx, CenterActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(ctx, 4001, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            rv.setOnClickPendingIntent(R.id.w_root, pi)
            return rv
        }
    }
}
