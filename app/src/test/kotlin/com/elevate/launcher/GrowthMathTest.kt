package com.elevate.launcher

import com.elevate.launcher.center.data.GrowthMath
import com.elevate.launcher.center.data.GrowthMath.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthMathTest {
    private val today = "2026-10-28"
    private fun p(id: Long, day: String, cm: Double) = Point(id, day, cm)

    private val pts = listOf(
        p(1, "2025-09-01", 160.0), p(2, "2026-04-10", 163.0), p(3, "2026-08-01", 165.0),
        p(4, "2026-10-01", 165.4), p(5, "2026-10-27", 165.6)
    )

    @Test fun rangeFiltersByDate() {
        assertEquals(5, GrowthMath.inRange(pts, "all", today).size)
        assertEquals(2, GrowthMath.inRange(pts, "1m", today).size)    // from 2026-09-27
        assertEquals(3, GrowthMath.inRange(pts, "3m", today).size)    // from 2026-07-28
        assertEquals(4, GrowthMath.inRange(pts, "1y", today).size)    // from 2025-10-27: the 2025-09-01 reading is out
        assertEquals(3, GrowthMath.inRange(pts, "6m", today).size)    // from 2026-04-28: the 2026-04-10 reading is out
    }

    @Test fun trendNeedsEnoughReadingsSpreadOverTime() {
        assertFalse(GrowthMath.enoughForTrend(emptyList()))
        assertFalse(GrowthMath.enoughForTrend(pts.take(2)))
        assertFalse(GrowthMath.enoughForTrend(listOf(p(1, "2026-10-01", 160.0), p(2, "2026-10-05", 160.2), p(3, "2026-10-09", 160.1))))
        assertTrue(GrowthMath.enoughForTrend(pts))
    }

    @Test fun trendNoteNeverClaimsATrendFromFewReadings() {
        assertEquals("", GrowthMath.trendNote(emptyList()))
        assertTrue(GrowthMath.trendNote(pts.take(1)).startsWith("One reading"))
        assertTrue(GrowthMath.trendNote(pts.take(2)).startsWith("Two readings"))
        assertTrue(GrowthMath.trendNote(listOf(p(1, "2026-10-01", 160.0), p(2, "2026-10-05", 160.2), p(3, "2026-10-09", 160.1))).contains("not enough"))
        assertTrue(GrowthMath.trendNote(pts).startsWith("5 readings over"))
    }

    @Test fun nearestAndSuspicious() {
        assertEquals(5L, GrowthMath.nearest(pts, "2026-10-28")!!.id)
        assertEquals(4L, GrowthMath.nearest(pts, "2026-10-05")!!.id)
        assertNull(GrowthMath.nearest(emptyList(), "2026-10-05"))
        // 175.0 cm next to a 165.6 cm reading the day before: likely a slip
        assertNotNull(GrowthMath.suspiciousNeighbour(pts, "2026-10-28", 175.0))
        // 2 cm over two months is ordinary
        assertNull(GrowthMath.suspiciousNeighbour(pts, "2026-12-20", 167.0))
        // a big gap in time allows a bigger change, but not more than 20 cm
        assertNull(GrowthMath.suspiciousNeighbour(pts, "2027-12-20", 172.0))
        assertNotNull(GrowthMath.suspiciousNeighbour(pts, "2027-12-20", 190.0))
        // the reading being edited is not compared with itself
        assertNull(GrowthMath.suspiciousNeighbour(listOf(p(9, "2026-10-28", 165.0)), "2026-10-28", 190.0, ignoreId = 9))
    }

    @Test fun duplicatesAreSpotted() {
        assertTrue(GrowthMath.isDuplicate(pts, "2026-10-27", 165.6))
        assertTrue(GrowthMath.isDuplicate(pts, "2026-10-27", 165.62))
        assertFalse(GrowthMath.isDuplicate(pts, "2026-10-27", 165.8))
        assertFalse(GrowthMath.isDuplicate(pts, "2026-10-26", 165.6))
        assertFalse(GrowthMath.isDuplicate(pts, "2026-10-27", 165.6, ignoreId = 5))
    }

    @Test fun differences() {
        assertEquals(0.4, GrowthMath.diffCm(pts[2], pts[3]), 0.0001)
        assertEquals(0.4, GrowthMath.diffCm(pts[3], pts[2]), 0.0001)   // order of arguments does not matter
        assertEquals(5.6, GrowthMath.diffCm(pts[0], pts[4]), 0.0001)
        assertEquals(5.6, GrowthMath.diffCm(pts[4], pts[0]), 0.0001)
        assertEquals(421, GrowthMath.daysBetween(pts[0], pts[4]))
        assertTrue(GrowthMath.withinMeasuringError(0.6))
        assertFalse(GrowthMath.withinMeasuringError(1.2))
        assertEquals("+1.0 cm", GrowthMath.signed(1.0, false))
        assertEquals("-0.4 in", GrowthMath.signed(-1.0, true))
    }
}
