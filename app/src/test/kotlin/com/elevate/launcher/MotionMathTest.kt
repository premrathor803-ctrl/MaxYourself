package com.elevate.launcher

import com.elevate.launcher.core.MotionMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {
    private fun level(pref: String = "full", perf: String = "balanced", scale: Float = 1f) = MotionMath.level(pref, perf, scale)

    @Test fun fullWhenEverythingAllowsIt() {
        assertEquals(MotionMath.FULL, level())
    }

    @Test fun phoneSettingAlwaysWins() {
        assertEquals(MotionMath.OFF, level(scale = 0f))
        assertEquals(MotionMath.OFF, level("full", "balanced", -1f))
    }

    @Test fun elevateSettingCanLowerMotion() {
        assertEquals(MotionMath.REDUCED, level("reduced"))
        assertEquals(MotionMath.OFF, level("off"))
    }

    @Test fun performanceProfileCanLowerMotionFurther() {
        assertEquals(MotionMath.REDUCED, level("full", "lightweight"))
        assertEquals(MotionMath.OFF, level("full", "minimal"))
        assertEquals(MotionMath.OFF, level("reduced", "minimal"))
    }

    @Test fun nothingRaisesMotionAboveWhatTheLowestSettingAllows() {
        for (p in listOf("full", "reduced", "off")) for (f in listOf("balanced", "lightweight", "minimal")) {
            val l = level(p, f)
            if (p == "off" || f == "minimal") assertEquals(MotionMath.OFF, l)
            if (p == "reduced" || f == "lightweight") assertTrue(l <= MotionMath.REDUCED)
        }
    }

    @Test fun durationsScaleWithTheLevel() {
        assertEquals(500L, MotionMath.duration(500L, MotionMath.FULL))
        assertEquals(300L, MotionMath.duration(500L, MotionMath.REDUCED))
        assertEquals(0L, MotionMath.duration(500L, MotionMath.OFF))
    }

    @Test fun entranceCountNeverGrowsWhenMotionIsLower() {
        assertEquals(8, MotionMath.entranceCount(MotionMath.FULL))
        assertEquals(3, MotionMath.entranceCount(MotionMath.REDUCED))
        assertEquals(0, MotionMath.entranceCount(MotionMath.OFF))
    }
}
