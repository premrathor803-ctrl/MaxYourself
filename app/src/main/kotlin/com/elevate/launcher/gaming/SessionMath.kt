package com.elevate.launcher.gaming

import java.util.Calendar
import java.util.TimeZone

/** One stretch of play time, in epoch milliseconds. */
class Span(val start: Long, val end: Long)

/**
 * Plain calculations for play-session totals. No Android classes are used, so these can be unit-tested on an ordinary
 * JVM and reused anywhere.
 */
object SessionMath {
    fun durationMs(start: Long, end: Long): Long = if (end > start) end - start else 0L

    /** Start of the local day that contains [ms]. */
    fun dayStart(ms: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val c = Calendar.getInstance(zone)
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** Start times of the last [count] local days, oldest first, ending with the day that contains [now]. */
    fun lastDays(now: Long, count: Int, zone: TimeZone = TimeZone.getDefault()): List<Long> {
        val out = ArrayList<Long>(count)
        val c = Calendar.getInstance(zone)
        c.timeInMillis = dayStart(now, zone)
        c.add(Calendar.DAY_OF_YEAR, -(count - 1))
        for (i in 0 until count) {
            out.add(c.timeInMillis)
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return out
    }

    /** Milliseconds played on each of the given days. A session that crosses midnight counts towards both days. */
    fun perDay(spans: List<Span>, dayStarts: List<Long>, zone: TimeZone = TimeZone.getDefault()): LongArray {
        val res = LongArray(dayStarts.size)
        for (i in dayStarts.indices) {
            val s = dayStarts[i]
            val c = Calendar.getInstance(zone)
            c.timeInMillis = s
            c.add(Calendar.DAY_OF_YEAR, 1)
            val e = c.timeInMillis
            for (sp in spans) {
                val lo = if (sp.start > s) sp.start else s
                val hi = if (sp.end < e) sp.end else e
                if (hi > lo) res[i] += hi - lo
            }
        }
        return res
    }

    /** "45 min", "1 h", "1 h 30 min", "under 1 min". Rounded to the nearest minute. */
    fun label(ms: Long): String {
        if (ms <= 0L) return "0 min"
        val m = ((ms + 30_000L) / 60_000L).toInt()
        if (m <= 0) return "under 1 min"
        val h = m / 60
        val r = m % 60
        return if (h == 0) "$m min" else if (r == 0) "$h h" else "$h h $r min"
    }
}
