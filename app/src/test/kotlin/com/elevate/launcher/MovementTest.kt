package com.elevate.launcher

import com.elevate.launcher.center.content.Articles
import com.elevate.launcher.center.content.Exercises
import com.elevate.launcher.center.content.Movement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementTest {
    @Test fun everyGroupedExerciseExists() {
        for (g in Movement.groups) {
            assertTrue("group ${g.key} is empty", g.keys.isNotEmpty())
            for (k in g.keys) assertNotNull("missing exercise $k in ${g.key}", Exercises.get(k))
        }
    }

    @Test fun groupKeysAreUnique() {
        assertEquals(Movement.groups.size, Movement.groups.map { it.key }.toSet().size)
    }

    @Test fun suggestionsAreCautiousAndNeverMentionHeight() {
        for (k in Movement.keys) {
            val s = Movement.suggestion(Exercises.get(k)!!).lowercase()
            assertFalse(s.contains("taller") || s.contains("height") || s.contains("grow"))
            assertTrue(s.isNotEmpty())
        }
    }

    @Test fun growthGuideArticlesExistAndAreKeyedUniquely() {
        for (k in Articles.growthGuide) assertNotNull("missing article $k", Articles.get(k))
        assertEquals(Articles.all.size, Articles.all.map { it.key }.toSet().size)
    }

    @Test fun growthArticlesCarryTheDoctorAdvice() {
        val doctor = Articles.get("growth_doctor")!!.text()
        assertTrue(doctor.contains("doctor"))
        assertTrue(Articles.get("growth_claims")!!.text().contains("not supported"))
    }
}
