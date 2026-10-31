package com.elevate.launcher.center.ui

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.elevate.launcher.center.content.Plans
import com.elevate.launcher.center.data.Center
import com.elevate.launcher.center.data.DashLayout
import com.elevate.launcher.center.data.Days
import com.elevate.launcher.center.data.GrowthMath
import com.elevate.launcher.center.data.HState
import com.elevate.launcher.center.data.HabitRepo
import com.elevate.launcher.center.data.XpMath
import com.elevate.launcher.core.ColorMath
import com.elevate.launcher.core.CountText
import com.elevate.launcher.core.Ds
import com.elevate.launcher.core.HeightUnits
import com.elevate.launcher.core.Ico
import com.elevate.launcher.core.MATCH
import com.elevate.launcher.core.Motion
import com.elevate.launcher.core.Palette
import com.elevate.launcher.core.Prefs
import com.elevate.launcher.core.RingsView
import com.elevate.launcher.core.Sparkline
import com.elevate.launcher.core.Tap
import com.elevate.launcher.core.UiKit
import com.elevate.launcher.core.WRAP
import com.elevate.launcher.core.XpBar
import com.elevate.launcher.core.dp
import com.elevate.launcher.core.dpf
import com.elevate.launcher.core.lp
import com.elevate.launcher.core.toast
import com.elevate.launcher.data.Io
import com.elevate.launcher.gaming.GamingActivity
import java.util.Calendar

/** What the last drawing showed, so a refresh animates from there instead of from zero. */
private class Shown(val rings: FloatArray, val xp: Int, val level: Int, val fraction: Float)

/**
 * The Center's home. Everything on it is read from the person's own logs in one background load; the cards are built from
 * that result and can be turned off or reordered in the customization centre.
 */
class DashboardScreen(host: CenterActivity) : Screen(host) {
    override val title = "Center"
    override val actionLabel: String? = "Settings"
    override fun onAction() { host.openSection("settings") }
    override fun onResumeScreen() { host.refresh() }

    private val async = AsyncBox(host, { DashLoad.load() }, { b, d -> draw(b, d) }, { it.sig() })
    private var shown: Shown? = null
    private var firstDraw = true
    private var popHabit = -1L
    private var lastData: DashData? = null

    /** The same data drawn again, for example after a unit change. */
    fun redraw() = async.redraw()

    override fun build(): View = async.view()

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val base = when {
            h < 5 -> "Hello"
            h < 12 -> "Good morning"
            h < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val name = Prefs.userName
        return if (name.isBlank()) base else "$base, $name"
    }

    // -------------------------------------------------------------------------------------------- drawing
    private fun draw(col: LinearLayout, d: DashData) {
        lastData = d
        val before = shown
        for (id in DashLayout.resolve(Prefs.dashLayout, Prefs.dashOrder, Prefs.dashHidden)) {
            when (id) {
                DashLayout.HERO -> hero(col, d, before)
                DashLayout.QUICK -> quickActions(col, d)
                DashLayout.HABITS -> habits(col, d)
                DashLayout.WORKOUT -> workout(col, d)
                DashLayout.GROWTH -> growth(col, d)
                DashLayout.GAMING -> gaming(col, d)
                DashLayout.ACHIEVEMENTS -> achievements(col, d)
                DashLayout.EXPLORE -> explore(col)
            }
        }
        col.noteCard("General fitness and wellness information. It is not medical advice, and nothing here rates your looks or promises to change your height. XP and achievements come only from what you log. Everything stays on this phone.")
        if (firstDraw) {
            firstDraw = false
            Motion.enter(col)
        }
        checkMilestones(d)
    }

    private fun heroStyle(): String = if (Prefs.cardStyle == "flat" || Prefs.cardStyle == "outline") Prefs.cardStyle else "accent"

    private fun hero(col: LinearLayout, d: DashData, before: Shown?) {
        val s = Palette.live()
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = Ds.cardBg(host, heroStyle(), s, 24f)
            elevation = Ds.cardElevation(host, heroStyle(), s)
            setPadding(host.dp(20), host.dp(20), host.dp(20), host.dp(18))
        }
        val greet = UiKit.display(host, greeting(), 28f, Palette.text).apply { maxLines = 2 }
        card.addView(greet, lp(MATCH, WRAP))
        card.addView(UiKit.text(host, Days.prettyLong(d.today), 13.5f, Palette.dim), lp(MATCH, WRAP).apply { topMargin = host.dp(4); bottomMargin = host.dp(16) })

