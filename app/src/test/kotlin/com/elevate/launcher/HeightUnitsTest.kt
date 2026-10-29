package com.elevate.launcher

import com.elevate.launcher.core.HeightUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeightUnitsTest {
    @Test fun feetInchesToCentimetres() {
        assertEquals(182.88, HeightUnits.feetInchesToCm(6.0, 0.0), 0.001)
        assertEquals(170.18, HeightUnits.feetInchesToCm(5.0, 7.0), 0.001)
        assertEquals(152.4, HeightUnits.feetInchesToCm(0.0, 60.0), 0.001)
    }

    @Test fun centimetresToFeetInches() {
        val (f, i) = HeightUnits.cmToFeetInches(170.18)
        assertEquals(5, f)
        assertEquals(7.0, i, 0.001)
        val (f2, i2) = HeightUnits.cmToFeetInches(182.88)
        assertEquals(6, f2)
        assertEquals(0.0, i2, 0.001)
    }

    @Test fun roundingNeverShowsTwelveInches() {
        // 5 ft 11.97 in must not be shown as "5 ft 12.0 in"
        val cm = HeightUnits.feetInchesToCm(5.0, 11.97)
        val (f, i) = HeightUnits.cmToFeetInches(cm)
        assertEquals(6, f)
        assertEquals(0.0, i, 0.001)
        assertTrue(i < 12.0)
    }

    @Test fun roundTripStaysWithinTenthOfAnInch() {
        var cm = 100.0
        while (cm <= 220.0) {
            val (f, i) = HeightUnits.cmToFeetInches(cm)
            val back = HeightUnits.feetInchesToCm(f.toDouble(), i)
            assertEquals(cm, back, 0.14)
            cm += 0.7
        }
    }

    @Test fun formatting() {
        assertEquals("170.2 cm", HeightUnits.formatCm(170.18))
        assertEquals("5 ft 7.0 in", HeightUnits.formatFeetInches(170.18))
        assertEquals("+1.0 cm", HeightUnits.formatDiff(1.0, false))
        assertEquals("-0.4 in", HeightUnits.formatDiff(-1.0, true))
    }

    @Test fun implausibleValuesAreRejected() {
        assertFalse(HeightUnits.isPlausibleCm(0.0))
        assertFalse(HeightUnits.isPlausibleCm(49.9))
        assertFalse(HeightUnits.isPlausibleCm(300.0))
        assertFalse(HeightUnits.isPlausibleCm(Double.NaN))
        assertTrue(HeightUnits.isPlausibleCm(165.0))
    }
}
