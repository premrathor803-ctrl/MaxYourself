package com.elevate.launcher.ui.center

import com.elevate.launcher.center.content.Articles
import com.elevate.launcher.center.content.Exercises
import com.elevate.launcher.center.content.Plans

/** Lets app search also find Center features, guides, exercises and workouts ("posture", "squat", "sleep"...). */
object CenterIndex {
    class Hit(val title: String, val sub: String, val section: String)

    private class Feature(val title: String, val sub: String, val section: String, val words: List<String>)

    private val features = listOf(
        Feature("Habits", "Center · daily routines and streaks", "habits", listOf("habit", "routine", "streak", "daily", "tracker")),
        Feature("Workouts", "Center · home workouts and history", "workouts", listOf("workout", "exercise", "training", "fitness", "gym", "home workout", "strength", "cardio")),
        Feature("Grooming and skincare", "Center · routines and guides", "grooming", listOf("grooming", "skincare", "skin", "hair", "beard", "face", "hygiene", "lookmax", "facial")),
        Feature("Posture and mobility", "Center · stretches and posture", "posture", listOf("posture", "mobility", "stretch", "flexibility", "back", "neck")),
        Feature("Height tracker", "Center · optional height log", "height", listOf("height", "growth", "tall", "measure")),
        Feature("Sleep log", "Center · optional sleep diary", "sleep", listOf("sleep", "bedtime", "rest")),
        Feature("Goals", "Center · targets you set", "goals", listOf("goal", "target")),
        Feature("Progress", "Center · charts from your logs", "progress", listOf("progress", "chart", "stats", "statistics")),
        Feature("Learn", "Center · offline guides", "learn", listOf("learn", "guide", "article", "tips")),
        Feature("Center settings", "Center · timers, trackers and your data", "settings", listOf("center", "elevate", "delete data"))
    )

    fun search(q: String): List<Hit> {
        val s = q.trim().lowercase()
        if (s.length < 2) return emptyList()
        val out = ArrayList<Hit>()
        for (f in features) {
            if (f.title.lowercase().contains(s) || f.words.any { it.startsWith(s) || s.startsWith(it) && it.length >= 4 }) {
                out.add(Hit(f.title, f.sub, f.section))
            }
        }
        for (a in Articles.search(s).take(3)) out.add(Hit(a.title, "Center · " + a.group + " guide", "article:" + a.key))
        for (e in Exercises.search(s).take(3)) out.add(Hit(e.name, "Center · exercise", "exercise:" + e.key))
        for (p in Plans.all.filter { it.name.lowercase().contains(s) }.take(2)) out.add(Hit(p.name, "Center · workout", "plan:" + p.key))
        return out.take(6)
    }
}
