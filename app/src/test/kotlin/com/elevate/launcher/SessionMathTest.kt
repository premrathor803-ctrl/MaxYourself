package com.elevate.launcher

import com.elevate.launcher.gaming.SessionMath
import com.elevate.launcher.gaming.Span
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SessionMathTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long {
        val c = Calendar.getInstance(utc)
        c.clear()
        c.set(y, mo - 1, d, h, mi, 0)
        return c.timeInMillis
    }

    @Test fun durationIsNeverNegative() {
        assertEquals(0L, SessionMath.durationMs(1000L, 500L))
        assertEquals(60_000L, SessionMath.durationMs(0L, 60_000L))
    }

    @Test fun labels() {
        assertEquals("0 min", SessionMath.label(0))
        assertEquals("under 1 min", SessionMath.label(20_000))
        assertEquals("1 min", SessionMath.label(45_000))
        assertEquals("45 min", SessionMath.label(45 * 60_000L))
        assertEquals("1 h", SessionMath.label(60 * 60_000L))
        assertEquals("1 h 30 min", SessionMath.label(90 * 60_000L))
    }

    @Test fun lastDaysAreConsecutiveAndEndToday() {
        val now = at(2026, 10, 28, 15, 30)
        val days = SessionMath.lastDays(now, 7, utc)
        assertEquals(7, days.size)
        assertEquals(at(2026, 10, 22, 0, 0), days.first())
        assertEquals(at(2026, 10, 28, 0, 0), days.last())
    }

    @Test fun sessionOverMidnightCountsOnBothDays() {
        val days = SessionMath.lastDays(at(2026, 10, 28, 12, 0), 2, utc)
        // 23:30 on the 27th until 00:45 on the 28th
        val spans = listOf(Span(at(2026, 10, 27, 23, 30), at(2026, 10, 28, 0, 45)))
        val per = SessionMath.perDay(spans, days, utc)
        assertEquals(30 * 60_000L, per[0])
        assertEquals(45 * 60_000L, per[1])
    }

    @Test fun totalsAddUp() {
        val days = SessionMath.lastDays(at(2026, 10, 28, 12, 0), 7, utc)
        val spans = listOf(
            Span(at(2026, 10, 28, 9, 0), at(2026, 10, 28, 10, 0)),
            Span(at(2026, 10, 28, 20, 0), at(2026, 10, 28, 20, 20)),
            Span(at(2026, 10, 25, 18, 0), at(2026, 10, 25, 19, 30))
        )
        val per = SessionMath.perDay(spans, days, utc)
        assertEquals(80 * 60_000L, per[6])
        assertEquals(90 * 60_000L, per[3])
        assertEquals(170 * 60_000L, per.sum())
    }

    @Test fun backwardSpanAddsNothing() {
        val days = SessionMath.lastDays(at(2026, 10, 28, 12, 0), 1, utc)
        val per = SessionMath.perDay(listOf(Span(2000L, 1000L)), days, utc)
        assertEquals(0L, per[0])
    }
}
