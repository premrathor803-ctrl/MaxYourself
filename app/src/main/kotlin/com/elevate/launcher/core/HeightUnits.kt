package com.elevate.launcher.core

import java.util.Locale

/**
 * Height unit conversions. Heights are always stored in centimetres and converted only for display and entry.
 * No Android classes are used here, so the maths can be unit-tested on an ordinary JVM.
 */
object HeightUnits {
    const val CM_PER_INCH = 2.54
    const val MIN_CM = 50.0
    const val MAX_CM = 260.0

    fun feetInchesToCm(feet: Double, inches: Double): Double = (feet * 12.0 + inches) * CM_PER_INCH

    /** Whole feet and inches rounded to one decimal. 5 ft 11.96 in rounds up to 6 ft 0 in. */
    fun cmToFeetInches(cm: Double): Pair<Int, Double> {
        val tenths = Math.round(cm / CM_PER_INCH * 10.0)
        val feet = (tenths / 120L).toInt()
        val inches = (tenths % 120L) / 10.0
        return feet to inches
    }

    fun formatCm(cm: Double): String = String.format(Locale.US, "%.1f cm", cm)

    fun formatFeetInches(cm: Double): String {
        val (f, i) = cmToFeetInches(cm)
        return String.format(Locale.US, "%d ft %.1f in", f, i)
    }

    fun format(cm: Double, feetInches: Boolean): String = if (feetInches) formatFeetInches(cm) else formatCm(cm)

    /** A signed difference such as "+1.2 cm" or "-0.5 in". */
    fun formatDiff(diffCm: Double, feetInches: Boolean): String =
        if (feetInches) String.format(Locale.US, "%+.1f in", diffCm / CM_PER_INCH) else String.format(Locale.US, "%+.1f cm", diffCm)

    fun isPlausibleCm(cm: Double): Boolean = cm in MIN_CM..MAX_CM
}
