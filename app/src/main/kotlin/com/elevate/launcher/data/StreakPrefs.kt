package com.elevate.launcher.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight local-only streak tracker (SharedPreferences, no DB) for the Wellness Hub.
 * Nothing here leaves the device.
 */
class StreakPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("wellness_streaks", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun today(): String = dayFormat.format(Date())

    fun isDoneToday(itemId: String): Boolean =
        prefs.getString(lastDoneKey(itemId), null) == today()

    fun markDoneToday(itemId: String) {
        val last = prefs.getString(lastDoneKey(itemId), null)
        val todayStr = today()
        if (last == todayStr) return

        val currentStreak = prefs.getInt(streakKey(itemId), 0)
        val newStreak = if (isYesterday(last)) currentStreak + 1 else 1

        prefs.edit()
            .putString(lastDoneKey(itemId), todayStr)
            .putInt(streakKey(itemId), newStreak)
            .apply()
    }

    fun getStreak(itemId: String): Int = prefs.getInt(streakKey(itemId), 0)

    private fun isYesterday(lastDoneStr: String?): Boolean {
        if (lastDoneStr == null) return false
        return try {
            val last = dayFormat.parse(lastDoneStr) ?: return false
            val diffMillis = Date().time - last.time
            val diffDays = diffMillis / (24 * 60 * 60 * 1000)
            diffDays in 0..1
        } catch (_: Exception) {
            false
        }
    }

    private fun lastDoneKey(itemId: String) = "last_done_$itemId"
    private fun streakKey(itemId: String) = "streak_$itemId"
}
