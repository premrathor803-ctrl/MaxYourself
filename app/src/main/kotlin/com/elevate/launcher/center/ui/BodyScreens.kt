package com.elevate.launcher.center.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.elevate.launcher.center.content.Articles
import com.elevate.launcher.center.content.Plans
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.Measure
import com.elevate.launcher.center.data.MeasureRepo
import com.elevate.launcher.center.data.SleepEntry
import com.elevate.launcher.center.data.SleepRepo
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast

// =============================================================================================
class PostureScreen(host: CenterActivity) : Screen(host) {
    override val title = "Posture and mobility"

    override fun build(): View {
        val col = host.vbox()
        col.noteCard("These are movement and awareness tools. They do not claim to fix your spine or change your height.")
        col.tapCard("Posture and mobility library", "Gentle activities grouped by purpose, with done tracking") { host.push(MovementLibraryScreen(host)) }
        Plans.get("posture_reset")?.let { col.planCard(host, it, "Quick reset") }

        col.heading("Mobility", 10)
        Plans.get("mobility_flex")?.let { p -> col.tapCard(p.name, "About ${p.minutes} min · ${p.blurb}") { host.push(PlanDetailScreen(host, p)) } }
        Plans.get("recovery_gentle")?.let { p -> col.tapCard(p.name, "About ${p.minutes} min · ${p.blurb}") { host.push(PlanDetailScreen(host, p)) } }
        col.tapCard("Mobility exercises", "Stretches with written instructions") { host.push(ExerciseLibraryScreen(host, "Mobility")) }
        col.tapCard("Posture exercises", "Small movements for upper back and neck") { host.push(ExerciseLibraryScreen(host, "Posture")) }

        for (g in listOf("Posture", "Sleep", "Nutrition")) {
            col.heading(g, 12)
            for (a in Articles.inGroup(g)) col.tapCard(a.title, a.summary) { host.push(ArticleScreen(host, a)) }
        }

        col.heading("Optional trackers", 12)
        val h = MeasureRepo.list("height").size
        col.tapCard(
            "Height and growth",
            if (Center.moduleOn("height", false)) pluralize(h, "reading") + " recorded" else "Tracker, movement library and growth guides. Height tracking is off until you turn it on."
        ) { host.push(GrowthScreen(host)) }
        val s = SleepRepo.recent(400).size
        col.tapCard(
            "Sleep log",
            if (Center.moduleOn("sleep", false)) pluralize(s, "night") + " logged" else "Off. Tap to turn on a simple sleep diary."
        ) { host.push(SleepScreen(host)) }

        col.heading("About height", 12)
        for (key in listOf("height_facts", "measure_height")) Articles.get(key)?.let { a ->
            col.tapCard(a.title, a.summary) { host.push(ArticleScreen(host, a)) }
        }
        col.noteCard(Articles.DISCLAIMER)
        return col
    }
}

// =============================================================================================
// =============================================================================================
class SleepScreen(host: CenterActivity) : Screen(host) {
    override val title = "Sleep log"

