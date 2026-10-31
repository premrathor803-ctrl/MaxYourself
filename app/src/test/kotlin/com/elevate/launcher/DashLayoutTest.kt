package com.elevate.launcher

import com.elevate.launcher.center.data.DashLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashLayoutTest {
    private val all = DashLayout.all

    @Test fun balancedShowsEverythingInOrder() {
        assertEquals(all, DashLayout.resolve("balanced", "", ""))
        assertEquals(8, all.size)
    }

    @Test fun unknownLayoutFallsBackToBalanced() {
        assertEquals(all, DashLayout.resolve("nonsense", "", ""))
    }

    @Test fun everyLayoutKeepsExploreAndHero() {
        for (l in listOf("balanced", "focus", "explorer")) {
            val r = DashLayout.resolve(l, "", "")
            assertTrue("$l keeps explore", DashLayout.EXPLORE in r)
            assertTrue("$l keeps hero", DashLayout.HERO in r)
            assertEquals("$l has no duplicates", r.size, r.toSet().size)
        }
    }

    @Test fun focusPutsHabitsAndWorkoutRightAfterTheHero() {
        val r = DashLayout.resolve("focus", "", "")
        assertEquals(listOf("hero", "habits", "workout"), r.take(3))
        assertTrue(DashLayout.GAMING !in r)
    }

    @Test fun savedOrderWinsAndMissingCardsAreAppended() {
        val r = DashLayout.resolve("balanced", "gaming,hero", "")
        assertEquals(listOf("gaming", "hero"), r.take(2))
        assertEquals(all.toSet(), r.toSet())
    }

    @Test fun hiddenCardsAreRemoved() {
        val r = DashLayout.resolve("balanced", "", "gaming,achievements")
        assertTrue(DashLayout.GAMING !in r)
        assertTrue(DashLayout.ACHIEVEMENTS !in r)
        assertEquals(6, r.size)
    }

    @Test fun unknownIdsAreIgnored() {
        val r = DashLayout.resolve("balanced", "bogus,hero,,habits", "also_bogus")
        assertEquals(all.toSet(), r.toSet())
        assertEquals(listOf("hero", "habits"), r.take(2))
    }

    @Test fun hidingEverythingStillShowsTheHero() {
        assertEquals(listOf("hero"), DashLayout.resolve("balanced", "", all.joinToString(",")))
    }

    @Test fun editorListsMatchWhatTheDashboardShows() {
        for (l in listOf("balanced", "focus", "explorer")) {
            val order = DashLayout.fullOrder(l, "")
            val hidden = DashLayout.hiddenSet(l, "", "")
            assertEquals(8, order.size)
            assertEquals(DashLayout.resolve(l, "", ""), order.filter { it !in hidden })
        }
    }

    @Test fun layoutOwnChoicesStoreNothing() {
        for (l in listOf("balanced", "focus", "explorer")) {
            val order = DashLayout.fullOrder(l, "")
            val hidden = DashLayout.hiddenSet(l, "", "")
            assertEquals("" to "", DashLayout.toPrefs(l, order, hidden))
        }
    }

    @Test fun editedChoicesSurviveTheRoundTrip() {
        val order = DashLayout.move(DashLayout.fullOrder("focus", ""), "gaming", -5).split(',')
        val hidden = DashLayout.hiddenSet("focus", "", "") - "gaming" + "workout"
        val (o, h) = DashLayout.toPrefs("focus", order, hidden)
        assertTrue(o.isNotEmpty())
        assertEquals(order, DashLayout.fullOrder("focus", o))
        assertEquals(hidden, DashLayout.hiddenSet("focus", o, h))
        val shown = DashLayout.resolve("focus", o, h)
        assertTrue(DashLayout.GAMING in shown)
        assertTrue(DashLayout.WORKOUT !in shown)
    }

    @Test fun moveGoesUpDownAndStopsAtTheEnds() {
        val base = listOf("a", "b", "c")
        assertEquals("b,a,c", DashLayout.move(base, "b", -1))
        assertEquals("a,c,b", DashLayout.move(base, "b", 1))
        assertEquals("a,b,c", DashLayout.move(base, "a", -1))
        assertEquals("a,b,c", DashLayout.move(base, "c", 1))
        assertEquals("a,b,c", DashLayout.move(base, "zzz", 1))
    }

    @Test fun everyCardHasAName() {
        for (id in all) assertTrue("name for $id", !DashLayout.names[id].isNullOrBlank())
    }
}
