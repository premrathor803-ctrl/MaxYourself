package com.elevate.launcher.center.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Calendar days as "yyyy-MM-dd" strings (local time). Avoids java.time so it runs on Android 7. */
object Days {
    private fun fmt() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun today(): String = fmt().format(Date())

    fun cal(day: String): Calendar {
        val c = Calendar.getInstance()
        try { c.time = fmt().parse(day) ?: Date() } catch (e: Exception) { c.time = Date() }
        c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c
    }

    fun of(ms: Long): String = fmt().format(Date(ms))

    fun add(day: String, n: Int): String {
        val c = cal(day)
        c.add(Calendar.DAY_OF_YEAR, n)
        return fmt().format(c.time)
    }

    /** Monday = 0 ... Sunday = 6 */
    fun dow(day: String): Int = (cal(day).get(Calendar.DAY_OF_WEEK) + 5) % 7

    /** Whole days from a to b (b - a). */
    fun diff(a: String, b: String): Int = Math.round((cal(b).timeInMillis - cal(a).timeInMillis) / 86400000.0).toInt()

    fun range(from: String, to: String): List<String> {
        val out = ArrayList<String>()
        var d = from
        var guard = 0
        while (d <= to && guard++ < 4000) { out.add(d); d = add(d, 1) }
        return out
    }

    fun weekStart(day: String): String = add(day, -dow(day))

    fun pretty(day: String): String = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(cal(day).time)
    fun prettyLong(day: String): String = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(cal(day).time)
    fun short(day: String): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(cal(day).time)
    fun dayLetter(day: String): String = listOf("M", "T", "W", "T", "F", "S", "S")[dow(day)]
    fun timeOf(ms: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(ms))

    val dowNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