    override fun build(): View {
        val col = host.vbox()
        if (!Center.moduleOn("sleep", false)) {
            col.card {
                cardTitle("Optional sleep diary")
                para("Note when you went to bed, when you woke up and how rested you felt. It is a personal diary: it does not measure sleep and cannot diagnose a sleep problem.", dim = false, sp = 14f, bottom = 0)
            }
            col.actionButton("Turn on sleep log") { Center.setModule("sleep", true); host.refresh() }
            col.tapCard("Sleep basics", "How much sleep helps") { Articles.get("sleep_basics")?.let { host.push(ArticleScreen(host, it)) } }
            return col
        }

        col.noteCard("A personal diary, not a sleep measurement. Hours are calculated from the bed and wake times you enter.")
        col.actionButton("Log a night") { editDialog(null) }
        val list = SleepRepo.recent(14)
        if (list.isEmpty()) {
            col.emptyState("No nights logged", "Log last night to start. A chart appears after your first entry.")
        } else {
            val ordered = list.reversed()
            val hours = ordered.map { (it.hours ?: 0.0).toFloat() }
            col.heading("Hours slept, last ${pluralize(list.size, "night")}", 8)
            col.card {
                val chart = BarChart(host)
                chart.set(hours, ordered.map { it.day.substring(8).trimStart('0') }, null, " h")
                addView(chart, lp(MATCH, WRAP))
                if (list.size >= 3) {
                    val avg = list.mapNotNull { it.hours }.average()
                    para("Average of ${list.size} logged nights: " + String.format(java.util.Locale.US, "%.1f", avg) + " h (calculated).", sp = 12.5f, bottom = 0)
                } else {
                    para("Log a few more nights to see an average.", sp = 12.5f, bottom = 0)
                }
            }
            col.heading("Entries", 10)
            for (e in list) {
                val h = e.hours
                col.tapCard(
                    Days.pretty(e.day),
                    clockText(e.bed) + " to " + clockText(e.wake) + (if (h != null) " · " + String.format(java.util.Locale.US, "%.1f", h) + " h" else "") +
                        (if (e.energy > 0) " · rested " + e.energy + "/5" else "")
                ) {
                    Dialogs.list(host, Days.pretty(e.day), listOf("Edit", "Delete")) { i ->
                        if (i == 0) editDialog(e)
                        else Dialogs.confirm(host, "Delete this night?", Days.pretty(e.day), "Delete") {
                            SleepRepo.delete(e.day)
                            host.refresh()
                        }
                    }
                }
            }
        }
        col.heading("Learn", 12)
        for (key in listOf("sleep_basics", "wind_down")) Articles.get(key)?.let { a ->
            col.tapCard(a.title, a.summary) { host.push(ArticleScreen(host, a)) }
        }
        col.gap(6)
        col.actionButton("Turn off sleep log", false) { Center.setModule("sleep", false); host.refresh() }
        if (list.isNotEmpty()) {
            col.actionButton("Delete all sleep entries", false) {
                Dialogs.confirm(host, "Delete all entries?", "Every sleep entry will be removed from this phone.", "Delete all") {
                    Center.wipe("sleep")
                    host.refresh()
                }
            }
        }
        return col
    }

    private fun editDialog(existing: SleepEntry?) {
        val box = host.vbox()
        var day = existing?.day ?: Days.today()
        var bed = existing?.bed ?: "23:00"
        var wake = existing?.wake ?: "07:00"
        var energy = existing?.energy ?: 0
        val note = host.field("Note (optional)", existing?.note ?: "")
        var dayRow: LinearLayout? = null
        var bedRow: LinearLayout? = null
        var wakeRow: LinearLayout? = null
        dayRow = box.pickRow("Woke up on", Days.pretty(day)) {
            host.pickDate(day) { d -> day = d; (dayRow?.getChildAt(1) as? TextView)?.text = Days.pretty(d) + "  ›" }
        }
        bedRow = box.pickRow("Went to bed", clockText(bed)) {
            host.pickTime(bed) { t -> bed = t; (bedRow?.getChildAt(1) as? TextView)?.text = clockText(t) + "  ›" }
        }
        wakeRow = box.pickRow("Woke up", clockText(wake)) {
            host.pickTime(wake) { t -> wake = t; (wakeRow?.getChildAt(1) as? TextView)?.text = clockText(t) + "  ›" }
        }
        box.fieldLabel("How rested did you feel? (1 low, 5 high)")
        box.addView(host.chips((1..5).map { it.toString() to it.toString() }, if (energy in 1..5) energy.toString() else "") { energy = it.toInt() }, lp(MATCH, WRAP))
        box.fieldLabel("Note")
        box.addView(note, lp(MATCH, WRAP))
        host.formDialog(if (existing == null) "Log a night" else "Edit night", box) {
            if (existing != null && existing.day != day) SleepRepo.delete(existing.day)
            SleepRepo.save(SleepEntry(day, bed, wake, energy, note.text.toString().trim()))
            host.refresh()
            true
        }
    }
}
