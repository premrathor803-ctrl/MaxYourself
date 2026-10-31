package com.elevate.launcher.center.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.elevate.launcher.center.content.Articles
import com.elevate.launcher.center.content.Exercises
import com.elevate.launcher.center.content.Movement
import com.elevate.launcher.center.data.Activity
import com.elevate.launcher.center.data.ActivityRepo
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.GrowthMath
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.Measure
import com.elevate.launcher.center.data.MeasureRepo
import com.elevate.launcher.center.data.SleepRepo
import com.elevate.launcher.center.data.WorkoutRepo
import com.elevate.launcher.core.Dialogs
import com.elevate.launcher.core.HeightUnits
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.Io

/**
 * Reads on a background thread and draws on the main thread. When the screen was shown before, the last result is drawn
 * straight away (so the page does not jump), and it is only redrawn if the fresh result is different.
 */
class AsyncBox<T : Any>(
    private val host: CenterActivity,
    private val load: () -> T,
    private val draw: (LinearLayout, T) -> Unit,
    private val sig: (T) -> String
) {
    private var cached: T? = null
    private var cachedSig = ""
    private var box: LinearLayout? = null

    fun view(): LinearLayout {
        val b = host.vbox()
        box = b
        val c = cached
        if (c != null) draw(b, c) else {
            b.addView(com.elevate.launcher.core.Skeleton(host, 120), lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
            b.addView(com.elevate.launcher.core.Skeleton(host, 84), lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
            b.addView(com.elevate.launcher.core.Skeleton(host, 84), lp(MATCH, WRAP))
        }
        fetch(b)
        return b
    }

    /** Reads again and redraws if anything changed, without rebuilding the whole screen. */
    fun reload() {
        val b = box ?: return
        fetch(b)
    }

    private fun fetch(b: LinearLayout) {
        Io.work {
            val fresh: T? = try { load() } catch (t: Throwable) { android.util.Log.e("Elevate", "could not read records", t); null }
            Io.ui {
                if (box !== b || host.isDestroyed) return@ui
                if (fresh == null) {
                    if (cached == null) { b.removeAllViews(); b.noteCard("Could not read your records. Close this screen and open it again.") }
                    return@ui
                }
                val s = sig(fresh)
                if (cached == null || s != cachedSig) {
                    cached = fresh
                    cachedSig = s
                    b.removeAllViews()
                    draw(b, fresh)
                }
            }
        }
    }

    /** Draws the last result again, for example after a chart range or a unit was changed. */
    fun redraw() {
        val b = box ?: return
        val c = cached ?: return
        b.removeAllViews()
        draw(b, c)
    }
}

// =============================================================================================
/** Everything the hub shows, read in one go. All of it comes from stored records. */
class GrowthData(
    val heights: List<Measure>,
    val moveDays: Int,
    val moveItems: Int,
    val habitDone: Int,
    val habitSched: Int,
    val habitCount: Int,
    val recent: List<Recent>,
    val sleepAvg: Double?,
    val sleepNights: Int
) {
    class Recent(val day: String, val text: String, val cm: Double? = null)

    fun sig(): String = buildString {
        for (m in heights) append(m.id).append(':').append(m.day).append(':').append(m.value).append(':').append(m.note).append(';')
        append('|').append(moveDays).append(',').append(moveItems).append(',').append(habitDone).append(',').append(habitSched).append(',').append(habitCount)
        append('|').append(sleepAvg).append(',').append(sleepNights)
        for (r in recent) append('|').append(r.day).append(r.text).append(r.cm)
    }
}

object GrowthLoad {
    /** Habits in these categories count as "growth habits" on the hub. */
    val habitCategories = setOf("Posture", "Sleep", "Nutrition", "Fitness", "Health")

    fun hub(): GrowthData {
        val today = Days.today()
        val ws = Days.weekStart(today)
        val heights = MeasureRepo.list("height")
        val week = ActivityRepo.between(ws, today)
        val moveDays = week.map { it.day }.toSet().size
        val moveItems = week.count { it.kind == ActivityRepo.KIND_EXERCISE }

        var done = 0
        var sched = 0
        val habits = HabitRepo.all().filter { it.category in habitCategories }
        val workoutDays = WorkoutRepo.completedDays(ws, today)
        for (h in habits) {
            val lg = HabitRepo.logs(h.id, ws, today)
            for (d in Days.range(ws, today)) {
                when (HabitRepo.stateOf(h, d, lg, today, workoutDays)) {
                    HState.COMPLETED -> { done++; sched++ }
                    HState.NOT_COMPLETED -> sched++
                    else -> {}
                }
            }
        }

        val recent = ArrayList<GrowthData.Recent>()
        for (m in heights.takeLast(3)) recent.add(GrowthData.Recent(m.day, "Measured", m.value))
        for (a in ActivityRepo.recent(4)) recent.add(GrowthData.Recent(a.day, describe(a)))
        val sleep = SleepRepo.recent(7)
        for (e in sleep.take(2)) {
            val h = e.hours
            if (h != null) recent.add(GrowthData.Recent(e.day, "Slept " + String.format(java.util.Locale.US, "%.1f", h) + " h"))
        }
        recent.sortByDescending { it.day }
        val hours = sleep.mapNotNull { it.hours }
        return GrowthData(
            heights, moveDays, moveItems, done, sched, habits.size, recent.take(6),
            if (hours.isEmpty()) null else hours.average(), hours.size
        )
    }

    fun describe(a: Activity): String =
        if (a.kind == ActivityRepo.KIND_EXERCISE) "Did " + a.label + (if (a.minutes > 0) ", ${a.minutes} min" else "")
        else a.label + (if (a.minutes > 0) ", ${a.minutes} min" else "")
}

/** "27 Oct 2026". */
fun fullDate(day: String): String = Days.short(day) + " " + day.take(4)

// =============================================================================================
class GrowthScreen(host: CenterActivity) : Screen(host) {
    override val title = "Height and growth"

    private var range = "all"
    private var selId = -1L
    private var showText = false

    private val async = AsyncBox(host, { GrowthLoad.hub() }, { b, d -> draw(b, d) }, { it.sig() })

    override fun onResumeScreen() { host.refresh() }

    override fun build(): View = async.view()

    private fun draw(col: LinearLayout, d: GrowthData) {
        val inch = Center.heightUnit == "ftin"
        col.noteCard("Everything here comes from what you record on this phone. It cannot predict or change your height, and no exercise, food or supplement here is claimed to make anyone taller. Your height also varies by about 1 to 2 cm through a day.")

        col.tileRow(
            Triple("Add measurement", "Record your height", { addReading(d) }),
            Triple("Log movement", "Activity or exercises", { host.push(ActivityScreen(host)) })
        )
        col.tileRow(
            Triple("Log sleep", "Hours slept", { host.openSection("sleep") }),
            Triple("Habits", "Tick today's habits", { host.openSection("habits") })
        )

        heightCard(col, d, inch)
        if (Center.moduleOn("height", false) && d.heights.isNotEmpty()) chartCard(col, d, inch)

        // ---- this week
        col.heading("This week", 8)
        col.card {
            val rows = ArrayList<Pair<String, String>>()
            rows.add((if (d.moveDays == 0) "None" else pluralize(d.moveDays, "day")) to "Movement logged")
            rows.add((if (d.habitCount == 0) "–" else "${d.habitDone} of ${d.habitSched}") to "Growth habit check-ins")
            statRow(rows)
            if (d.habitCount == 0) {
                para("Growth habits are posture, sleep, nutrition and fitness habits. None yet: add one when you like.", sp = 12.5f, bottom = 6)
                actionButton("Browse habit ideas", false) { host.openSection("habits") }
            } else {
                para("Counted from your habit ticks for this week so far. Skipped days and days a habit is not scheduled are left out, and today only counts once it is done.", sp = 12.5f, bottom = 0)
            }
        }

        // ---- recent
        col.heading("Recent activity", 8)
        if (d.recent.isEmpty()) {
            col.para("Nothing logged yet. Measurements, movement and sleep you record will show up here.", sp = 13.5f)
        } else {
            col.card {
                for (r in d.recent) {
                    val text = if (r.cm != null) r.text + " " + HeightUnits.format(r.cm, inch) else r.text
                    para(Days.short(r.day) + "  ·  " + text, dim = false, sp = 13.5f, bottom = 4)
                }
                para("All of this was entered by you.", sp = 12f, bottom = 0)
            }
        }

        // ---- sections
        col.heading("Explore", 8)
        col.tapCard("Posture and mobility library", "Gentle activities, grouped by purpose, with done tracking") { host.push(MovementLibraryScreen(host)) }
        col.tapCard("Activity log", "Physical activity and movement you did") { host.push(ActivityScreen(host)) }
        col.tapCard(
            "Sleep log",
            if (d.sleepAvg != null) "Average " + String.format(java.util.Locale.US, "%.1f", d.sleepAvg) + " h over the last " + pluralize(d.sleepNights, "logged night") else "A personal sleep diary"
        ) { host.openSection("sleep") }
        col.tapCard("Summary", "Compare two readings and see totals for a period") { host.push(GrowthSummaryScreen(host)) }

        col.heading("Learn about growth", 12)
        for (key in Articles.growthGuide) {
            Articles.get(key)?.let { a -> col.tapCard(a.title, a.summary) { host.push(ArticleScreen(host, a)) } }
        }
        col.noteCard(Articles.DISCLAIMER + " If you have concerns about your growth or puberty, talk to a doctor.")
    }

    // ---------------------------------------------------------------- height card
    private fun addReading(d: GrowthData) {
        if (!Center.moduleOn("height", false)) Center.setModule("height", true)
        HeightEditor.open(host, null, d.heights) { host.refresh() }
    }

    private fun heightCard(col: LinearLayout, d: GrowthData, inch: Boolean) {
        if (!Center.moduleOn("height", false)) {
            col.card {
                cardTitle("Optional height tracker")
                para("Record your height now and then and see it on a chart. It only shows what you measured. Nothing is added until you add it.", dim = false, sp = 14f)
                para("Height is mostly set by genes, and growth plates close in the late teens. No stretch, exercise or supplement makes adults taller, and nothing here claims otherwise.", sp = 13f)
                actionButton("Turn on height tracker") { Center.setModule("height", true); host.refresh() }
            }
            return
        }
        col.card {
            cardTitle("Height")
            addView(host.chips(listOf("cm" to "Centimetres", "ftin" to "Feet and inches"), Center.heightUnit) { Center.heightUnit = it; async.redraw() }, lp(MATCH, WRAP))
            gap(10)
            if (d.heights.isEmpty()) {
                para("No measurements yet. Add your first reading to see it here. The chart appears after you have recorded some, and a trend needs three or more taken weeks apart.", dim = false, sp = 14f)
                actionButton("Add my first measurement") { addReading(d) }
            } else {
                val latest = d.heights.last()
                val prev = d.heights.getOrNull(d.heights.size - 2)
                addView(UiKit.text(host, HeightUnits.format(latest.value, inch), 30f, Palette.text, true), lp(MATCH, WRAP))
                para("Latest reading, " + Days.pretty(latest.day) + (if (latest.note.isNotEmpty()) " · " + latest.note else ""), sp = 13f, bottom = 6)
                if (prev != null) {
                    val diff = latest.value - prev.value
                    para("Previous: " + HeightUnits.format(prev.value, inch) + ", " + Days.pretty(prev.day) + "  ·  difference " + GrowthMath.signed(diff, inch), dim = false, sp = 13.5f, bottom = 4)
                    para(GrowthMath.diffNote(diff), sp = 12.5f, bottom = 8)
                }
                para(pluralize(d.heights.size, "reading") + " recorded in total.", sp = 12.5f, bottom = 8)
                actionButton("Add a measurement") { addReading(d) }
                actionButton("All readings: edit or delete", false) { host.push(HeightHistoryScreen(host)) }
            }
        }
    }

    // ---------------------------------------------------------------- chart card
    private fun chartCard(col: LinearLayout, d: GrowthData, inch: Boolean) {
        val all = d.heights
        val pts = GrowthMath.inRange(all.map { GrowthMath.Point(it.id, it.day, it.value) }, range, Days.today())
        col.card {
            cardTitle("Height over time (" + (if (inch) "inches" else "centimetres") + ")")
            addView(host.chips(GrowthMath.ranges, range) { range = it; async.redraw() }, lp(MATCH, WRAP))
            gap(8)
            if (pts.isEmpty()) {
                para("No readings in this range. Choose a longer range.", sp = 13f, bottom = 0)
                return@card
            }
            val chart = GrowthChart(host)
            val idx = pts.indexOfFirst { it.id == selId }.let { if (it < 0) pts.lastIndex else it }
            chart.set(pts.map { GrowthChart.Pt(it.day, if (inch) it.cm / 2.54 else it.cm) }, if (inch) " in" else " cm", idx)
            addView(chart, lp(MATCH, WRAP))
            val sel = host.vbox()
            addView(sel, lp(MATCH, WRAP))

            fun showSel(i: Int) {
                sel.removeAllViews()
                val p = pts[i]
                selId = p.id
                val note = all.firstOrNull { it.id == p.id }?.note ?: ""
                sel.para("Selected: " + HeightUnits.format(p.cm, inch) + "  ·  " + Days.pretty(p.day) + " " + p.day.take(4) + (if (note.isNotEmpty()) "  ·  " + note else ""), dim = false, sp = 14f, bottom = 6)
                val row = host.hbox()
                val earlier = UiKit.button(host, "‹ Earlier", false, repeatable = true) { chart.select(chart.selected - 1) }
                val edit = UiKit.button(host, "Edit", false) {
                    all.firstOrNull { it.id == p.id }?.let { m -> HeightEditor.open(host, m, all) { host.refresh() } }
                }
                val later = UiKit.button(host, "Later ›", false, repeatable = true) { chart.select(chart.selected + 1) }
                earlier.contentDescription = "Previous, earlier reading"
                later.contentDescription = "Next, later reading"
                earlier.alpha = if (i == 0) 0.4f else 1f
                later.alpha = if (i == pts.lastIndex) 0.4f else 1f
                row.addView(earlier, lp(0, WRAP, 1f).apply { rightMargin = host.dp(6) })
                row.addView(edit, lp(0, WRAP, 1f).apply { rightMargin = host.dp(6) })
                row.addView(later, lp(0, WRAP, 1f))
                sel.addView(row, lp(MATCH, WRAP))
            }
            chart.onSelect = { showSel(it) }
            showSel(idx)

            para(GrowthMath.trendNote(pts), sp = 12.5f, bottom = 6)

            actionButton(if (showText) "Hide readings as text" else "Show readings as text", false) { showText = !showText; async.redraw() }
            if (showText) {
                for (p in pts.reversed()) para(fullDate(p.day) + ":  " + HeightUnits.format(p.cm, inch), dim = false, sp = 13.5f, bottom = 2)
            }
            actionButton("Compare two readings", false) { host.push(GrowthSummaryScreen(host)) }
        }
    }
}

// =============================================================================================
/** Every height reading, newest first, with edit, delete and undo. */
class HeightHistoryScreen(host: CenterActivity) : Screen(host) {
    override val title = "Height readings"

    private class Data(val list: List<Measure>) {
        fun sig() = list.joinToString(";") { "${it.id}:${it.day}:${it.value}:${it.note}" }
    }

    private var undo: Measure? = null
    private val async = AsyncBox(host, { Data(MeasureRepo.list("height")) }, { b, d -> draw(b, d) }, { it.sig() })

    override fun build(): View = async.view()

    private fun draw(col: LinearLayout, d: Data) {
        val inch = Center.heightUnit == "ftin"
        undo?.let { m ->
            col.card {
                para("Deleted the reading of " + HeightUnits.format(m.value, inch) + " from " + Days.pretty(m.day) + ".", dim = false, sp = 13.5f, bottom = 6)
                actionButton("Undo") {
                    undo = null
                    Io.write {
                        MeasureRepo.add("height", m.value, m.day, m.note)
                        Io.ui { host.toast("Reading restored"); host.refresh() }
                    }
                }
                para("Undo is available until you leave this screen.", sp = 12f, bottom = 0)
            }
        }
        col.noteCard("Showing in " + (if (inch) "feet and inches" else "centimetres") + ". Change units on the Height and growth screen. Readings are stored in centimetres and converted for display.")
        col.actionButton("Add a measurement") {
            HeightEditor.open(host, null, d.list) { host.refresh() }
        }
        if (d.list.isEmpty()) {
            col.emptyState("No measurements yet", "Add your first reading. Nothing is shown until you record something.")
            return
        }
        val perDay = d.list.groupingBy { it.day }.eachCount()
        col.heading("Readings, newest first", 8)
        for (m in d.list.reversed()) {
            val sub = Days.pretty(m.day) + " " + m.day.take(4) + (if (m.note.isNotEmpty()) " · " + m.note else "") +
                (if ((perDay[m.day] ?: 0) > 1) " · " + perDay[m.day] + " readings this day" else "")
            col.tapCard(HeightUnits.format(m.value, inch), sub) {
                Dialogs.list(host, HeightUnits.format(m.value, inch) + " on " + Days.short(m.day), listOf("Edit", "Delete")) { i ->
                    if (i == 0) HeightEditor.open(host, m, d.list) { host.refresh() }
                    else Dialogs.confirm(host, "Delete this reading?", HeightUnits.format(m.value, inch) + " on " + Days.pretty(m.day) + ". You can undo it from this screen right after.", "Delete") {
                        undo = m
                        Io.write {
                            MeasureRepo.delete(m.id)
                            Io.ui { host.refresh() }
                        }
                    }
                }
            }
        }
        col.gap(6)
        col.actionButton("Delete all height readings", false) {
            Dialogs.confirm(host, "Delete all readings?", "Every height reading will be removed from this phone. This cannot be undone.", "Delete all") {
                undo = null
                Io.write {
                    Center.wipe("measures")
                    Io.ui { host.refresh() }
                }
            }
        }
    }
}

// =============================================================================================
/** Movement the person did and activity they typed in. Always entered by hand. */
class ActivityScreen(host: CenterActivity) : Screen(host) {
    override val title = "Activity log"

    private class Data(val list: List<Activity>, val week: LongArray, val weekDays: List<String>, val total: Int) {
        fun sig() = list.joinToString(";") { "${it.id}:${it.day}:${it.kind}:${it.label}:${it.minutes}:${it.note}" } + "|" + total
    }

    private val async = AsyncBox(host, {
        val today = Days.today()
        val days = Days.range(Days.add(today, -6), today)
        val entries = ActivityRepo.between(days.first(), today)
        val mins = LongArray(days.size)
        for (a in entries) if (a.kind == ActivityRepo.KIND_ACTIVITY) {
            val i = days.indexOf(a.day)
            if (i >= 0) mins[i] += a.minutes.toLong()
        }
        Data(ActivityRepo.recent(80), mins, days, ActivityRepo.count())
    }, { b, d -> draw(b, d) }, { it.sig() })

    override fun build(): View = async.view()

    private fun draw(col: LinearLayout, d: Data) {
        col.noteCard("Everything here was typed in or ticked by you. Elevate does not read steps, sensors or any health data, and needs no account or permission for this.")
        col.actionButton("Log activity") { ActivityEditor.open(host, null) { host.refresh() } }
        col.actionButton("Posture and mobility library", false) { host.push(MovementLibraryScreen(host)) }

        col.heading("Last 7 days", 10)
        if (d.week.sum() == 0L) {
            col.para("No active minutes logged in the last 7 days.", sp = 13.5f)
        } else {
            col.card {
                val chart = BarChart(host)
                chart.set(d.week.map { it.toFloat() }, d.weekDays.map { Days.dayLetter(it) }, null, " min")
                addView(chart, lp(MATCH, WRAP))
                para("Active minutes per day that you logged, " + d.week.sum() + " in total. Items you only marked as done have no minutes and are not counted here.", sp = 12.5f, bottom = 0)
            }
        }

        col.heading("Entries", 10)
        if (d.list.isEmpty()) {
            col.emptyState("Nothing logged yet", "Log a walk, a game of football, or mark a posture activity as done. A chart appears after your first entry.")
            return
        }
        for (a in d.list) {
            col.tapCard(
                (if (a.kind == ActivityRepo.KIND_EXERCISE) "Did " else "") + a.label,
                Days.pretty(a.day) + (if (a.minutes > 0) " · ${a.minutes} min" else "") + (if (a.note.isNotEmpty()) " · " + a.note else "")
            ) {
                val items = if (a.kind == ActivityRepo.KIND_ACTIVITY) listOf("Edit", "Delete") else listOf("Delete")
                Dialogs.list(host, a.label, items) { i ->
                    if (a.kind == ActivityRepo.KIND_ACTIVITY && i == 0) ActivityEditor.open(host, a) { host.refresh() }
                    else Dialogs.confirm(host, "Delete this entry?", a.label + " on " + Days.pretty(a.day), "Delete") {
                        Io.write { ActivityRepo.delete(a.id); Io.ui { host.refresh() } }
                    }
                }
            }
        }
        if (d.total > d.list.size) col.para("Showing the latest ${d.list.size} of ${d.total} entries.", sp = 12f)
        col.gap(6)
        col.actionButton("Delete all activity entries", false) {
            Dialogs.confirm(host, "Delete all entries?", "Every activity entry will be removed from this phone. This cannot be undone.", "Delete all") {
                Io.write { Center.wipe("activity"); Io.ui { host.refresh() } }
            }
        }
    }
}

// =============================================================================================
/** The existing exercises, grouped by purpose, with how often each was marked done. */
class MovementLibraryScreen(host: CenterActivity) : Screen(host) {
    override val title = "Posture and mobility"

    private class Data(val counts: Map<String, Int>, val weekDays: Int) {
        fun sig() = counts.entries.sortedBy { it.key }.joinToString(";") { it.key + "=" + it.value } + "|" + weekDays
    }

    private val async = AsyncBox(host, {
        val today = Days.today()
        Data(ActivityRepo.doneCounts(Days.add(today, -29)), ActivityRepo.between(Days.weekStart(today), today).filter { it.kind == ActivityRepo.KIND_EXERCISE }.map { it.day }.toSet().size)
    }, { b, d -> draw(b, d) }, { it.sig() })

    override fun onResumeScreen() { host.refresh() }
    override fun build(): View = async.view()

    private fun draw(col: LinearLayout, d: Data) {
        col.noteCard("Gentle, beginner-friendly movement and awareness. These activities do not lengthen bones or treat spine conditions. Stop if anything hurts, and see a doctor if pain or a visible curve keeps coming back.")
        col.card {
            statRow(listOf(
                (if (d.weekDays == 0) "None" else pluralize(d.weekDays, "day")) to "Marked done this week",
                d.counts.values.sum().toString() to "Done in the last 30 days"
            ))
            para("Counts come from the \"Mark as done\" button on each activity. Nothing is measured automatically.", sp = 12f, bottom = 0)
        }
        for (g in Movement.groups) {
            col.heading(g.title, 12)
            col.para(g.blurb, sp = 13f, bottom = 6)
            for (key in g.keys) {
                val ex = Exercises.get(key) ?: continue
                val n = d.counts[key] ?: 0
                col.tapCard(ex.name, Movement.suggestion(ex) + (if (n > 0) "  ·  done on ${pluralize(n, "day")} (30 days)" else "")) {
                    host.push(ExerciseDetailScreen(host, ex))
                }
            }
        }
        col.heading("Plans", 12)
        col.tapCard("Quick plans and mobility routines", "Short guided routines") { host.openSection("posture") }
        col.noteCard(Articles.DISCLAIMER)
    }
}

/** On an exercise page: "Mark as done today", with a repeated tap that cannot add it twice. */
fun LinearLayout.movementLogCard(host: CenterActivity, key: String, name: String, suggestion: String) {
    val box = host.vbox()
    lateinit var async: AsyncBox<Pair<Boolean, Int>>
    async = AsyncBox(host, {
        val today = Days.today()
        Pair(ActivityRepo.doneOn(key, today), ActivityRepo.doneCounts(Days.add(today, -29))[key] ?: 0)
    }, { b, r ->
        b.card {
            cardTitle("Your log")
            para("Suggested: $suggestion This is a common starting point, not a prescription.", sp = 12.5f)
            if (r.first) {
                para("Marked as done today.", dim = false, sp = 14f, bottom = 6)
            } else {
                actionButton("Mark as done today") {
                    Io.write {
                        if (!ActivityRepo.doneOn(key, Days.today())) {
                            ActivityRepo.add(Activity(0, Days.today(), ActivityRepo.KIND_EXERCISE, key, name, 0, ""))
                        }
                        Io.ui { host.toast("Marked as done"); async.reload() }
                    }
                }
            }
            para("Done on " + pluralize(r.second, "day") + " in the last 30.", sp = 12.5f, bottom = 0)
        }
    }, { "${it.first}:${it.second}" })
    box.addView(async.view())
    addView(box, lp(MATCH, WRAP))
}

// =============================================================================================
/** Numbers computed from the records for a chosen period, and the difference between two chosen readings. */
class GrowthSummaryScreen(host: CenterActivity) : Screen(host) {
    override val title = "Growth summary"

    private var period = "30"
    private var fromId = -1L
    private var toId = -1L

    private class Data(
        val heights: List<Measure>, val inPeriod: Int, val ticks: Int, val moveDays: Int, val moveItems: Int,
        val activeMin: Int, val sleepAvg: Double?, val sleepNights: Int
    ) {
        fun sig() = heights.joinToString(";") { "${it.id}:${it.day}:${it.value}" } + "|$inPeriod,$ticks,$moveDays,$moveItems,$activeMin,$sleepAvg,$sleepNights"
    }

    private val async = AsyncBox(host, { load() }, { b, d -> draw(b, d) }, { it.sig() })

    private fun fromDay(): String = when (period) {
        "7" -> Days.add(Days.today(), -6)
        "90" -> Days.add(Days.today(), -89)
        "all" -> "0000-01-01"
        else -> Days.add(Days.today(), -29)
    }

    private fun load(): Data {
        val today = Days.today()
        val from = fromDay()
        val heights = MeasureRepo.list("height")
        val acts = ActivityRepo.between(from, today)
        val sleep = SleepRepo.recent(1000).filter { it.day >= from }.mapNotNull { it.hours }
        return Data(
            heights, heights.count { it.day >= from },
            HabitRepo.completedPerDay(from, today).values.sum(),
            acts.map { it.day }.toSet().size,
            acts.count { it.kind == ActivityRepo.KIND_EXERCISE },
            acts.filter { it.kind == ActivityRepo.KIND_ACTIVITY }.sumOf { it.minutes },
            if (sleep.isEmpty()) null else sleep.average(), sleep.size
        )
    }

    override fun build(): View = async.view()

    private fun periodLabel() = when (period) { "7" -> "last 7 days"; "90" -> "last 90 days"; "all" -> "all time"; else -> "last 30 days" }

    private fun draw(col: LinearLayout, d: Data) {
        val inch = Center.heightUnit == "ftin"
        col.noteCard("Summaries are calculated from what you recorded. A difference between two readings is not necessarily biological growth, and nothing here scores or predicts growth.")

        col.heading("Compare two readings", 8)
        if (d.heights.size < 2) {
            col.para("You need at least two height readings to compare. Add them on the Height and growth screen.", sp = 13.5f)
        } else {
            val a = d.heights.firstOrNull { it.id == fromId } ?: d.heights.first()
            val b = d.heights.firstOrNull { it.id == toId } ?: d.heights.last()
            fun label(m: Measure) = fullDate(m.day) + " · " + HeightUnits.format(m.value, inch)
            col.card {
                pickRow("From", label(a)) { chooseReading(d.heights, inch) { fromId = it.id; async.redraw() } }
                pickRow("To", label(b)) { chooseReading(d.heights, inch) { toId = it.id; async.redraw() } }
                val pa = GrowthMath.Point(a.id, a.day, a.value)
                val pb = GrowthMath.Point(b.id, b.day, b.value)
                if (a.id == b.id) {
                    para("Choose two different readings.", sp = 13.5f, bottom = 0)
                } else {
                    val diff = GrowthMath.diffCm(pa, pb)
                    val days = GrowthMath.daysBetween(pa, pb)
                    addView(UiKit.text(host, GrowthMath.signed(diff, inch), 26f, Palette.text, true), lp(MATCH, WRAP))
                    para("over " + pluralize(days, "day") + " (later reading minus earlier reading)", sp = 13f, bottom = 6)
                    para(GrowthMath.diffNote(diff), sp = 12.5f, bottom = 0)
                }
            }
        }

        col.heading("Totals", 12)
        col.addView(host.chips(listOf("7" to "7 days", "30" to "30 days", "90" to "90 days", "all" to "All"), period) { period = it; async.reload() }, lp(MATCH, WRAP))
        col.gap(8)
        col.card {
            cardTitle("For the " + periodLabel())
            val lines = ArrayList<String>()
            lines.add(pluralize(d.inPeriod, "height reading") + " recorded (" + d.heights.size + " in total)" + (if (d.heights.isNotEmpty()) ", latest on " + fullDate(d.heights.last().day) else ""))
            lines.add(pluralize(d.ticks, "habit check-in") + " ticked")
            lines.add("Movement logged on " + pluralize(d.moveDays, "day") + (if (d.moveItems > 0) ", " + pluralize(d.moveItems, "library activity", "library activities") + " marked done" else ""))
            lines.add(d.activeMin.toString() + " active minutes logged")
            lines.add(if (d.sleepAvg != null) "Sleep: average " + String.format(java.util.Locale.US, "%.1f", d.sleepAvg) + " h over " + pluralize(d.sleepNights, "logged night") else "Sleep: no nights logged")
            for (l in lines) para("• $l", dim = false, sp = 13.5f, bottom = 4)
            para("Sleep and activity are only what you entered. Habit check-ins count ticks, not planned habits, and do not include workouts that tick a habit automatically.", sp = 12f, bottom = 0)
        }
        col.noteCard(Articles.DISCLAIMER)
    }

    private fun chooseReading(list: List<Measure>, inch: Boolean, onPick: (Measure) -> Unit) {
        val shown = list.reversed()
        Dialogs.list(host, "Choose a reading", shown.map { fullDate(it.day) + " · " + HeightUnits.format(it.value, inch) }) { i -> onPick(shown[i]) }
    }
}