        val row = host.hbox()
        val rings = RingsView(host)
        val list = listOf(
            RingsView.Ring(d.ringHabits, s.accent, s.accent2),
            RingsView.Ring(d.ringWorkouts, s.accent2, ColorMath.mix(s.accent2, 0xFFFFFFFF.toInt(), 0.3f)),
            RingsView.Ring(d.ringMovement, ColorMath.mix(s.accent, s.text, 0.4f), s.accent)
        )
        rings.colors(Palette.divider, Palette.text, Palette.dim)
        when {
            d.habitsAll == 0 -> rings.center("–", "no habits yet")
            d.due.isEmpty() -> rings.center("–", "none due today")
            else -> rings.center("${d.habitsDone}/${d.due.size}", "habits")
        }
        rings.contentDescription =
            (if (d.due.isEmpty()) "No habits due today. " else "Habits: ${d.habitsDone} of ${d.due.size} done today. ") +
                (if (d.plannedThisWeek > 0) "Workouts: ${d.weekDoneDays.size} of ${d.plannedThisWeek} planned this week. " else "Workouts this week: ${d.weekDoneDays.size}. ") +
                "Movement logged on ${d.moveDays} of the ${d.daysSoFar} days so far this week."
        rings.set(list, true, before?.rings)
        row.addView(rings, lp(host.dp(132), host.dp(132)))

