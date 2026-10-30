package com.elevate.launcher.center.data

import java.util.Locale

/**
 * Plain calculations for the height tracker. No Android classes are used, so they can be unit-tested on an ordinary JVM.
 * Nothing here predicts height or scores growth: it only filters, compares and describes what the person recorded.
 */
object GrowthMath {
    /** One recorded height. [cm] is always centimetres; units are converted only for display. */
    class Point(val id: Long, val day: String, val cm: Double)

    /** (key, label) for the chart range chips. */
    val ranges = listOf("1m" to "1 mo", "3m" to "3 mo", "6m" to "6 mo", "1y" to "1 yr", "all" to "All")

    fun rangeDays(range: String): Int? = when (range) {
        "1m" -> 31
        "3m" -> 92
        "6m" -> 183
        "1y" -> 366
        else -> null
    }

    /** Readings on or after the start of the range, in their original order. "all" keeps everything. */
    fun inRange(points: List<Point>, range: String, today: String): List<Point> {
        val d = rangeDays(range) ?: return points
        val from = Days.add(today, -d)
        return points.filter { it.day >= from }
    }

    /** A trend needs at least three readings that are at least four weeks apart overall. */
    fun enoughForTrend(points: List<Point>): Boolean =
        points.size >= 3 && Days.diff(points.first().day, points.last().day) >= 28

    /** One honest sentence about how much can be read from these points. Never describes a trend that is not there. */
    fun trendNote(points: List<Point>): String = when {
        points.isEmpty() -> ""
        points.size == 1 -> "One reading so far. A line needs two, and a trend needs three or more taken weeks apart."
        points.size == 2 -> "Two readings show a difference, not a trend."
        !enoughForTrend(points) -> "These readings are close together in time, so there is not enough to describe a trend yet."
        else -> "${points.size} readings over ${Days.diff(points.first().day, points.last().day)} days. Differences under about 1 cm can be measuring error."
    }

    /** The reading that is nearest in date to [day], ignoring the one being edited. */
    fun nearest(points: List<Point>, day: String, ignoreId: Long = -1L): Point? =
        points.filter { it.id != ignoreId }.minByOrNull { Math.abs(Days.diff(it.day, day)) }

    /**
     * Returns the neighbouring reading when a new value looks far from it, so the person can confirm it is not a typing slip:
     * more than 5 cm apart within two months, or more than 20 cm apart at any distance. Otherwise null.
     */
    fun suspiciousNeighbour(points: List<Point>, day: String, cm: Double, ignoreId: Long = -1L): Point? {
        val n = nearest(points, day, ignoreId) ?: return null
        val gap = Math.abs(Days.diff(n.day, day))
        val diff = Math.abs(cm - n.cm)
        return if (diff > 20.0 || (diff > 5.0 && gap < 60)) n else null
    }

    /** The same value on the same day was already recorded: usually a repeated tap. */
    fun isDuplicate(points: List<Point>, day: String, cm: Double, ignoreId: Long = -1L): Boolean =
        points.any { it.id != ignoreId && it.day == day && Math.abs(it.cm - cm) < 0.05 }

    /** Difference between two readings, in centimetres, later minus earlier (order given by date, not by argument order). */
    fun diffCm(a: Point, b: Point): Double = if (a.day <= b.day) b.cm - a.cm else a.cm - b.cm

    /** Whole days between two readings, always zero or more. */
    fun daysBetween(a: Point, b: Point): Int = Math.abs(Days.diff(a.day, b.day))

    /** Is a difference small enough that ordinary measuring error could explain it? */
    fun withinMeasuringError(diffCm: Double): Boolean = Math.abs(diffCm) < 1.0

    /** The note shown under a difference between two readings. */
    fun diffNote(diffCm: Double): String =
        if (withinMeasuringError(diffCm)) "This is within ordinary measuring error (about 1 cm), so it does not show growth."
        else "This is a difference between two readings. Time of day, shoes, posture and technique change a reading too, so it is not proof of growth."

    /** "+1.2 cm" in centimetres, or the same difference in inches. */
    fun signed(diffCm: Double, feetInches: Boolean): String =
        if (feetInches) String.format(Locale.US, "%+.1f in", diffCm / 2.54) else String.format(Locale.US, "%+.1f cm", diffCm)
}
