package com.elevate.launcher

import com.elevate.launcher.center.data.XpMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XpTest {
    private fun stats(h: Int = 0, w: Int = 0, m: Int = 0, s: Int = 0, g: Int = 0) = XpMath.Stats(h, w, m, s, g)

    @Test fun levelStartsGrowByOneHundredEachTime() {
        assertEquals(listOf(0, 100, 300, 600, 1000, 1500), (1..6).map { XpMath.levelStart(it) })
    }

    @Test fun levelBoundaries() {
        assertEquals(1, XpMath.level(0).level)
        assertEquals(1, XpMath.level(99).level)
        val l2 = XpMath.level(100)
        assertEquals(2, l2.level)
        assertEquals(0, l2.into)
        assertEquals(200, l2.span)
        assertEquals(2, XpMath.level(299).level)
        assertEquals(3, XpMath.level(300).level)
        assertEquals(1000, XpMath.level(1000).total)
    }

    @Test fun fractionAndRemainingAreConsistent() {
        val l = XpMath.level(150)
        assertEquals(2, l.level)
        assertEquals(50, l.into)
        assertEquals(150, l.toNext)
        assertEquals(0.25f, l.fraction, 0.0001f)
    }

    @Test fun negativeXpIsTreatedAsZero() {
        assertEquals(1, XpMath.level(-50).level)
        assertEquals(0, XpMath.xp(stats(h = -3, w = -1)))
    }

    @Test fun xpComesOnlyFromRealLogs() {
        assertEquals(10, XpMath.xp(stats(h = 1)))
        assertEquals(50, XpMath.xp(stats(w = 1)))
        assertEquals(10, XpMath.xp(stats(m = 1)))
        assertEquals(5, XpMath.xp(stats(s = 1)))
        assertEquals(10 + 50 + 10 + 5, XpMath.xp(stats(1, 1, 1, 1, 0)))
    }

    @Test fun heightReadingsNeverEarnXp() {
        assertEquals(0, XpMath.xp(stats(g = 50)))
    }

    @Test fun titlesCoverEveryLevel() {
        assertEquals("Starter", XpMath.title(1))
        assertEquals("Steady", XpMath.title(2))
        assertEquals("Elevated", XpMath.title(40))
        assertTrue((1..30).all { XpMath.title(it).isNotBlank() })
    }

    @Test fun nothingIsUnlockedFromNothing() {
        val a = XpMath.achievements(stats())
        assertEquals(10, a.size)
        assertTrue(a.none { it.unlocked })
        assertTrue(a.all { it.fraction == 0f })
    }

    @Test fun achievementIdsAreUnique() {
        val a = XpMath.achievements(stats())
        assertEquals(a.size, a.map { it.id }.toSet().size)
    }

    @Test fun progressIsCappedAtTheGoal() {
        val a = XpMath.achievements(stats(h = 500)).first { it.id == "checks_100" }
        assertEquals(100, a.value)
        assertTrue(a.unlocked)
        assertEquals(1f, a.fraction, 0f)
    }

    @Test fun achievementsUnlockFromTheirOwnLogOnly() {
        val onlyHeight = XpMath.achievements(stats(g = 1)).filter { it.unlocked }.map { it.id }
        assertEquals(listOf("first_measure"), onlyHeight)
        val onlyHabit = XpMath.achievements(stats(h = 1)).filter { it.unlocked }.map { it.id }
        assertEquals(listOf("first_check"), onlyHabit)
    }

    @Test fun allRounderNeedsFourKindsOfLog() {
        fun unlocked(s: XpMath.Stats) = XpMath.achievements(s).first { it.id == "all_round" }.unlocked
        assertFalse(unlocked(stats(h = 9, w = 9, m = 9)))
        assertTrue(unlocked(stats(h = 1, w = 1, m = 1, s = 1)))
    }

    @Test fun firstLookIsQuiet() {
        val m = XpMath.milestones(null, null, 4, setOf("first_check"))
        assertNull(m.levelUp)
        assertTrue(m.unlocked.isEmpty())
    }

    @Test fun levelUpAndNewAchievementsAreReportedOnce() {
        val m = XpMath.milestones(2, setOf("first_check"), 3, setOf("first_check", "first_workout", "checks_10"))
        assertEquals(3, m.levelUp)
        assertEquals(listOf("checks_10", "first_workout"), m.unlocked)
        val again = XpMath.milestones(3, setOf("first_check", "first_workout", "checks_10"), 3, setOf("first_check", "first_workout", "checks_10"))
        assertNull(again.levelUp)
        assertTrue(again.unlocked.isEmpty())
    }

    @Test fun droppingALevelAfterDeletingRecordsIsNotCelebrated() {
        val m = XpMath.milestones(4, setOf("first_check"), 3, setOf("first_check"))
        assertNull(m.levelUp)
    }
}