        val right = host.vbox()
        val lv = d.level
        right.addView(
            UiKit.pill(host, "LEVEL ${lv.level} · ${XpMath.title(lv.level).uppercase()}", ColorMath.withAlpha(s.accent, 0.20f), Palette.accentText, 10.5f),
            lp(WRAP, WRAP)
        )
        val xpRow = host.hbox()
        xpRow.gravity = Gravity.BOTTOM
        val xpText = CountText(host).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 34f)
            setTextColor(Palette.text)
            contentDescription = "${d.xp} experience points"
        }
        xpText.setValue(d.xp.toDouble(), 0, "", "", true, before?.xp?.toDouble())
        xpRow.addView(xpText, lp(WRAP, WRAP))
        xpRow.addView(UiKit.text(host, "XP", 13f, Palette.dim, true), lp(WRAP, WRAP).apply { leftMargin = host.dp(6); bottomMargin = host.dp(5) })
        right.addView(xpRow, lp(MATCH, WRAP).apply { topMargin = host.dp(8) })
        val bar = XpBar(host)
        bar.colors(s.accent, s.accent2, Palette.divider)
        bar.contentDescription = "${lv.toNext} experience points to level ${lv.level + 1}"
        bar.set(lv.fraction, true, if (before != null && before.level == lv.level) before.fraction else 0f)
        right.addView(bar, lp(MATCH, WRAP).apply { topMargin = host.dp(8) })
        right.addView(UiKit.text(host, "${lv.toNext} XP to Level ${lv.level + 1}", 12.5f, Palette.dim), lp(MATCH, WRAP).apply { topMargin = host.dp(6) })
        row.addView(right, lp(0, WRAP, 1f).apply { leftMargin = host.dp(16) })
        card.addView(row, lp(MATCH, WRAP))

        // legend
        val legend = host.hbox()
        fun item(color: Int, label: String, value: String) {
            val c = host.vbox()
            val top = host.hbox()
            top.addView(android.view.View(host).apply { background = UiKit.rounded(color, host.dpf(4f)) }, lp(host.dp(8), host.dp(8)))
            top.addView(UiKit.text(host, label, 11.5f, Palette.dim), lp(WRAP, WRAP).apply { leftMargin = host.dp(6) })
            c.addView(top)
            c.addView(UiKit.text(host, value, 14f, Palette.text, true), lp(WRAP, WRAP).apply { topMargin = host.dp(2) })
            legend.addView(c, lp(0, WRAP, 1f))
        }
        item(s.accent, "Habits", if (d.due.isEmpty()) "–" else "${d.habitsDone} of ${d.due.size}")
        item(s.accent2, "Workouts", if (d.plannedThisWeek > 0) "${d.weekDoneDays.size} of ${d.plannedThisWeek} this week" else "${d.weekDoneDays.size} this week")
        item(ColorMath.mix(s.accent, s.text, 0.4f), "Movement", "${d.moveDays} of ${d.daysSoFar} days")
        card.addView(legend, lp(MATCH, WRAP).apply { topMargin = host.dp(16) })

        // how XP works: opens and closes smoothly
        val info = UiKit.text(
            host,
            "XP comes only from what you log: ${XpMath.PER_HABIT_CHECK} for each habit check-in, ${XpMath.PER_WORKOUT} for each finished workout, " +
                "${XpMath.PER_MOVEMENT} for each movement you log and ${XpMath.PER_SLEEP_LOG} for each night of sleep notes. Playing games and " +
                "recording height earn nothing, and nothing is taken away for a missed day. XP is worked out from your records, so deleting one lowers it.",
            12.5f, Palette.dim
        ).apply { setLineSpacing(0f, 1.15f); visibility = View.GONE; setPadding(0, host.dp(10), 0, 0) }
        var open = false
        val toggle = UiKit.text(host, "How XP works", 12.5f, Palette.accentText, true).apply {
            setPadding(0, host.dp(12), 0, host.dp(4))
            isClickable = true
            setOnClickListener {
                open = !open
                text = if (open) "Hide" else "How XP works"
                Motion.expand(info, card.width - card.paddingLeft - card.paddingRight, open)
            }
        }
        card.addView(toggle, lp(WRAP, WRAP))
        card.addView(info, lp(MATCH, WRAP))
        col.addView(card, lp(MATCH, WRAP).apply { bottomMargin = host.dp(12) })

        val gain = if (before != null) d.xp - before.xp else 0
        if (gain in 1..400) {
            val pill = UiKit.pill(host, "+$gain XP", ColorMath.withAlpha(s.accent, 0.24f), Palette.accentText, 12f)
            xpRow.addView(pill, lp(WRAP, WRAP).apply { leftMargin = host.dp(10); bottomMargin = host.dp(6) })
            Motion.flash(pill)
        }
        shown = Shown(floatArrayOf(d.ringHabits, d.ringWorkouts, d.ringMovement), d.xp, lv.level, lv.fraction)
    }

    // -------------------------------------------------------------------------------------------- quick actions
    private fun quickActions(col: LinearLayout, d: DashData) {
        val hs = hScroll()
        val row = host.hbox()
        fun chip(icon: String, label: String, action: () -> Unit) {
            val c = host.hbox()
            c.background = Ds.secondaryBg(host, Palette.live(), 22f)
            c.setPadding(host.dp(14), host.dp(10), host.dp(16), host.dp(10))
            c.minimumHeight = host.dp(44)
            c.isClickable = true
            Motion.press(c)
            c.setOnClickListener { if (Tap.ok()) action() }
            c.addView(Ico.view(host, icon, 18, Palette.accentText))
            c.addView(UiKit.text(host, label, 13.5f, Palette.text, true), lp(WRAP, WRAP).apply { leftMargin = host.dp(8) })
            row.addView(c, lp(WRAP, WRAP).apply { rightMargin = host.dp(8) })
        }
        chip("growth", "Add height") {
            if (!d.heightOn) host.openSection("growth") else HeightEditor.open(host, null, d.heights) { host.refresh() }
        }
        chip("workouts", "Start workout") {
            val plan = d.planKey?.let { Plans.get(it) }
            if (plan != null && !d.workoutDone) startPlan(host, plan) else host.openSection("workouts")
        }
        chip("sleep", "Log sleep") { host.openSection("sleep") }
        chip("posture", "Posture break") { host.openSection("movement") }
        chip("gaming", "Gaming") { host.startActivity(Intent(host, GamingActivity::class.java)) }
        chip("palette", "Customize") { host.openSection("customize") }
        hs.addView(row)
        col.addView(hs, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
    }

    private fun hScroll(): HorizontalScrollView = HorizontalScrollView(host).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isFillViewport = false
    }

    // -------------------------------------------------------------------------------------------- habits
    private fun habits(col: LinearLayout, d: DashData) {
        col.sectionHeader("Today's habits", if (d.due.isEmpty()) "" else "${d.habitsDone} of ${d.due.size} done")
        if (d.habitsAll == 0) {
            col.para("Habits are small actions you tick off. Nothing is added until you tap Add.", sp = 13.5f)
            col.starterList(4) { host.refresh() }
            col.tapCard("More habit options", "Create your own or browse every starter") { host.openSection("habits") }
            return
        }
        if (d.due.isEmpty()) {
            col.para("Nothing is scheduled for today.", sp = 13.5f)
        } else {
            val hs = hScroll()
            val row = host.hbox()
            for (h in d.due.take(12)) row.addView(habitCard(h, d), lp(host.dp(150), WRAP).apply { rightMargin = host.dp(10) })
            hs.addView(row)
            col.addView(hs, lp(MATCH, WRAP).apply { bottomMargin = host.dp(8) })
            if (d.due.size > 12) col.para("+${d.due.size - 12} more in Habits", sp = 12.5f)
        }
        col.tapCard("All habits", "History, streaks and editing") { host.openSection("habits") }
    }

    private fun habitCard(h: DashHabit, d: DashData): View {
        val s = Palette.live()
        val done = h.state == HState.COMPLETED
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = Ds.pressableCard(host, Prefs.cardStyle, s, 18f)
            elevation = Ds.cardElevation(host, Prefs.cardStyle, s)
            setPadding(host.dp(14), host.dp(8), host.dp(14), host.dp(14))
            isClickable = true
            Motion.press(this)
            setOnClickListener { if (Tap.ok()) host.push(HabitDetailScreen(host, h.id)) }
            setOnLongClickListener {
                HabitRepo.get(h.id)?.let { hb -> habitStateDialog(host, hb, d.today) { async.reload() } }
                true
            }
        }
        val top = host.hbox()
        val dot = StateDot(host)
        dot.kind = stateKind(h.state)
        dot.contentDescription = h.name + ": " + stateWords(h.state, d.today) + ". Double tap to " + (if (done) "undo" else "mark done") + "."
        val dotBox = host.hbox()
        dotBox.gravity = Gravity.CENTER
        dotBox.setPadding(host.dp(0), host.dp(4), host.dp(10), host.dp(4))
        dotBox.addView(dot)
        dotBox.setOnClickListener { toggle(h, d) }
        top.addView(dotBox)
        top.addView(
            UiKit.text(host, if (done) "Done" else "To do", 11.5f, if (done) Palette.accentText else Palette.dim, true),
            lp(0, WRAP, 1f).apply { gravity = Gravity.CENTER_VERTICAL }
        )
        card.addView(top, lp(MATCH, WRAP))
        card.addView(UiKit.text(host, h.name, 14.5f, Palette.text, true).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, lp(MATCH, WRAP).apply { topMargin = host.dp(2) })
        card.addView(UiKit.text(host, if (h.sub.isEmpty()) stateWords(h.state, d.today) else h.sub, 12f, Palette.dim).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, lp(MATCH, WRAP).apply { topMargin = host.dp(2) })
        if (popHabit == h.id && done) {
            popHabit = -1L
            dot.post { Motion.pop(dotBox) }
        }
        return card
    }

    private fun toggle(h: DashHabit, d: DashData) {
        val completed = h.state == HState.COMPLETED
        when {
            h.method == 1 && !completed -> host.toast("Counts automatically when you finish a workout. Press and hold to mark it by hand.")
            completed && h.method == 1 && !h.manualDone -> host.toast("Done through a workout. Press and hold to change it.")
            else -> {
                popHabit = if (completed) -1L else h.id
                Io.write {
                    HabitRepo.setState(h.id, d.today, if (completed) 0 else HabitRepo.LOG_DONE)
                    Io.ui { async.reload() }
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------- workout
    private fun workout(col: LinearLayout, d: DashData) {
        col.sectionHeader("Today's workout", "")
        val s = Palette.live()
        val card = UiKit.card(host)
        val head = host.hbox()
        head.addView(Ico.badge(host, "workouts", 38), lp(host.dp(38), host.dp(38)))
        val titles = host.vbox()
        val plan = d.planKey?.let { Plans.get(it) }
        when {
            d.resumeName != null -> {
                titles.addView(UiKit.text(host, "In progress", 12f, Palette.accentText, true))
                titles.addView(UiKit.text(host, d.resumeName, 16f, Palette.text, true))
            }
            d.workoutDone -> {
                titles.addView(UiKit.text(host, "Done today", 12f, Palette.accentText, true))
                titles.addView(UiKit.text(host, "Workout finished", 16f, Palette.text, true))
            }
            plan != null -> {
                titles.addView(UiKit.text(host, "Suggested for today", 12f, Palette.dim, true))
                titles.addView(UiKit.text(host, plan.name, 16f, Palette.text, true))
            }
            else -> {
                titles.addView(UiKit.text(host, "Rest day", 12f, Palette.dim, true))
                titles.addView(UiKit.text(host, "No workout planned", 16f, Palette.text, true))
            }
        }
        head.addView(titles, lp(0, WRAP, 1f).apply { leftMargin = host.dp(12) })
        card.addView(head, lp(MATCH, WRAP))

        if (d.resumeName != null && d.resumeTotal > 0) {
            val bar = XpBar(host)
            bar.colors(s.accent, s.accent2, Palette.divider)
            bar.set(d.resumeDone.toFloat() / d.resumeTotal, true)
            bar.contentDescription = "${d.resumeDone} of ${d.resumeTotal} exercises finished"
            card.addView(bar, lp(MATCH, WRAP).apply { topMargin = host.dp(14) })
            card.addView(UiKit.text(host, "${d.resumeDone} of ${d.resumeTotal} exercises finished · started ${d.resumeStarted}", 12.5f, Palette.dim), lp(MATCH, WRAP).apply { topMargin = host.dp(6) })
        } else if (plan != null && !d.workoutDone) {
            card.addView(UiKit.text(host, "About ${plan.minutes} min · ${plan.levelName} · ${pluralize(plan.steps.size, "exercise")}", 12.5f, Palette.dim), lp(MATCH, WRAP).apply { topMargin = host.dp(10) })
        }

        // the week at a glance: filled = workout finished that day
        val strip = host.hbox()
        val ws = Days.weekStart(d.today)
        for (i in 0..6) {
            val day = Days.add(ws, i)
            val c = host.vbox()
            c.gravity = Gravity.CENTER_HORIZONTAL
            val dot = StateDot(host)
            dot.kind = when {
                day in d.weekDoneDays -> 1
                day == d.today -> 0
                day > d.today && Plans.isTrainingDay(day) -> 0
                else -> 4
            }
            val t = UiKit.text(host, Days.dayLetter(day), 11.5f, if (Plans.isTrainingDay(day)) Palette.text else Palette.dim, Plans.isTrainingDay(day))
            t.gravity = Gravity.CENTER
            c.addView(dot, lp(WRAP, WRAP))
            c.addView(t, lp(WRAP, WRAP))
            strip.addView(c, lp(0, WRAP, 1f))
        }
        card.addView(strip, lp(MATCH, WRAP).apply { topMargin = host.dp(12) })
        card.addView(
            UiKit.text(host, if (d.plannedThisWeek > 0) "${d.weekDoneDays.size} of ${d.plannedThisWeek} planned workouts this week" else "${pluralize(d.weekDoneDays.size, "workout")} this week", 12.5f, Palette.dim),
            lp(MATCH, WRAP).apply { topMargin = host.dp(4) }
        )

        when {
            d.resumeName != null -> card.buttonRow("Resume" to { resumeWorkout(host) }, "All workouts" to { host.openSection("workouts") })
            d.workoutDone -> card.buttonRow("See history" to { host.openSection("history") }, "Choose another" to { host.openSection("workouts") })
            plan != null -> card.buttonRow("Start" to { startPlan(host, plan) }, "Details" to { host.openSection("plan:" + plan.key) })
            else -> {
                val rest = Plans.restDayPlan()
                card.buttonRow("Gentle recovery" to { startPlan(host, rest) }, "Choose a workout" to { host.openSection("workouts") })
            }
        }
        col.addView(card, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
    }

    // -------------------------------------------------------------------------------------------- height and posture
    private fun growth(col: LinearLayout, d: DashData) {
        col.sectionHeader("Height and posture", "")
        val s = Palette.live()
        val card = UiKit.card(host)
        val head = host.hbox()
        head.addView(Ico.badge(host, "growth", 38), lp(host.dp(38), host.dp(38)))
        head.addView(UiKit.text(host, "From your own readings", 13f, Palette.dim), lp(0, WRAP, 1f).apply { leftMargin = host.dp(12) })
        card.addView(head, lp(MATCH, WRAP))
        val inch = Center.heightUnit == "ftin"

        if (!d.heightOn) {
            card.addView(UiKit.text(host, "Height tracking is off. Turn it on if you would like to record your height now and then. It only shows what you measure.", 13.5f, Palette.dim).apply { setLineSpacing(0f, 1.15f) },
                lp(MATCH, WRAP).apply { topMargin = host.dp(12) })
            card.buttonRow("Set up height tracking" to { host.openSection("growth") })
        } else if (d.heights.isEmpty()) {
            card.addView(UiKit.text(host, "No measurements yet. Add your first reading and it will appear here.", 13.5f, Palette.dim).apply { setLineSpacing(0f, 1.15f) },
                lp(MATCH, WRAP).apply { topMargin = host.dp(12) })
            card.buttonRow("Add measurement" to { HeightEditor.open(host, null, d.heights) { host.refresh() } }, "Open" to { host.openSection("growth") })
        } else {
            val latest = d.heights.last()
            val prev = d.heights.getOrNull(d.heights.size - 2)
            val row = host.hbox()
            val value: TextView = if (inch) UiKit.display(host, HeightUnits.format(latest.value, true), 32f, Palette.text)
            else CountText(host).apply {
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 34f)
                setTextColor(Palette.text)
                setValue(latest.value, 1, "", " cm", true, prevHeightShown)
                prevHeightShown = latest.value
            }
            value.contentDescription = "Latest height " + HeightUnits.format(latest.value, inch)
            row.addView(value, lp(0, WRAP, 1f))
            val sp = Sparkline(host)
            sp.set(d.heights.takeLast(8).map { it.value }, s.accent, s.accent2)
            sp.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            row.addView(sp, lp(host.dp(96), host.dp(36)))
            card.addView(row, lp(MATCH, WRAP).apply { topMargin = host.dp(12) })
            card.addView(UiKit.text(host, "Latest reading, " + Days.pretty(latest.day), 12.5f, Palette.dim), lp(MATCH, WRAP).apply { topMargin = host.dp(2) })
            if (prev != null) {
                val diff = latest.value - prev.value
                card.addView(
                    UiKit.text(host, "Previous " + HeightUnits.format(prev.value, inch) + " · difference " + GrowthMath.signed(diff, inch), 13f, Palette.text),
                    lp(MATCH, WRAP).apply { topMargin = host.dp(8) }
                )
                card.addView(UiKit.text(host, GrowthMath.diffNote(diff), 12f, Palette.dim).apply { setLineSpacing(0f, 1.12f) }, lp(MATCH, WRAP).apply { topMargin = host.dp(2) })
            }
            card.buttonRow("Add measurement" to { HeightEditor.open(host, null, d.heights) { host.refresh() } }, "Chart and history" to { host.openSection("growth") })
        }

        // posture and mobility this week
        val posture = host.hbox()
        posture.background = UiKit.rounded(Palette.surface2, host.dpf(Ds.R_SMALL))
        posture.setPadding(host.dp(14), host.dp(12), host.dp(12), host.dp(12))
        posture.isClickable = true
        Motion.press(posture)
        posture.setOnClickListener { if (Tap.ok()) host.openSection("movement") }
        posture.addView(Ico.view(host, "posture", 22, Palette.accentText))
        val pt = host.vbox()
        pt.addView(UiKit.text(host, "Posture and mobility", 14f, Palette.text, true))
        pt.addView(UiKit.text(host, if (d.moveItemsWeek == 0) "Nothing marked done this week yet" else pluralize(d.moveItemsWeek, "activity", "activities") + " marked done this week", 12f, Palette.dim))
        posture.addView(pt, lp(0, WRAP, 1f).apply { leftMargin = host.dp(12) })
        posture.addView(Ico.view(host, "chevron", 18, Palette.dim))
        card.addView(posture, lp(MATCH, WRAP).apply { topMargin = host.dp(14) })
        col.addView(card, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
    }

    private var prevHeightShown: Double? = null

    // -------------------------------------------------------------------------------------------- gaming
    private fun gaming(col: LinearLayout, d: DashData) {
        val s = Palette.live()
        val card = UiKit.card(host)
        card.isClickable = true
        Motion.press(card)
        card.setOnClickListener { if (Tap.ok()) host.startActivity(Intent(host, GamingActivity::class.java)) }
        val head = host.hbox()
        head.addView(Ico.badge(host, "gaming", 38, if (d.gamingOn) s.accent else s.dim), lp(host.dp(38), host.dp(38)))
        val t = host.vbox()
        t.addView(UiKit.text(host, "Gaming mode", 16f, Palette.text, true))
        t.addView(
            UiKit.text(
                host,
                if (d.gameSessionsToday == 0) "No sessions tracked today" else "${d.gameMinToday} min tracked today (about)",
                12.5f, Palette.dim
            )
        )
        head.addView(t, lp(0, WRAP, 1f).apply { leftMargin = host.dp(12) })
        val status = UiKit.pill(
            host, if (d.gamingOn) "ON" else "OFF",
            if (d.gamingOn) ColorMath.withAlpha(s.accent, 0.22f) else Palette.surface2,
            if (d.gamingOn) Palette.accentText else Palette.dim
        )
        if (d.gamingOn) status.background = UiKit.rounded(ColorMath.withAlpha(s.accent, 0.22f), host.dpf(20f), s.accent, host.dp(1))
        head.addView(status, lp(WRAP, WRAP))
        card.addView(head, lp(MATCH, WRAP))
        card.addView(
            UiKit.text(host, "Only games you open from Elevate are tracked, and the end time is approximate.", 12f, Palette.dim).apply { setLineSpacing(0f, 1.12f) },
            lp(MATCH, WRAP).apply { topMargin = host.dp(10) }
        )
        col.addView(card, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
    }

    // -------------------------------------------------------------------------------------------- achievements
    private fun achievements(col: LinearLayout, d: DashData) {
        val got = d.achievements.count { it.unlocked }
        col.sectionHeader("Achievements", "$got of ${d.achievements.size}")
        val s = Palette.live()
        val hs = hScroll()
        val row = host.hbox()
        row.gravity = Gravity.TOP
        for (a in d.achievements.sortedByDescending { it.unlocked }) {
            val card = LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                background = if (a.unlocked) Ds.cardBg(host, "accent", s, 18f) else Ds.cardBg(host, if (Prefs.cardStyle == "outline") "outline" else "flat", s, 18f)
                setPadding(host.dp(14), host.dp(14), host.dp(14), host.dp(14))
                isClickable = true
                Motion.press(this)
                setOnClickListener { if (Tap.ok()) host.toast(a.title + ": " + a.blurb + (if (a.unlocked) " (unlocked)" else " (${a.value} of ${a.goal})")) }
                contentDescription = a.title + ". " + a.blurb + ". " + (if (a.unlocked) "Unlocked." else "${a.value} of ${a.goal}.")
            }
            card.addView(Ico.badge(host, a.icon, 36, if (a.unlocked) s.accent else s.dim), lp(host.dp(36), host.dp(36)))
            card.addView(UiKit.text(host, a.title, 13.5f, if (a.unlocked) Palette.text else Palette.dim, true).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, lp(MATCH, WRAP).apply { topMargin = host.dp(10) })
            if (a.unlocked) {
                card.addView(UiKit.text(host, "Unlocked", 11.5f, Palette.accentText, true), lp(MATCH, WRAP).apply { topMargin = host.dp(4) })
            } else {
                val bar = XpBar(host)
                bar.colors(s.accent, s.accent2, Palette.divider)
                bar.set(a.fraction, false)
                card.addView(bar, lp(MATCH, WRAP).apply { topMargin = host.dp(8) })
                card.addView(UiKit.text(host, "${a.value} of ${a.goal}", 11.5f, Palette.dim), lp(MATCH, WRAP).apply { topMargin = host.dp(4) })
            }
            row.addView(card, lp(host.dp(138), WRAP).apply { rightMargin = host.dp(10) })
        }
        hs.addView(row)
        col.addView(hs, lp(MATCH, WRAP).apply { bottomMargin = host.dp(10) })
    }

    // -------------------------------------------------------------------------------------------- explore
    private fun explore(col: LinearLayout) {
        col.sectionHeader("Explore", "")
        col.tileRow(
            Triple("Habits", "Daily routines", { host.openSection("habits") }),
            Triple("Workouts", "Plans and history", { host.openSection("workouts") })
        )
        col.tileRow(
            Triple("Grooming", "Skincare and hair", { host.openSection("grooming") }),
            Triple("Posture", "Mobility and sleep", { host.openSection("posture") })
        )
        col.tileRow(
            Triple("Goals", "Targets you set", { host.openSection("goals") }),
            Triple("Progress", "Charts from your logs", { host.openSection("progress") })
        )
        col.tileRow(
            Triple("Learn", "Short offline guides", { host.openSection("learn") }),
            Triple("Reminders", "Optional nudges", { host.openSection("reminders") })
        )
        col.tileRow(
            Triple("Photo journal", "Private progress photos", { host.openSection("photos") }),
            Triple("Height and growth", "Tracker, movement, guides", { host.openSection("growth") })
        )
    }

    // -------------------------------------------------------------------------------------------- milestones
    private fun suppressCelebration(): Boolean =
        host.intent.getBooleanExtra("ci_skip_onboarding", false) && !host.intent.getBooleanExtra("ci_celebrate", false)

    private fun checkMilestones(d: DashData) {
        val ids = d.achievements.filter { it.unlocked }.map { it.id }.toSet()
        val sp = Prefs.sp
        val seenLevel = if (sp.contains("seen_level")) Prefs.getInt("seen_level", 1) else null
        val seenIds = sp.getString("seen_ach", null)?.split(',')?.filter { it.isNotEmpty() }?.toSet()
        val m = XpMath.milestones(seenLevel, seenIds, d.level.level, ids)
        val news = m.levelUp != null || m.unlocked.isNotEmpty()
        // Not a good moment (another screen is on top, or an automated test is running): leave the saved state alone so
        // the celebration is shown the next time the dashboard is on top.
        if (news && (suppressCelebration() || !host.isTop(this) || host.isDestroyed)) return
        Prefs.putInt("seen_level", d.level.level)
        Prefs.putString("seen_ach", ids.joinToString(","))
        if (news) celebrate(m, d)
    }

    private fun celebrate(m: XpMath.Milestones, d: DashData) {
        val s = Palette.live()
        val box = host.vbox()
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(host.dp(24), host.dp(8), host.dp(24), host.dp(4))
        val badge = Ico.badge(host, if (m.levelUp != null) "bolt" else "trophy", 72, s.accent)
        box.addView(badge, lp(host.dp(72), host.dp(72)))
        val title = if (m.levelUp != null) "Level ${m.levelUp}: ${XpMath.title(m.levelUp)}" else "Achievement unlocked"
        box.addView(UiKit.display(host, title, 24f, Palette.text).apply { gravity = Gravity.CENTER }, lp(MATCH, WRAP).apply { topMargin = host.dp(16) })
        val lines = ArrayList<String>()
        for (id in m.unlocked) d.achievements.firstOrNull { it.id == id }?.let { lines.add(it.title + ": " + it.blurb) }
        if (m.levelUp != null) lines.add(0, "You have ${d.xp} XP from the check-ins, workouts, movement and sleep notes you logged.")
        box.addView(
            UiKit.text(host, lines.joinToString("\n\n"), 14f, Palette.dim).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.15f) },
            lp(MATCH, WRAP).apply { topMargin = host.dp(10) }
        )
        val dlg = AlertDialog.Builder(host).setView(box).setPositiveButton("Nice", null).create()
        dlg.show()
        Motion.pop(badge)
    }
}

/** A section title with a small count or note on the right. */
fun LinearLayout.sectionHeader(title: String, right: String) {
    val ctx = context
    val row = ctx.hbox()
    row.addView(UiKit.text(ctx, title, 17f, Palette.text, true), lp(0, WRAP, 1f))
    if (right.isNotEmpty()) row.addView(UiKit.text(ctx, right, 12.5f, Palette.dim))
    addView(row, lp(MATCH, WRAP).apply { topMargin = ctx.dp(8); bottomMargin = ctx.dp(8) })
}
