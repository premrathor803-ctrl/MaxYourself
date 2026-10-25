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
            "Height tracker",
            if (Center.moduleOn("height", false)) pluralize(h, "reading") + " recorded" else "Off. Tap to read how it works and turn it on."
        ) { host.push(HeightScreen(host)) }
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
class HeightScreen(host: CenterActivity) : Screen(host) {
    override val title = "Height tracker"

    private fun cmToText(cm: Double): String =
        if (Center.heightUnit == "ftin") {
            val totalIn = cm / 2.54
            val ft = Math.floor(totalIn / 12.0).toInt()
            val inch = totalIn - ft * 12.0
            String.format(java.util.Locale.US, "%d ft %.1f in", ft, inch)
        } else {
            String.format(java.util.Locale.US, "%.1f cm", cm)
        }

    override fun build(): View {
        val col = host.vbox()
        if (!Center.moduleOn("height", false)) {
            col.card {
                cardTitle("Optional height tracker")
                para("You can record your height now and then and see it on a chart. It only shows what you measured.", dim = false, sp = 14f)
                para("Height is mostly set by genetics, and your growth plates close in the late teens. Nothing in this app, no exercise, stretch or supplement, makes adults taller, and nothing here claims otherwise. Your height also changes by about 1 to 2 cm across a day, so small differences are usually just noise.", sp = 13f, bottom = 0)
            }
            col.actionButton("Turn on height tracker") { Center.setModule("height", true); host.refresh() }
            col.tapCard("How height works", "Genetics, growth plates and daily variation") { Articles.get("height_facts")?.let { host.push(ArticleScreen(host, it)) } }
            col.tapCard("How to measure properly", "A repeatable method") { Articles.get("measure_height")?.let { host.push(ArticleScreen(host, it)) } }
            return col
        }

        col.noteCard("This tracks what you measured. It does not predict or change your height. Readings vary by about 1 to 2 cm through the day, so compare measurements taken at the same time of day.")
        col.fieldLabel("Show height in")
        col.addView(host.chips(listOf("cm" to "Centimetres", "ftin" to "Feet and inches"), Center.heightUnit) { Center.heightUnit = it; host.refresh() }, lp(MATCH, WRAP))
        col.gap(8)
        col.actionButton("Add a measurement") { editDialog(null) }

        val list = MeasureRepo.list("height")
        if (list.isEmpty()) {
            col.emptyState("No measurements yet", "Add your first reading. A chart appears once you have two or more.")
        } else {
            col.heading("Chart", 8)
            if (list.size >= 2) {
                val first = list.first()
                val inch = Center.heightUnit == "ftin"
                val chart = LineChart(host)
                val x0 = first.day
                chart.set(
                    list.map { Days.diff(x0, it.day).toFloat() },
                    list.map { if (inch) (it.value / 2.54).toFloat() else it.value.toFloat() },
                    Days.short(first.day), Days.short(list.last().day), if (inch) " in" else " cm"
                )
                col.card { addView(chart, lp(MATCH, WRAP)) }
                val latest = list.last()
                val change = latest.value - first.value
                col.card {
                    statRow(listOf(
                        cmToText(first.value) to "First reading",
                        cmToText(latest.value) to "Latest reading",
                        String.format(java.util.Locale.US, "%+.1f cm", change) to "Difference"
                    ))
                    para("Recorded by you. The difference is calculated from your first and latest readings. A change under about 1 cm is within normal measuring error.", sp = 12f, bottom = 0)
                }
            } else {
                col.card {
                    cardTitle(cmToText(list.first().value))
                    para("Add at least one more reading to see a chart.", sp = 13f, bottom = 0)
                }
            }
            col.heading("Readings", 10)
            for (m in list.reversed()) {
                col.tapCard(cmToText(m.value), Days.pretty(m.day) + (if (m.note.isNotEmpty()) " · " + m.note else "")) {
                    Dialogs.list(host, cmToText(m.value), listOf("Edit", "Delete")) { i ->
                        if (i == 0) editDialog(m)
                        else Dialogs.confirm(host, "Delete this reading?", Days.pretty(m.day), "Delete") {
                            MeasureRepo.delete(m.id)
                            host.refresh()
                        }
                    }
                }
            }
        }
        col.heading("Learn", 12)
        for (key in listOf("measure_height", "height_facts")) Articles.get(key)?.let { a ->
            col.tapCard(a.title, a.summary) { host.push(ArticleScreen(host, a)) }
        }
        col.gap(6)
        col.actionButton("Turn off height tracker", false) { Center.setModule("height", false); host.refresh() }
        if (list.isNotEmpty()) {
            col.actionButton("Delete all height readings", false) {
                Dialogs.confirm(host, "Delete all readings?", "Every height reading will be removed from this phone.", "Delete all") {
                    Center.wipe("measures")
                    host.refresh()
                }
            }
        }
        return col
    }

    private fun editDialog(existing: Measure?) {
        val box = host.vbox()
        var day = existing?.day ?: Days.today()
        val inch = Center.heightUnit == "ftin"
        val cm = host.field("Height in cm", if (existing != null && !inch) fmtNum(Math.round(existing.value * 10) / 10.0) else "", number = true)
        val ft = host.field("Feet", if (existing != null && inch) Math.floor(existing.value / 2.54 / 12.0).toInt().toString() else "", number = true)
        val inc = host.field("Inches", if (existing != null && inch) fmtNum(Math.round((existing.value / 2.54 - Math.floor(existing.value / 2.54 / 12.0) * 12.0) * 10) / 10.0) else "", number = true)
        val note = host.field("Note, e.g. morning, barefoot", existing?.note ?: "")
        if (inch) {
            box.fieldLabel("Height")
            val r = host.hbox()
            r.addView(ft, lp(0, WRAP, 1f))
            r.addView(inc, lp(0, WRAP, 1f).apply { leftMargin = host.dp(12) })
            box.addView(r, lp(MATCH, WRAP))
        } else {
            box.fieldLabel("Height (cm)")
            box.addView(cm, lp(MATCH, WRAP))
        }
        var dateRow: LinearLayout? = null
        dateRow = box.pickRow("Date", Days.pretty(day)) {
            host.pickDate(day) { d ->
                day = d
                (dateRow?.getChildAt(1) as? TextView)?.text = Days.pretty(d) + "  ›"
            }
        }
        box.fieldLabel("Note (optional)")
        box.addView(note, lp(MATCH, WRAP))
        host.formDialog(if (existing == null) "Add measurement" else "Edit measurement", box) {
            val value: Double? = if (inch) {
                val f = ft.text.toString().replace(',', '.').toDoubleOrNull()
                val i = inc.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                if (f == null) null else (f * 12.0 + i) * 2.54
            } else cm.text.toString().replace(',', '.').toDoubleOrNull()
            if (value == null || value < 50.0 || value > 260.0) {
                host.toast("Enter a realistic height")
                false
            } else {
                if (existing == null) MeasureRepo.add("height", value, day, note.text.toString().trim())
                else {
                    existing.value = value; existing.day = day; existing.note = note.text.toString().trim()
                    MeasureRepo.update(existing)
                }
                host.refresh()
                true
            }
        }
    }
}

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
